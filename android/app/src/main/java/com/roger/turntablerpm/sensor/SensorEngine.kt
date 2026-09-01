package com.roger.turntablerpm.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.roger.turntablerpm.core.SamplingStats
import com.roger.turntablerpm.core.SpinProjector
import com.roger.turntablerpm.core.SpinSample
import com.roger.turntablerpm.core.SpeedStatistics
import com.roger.turntablerpm.core.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次量測的即時狀態。 */
data class EngineState(
    val running: Boolean = false,
    val sampleCount: Int = 0,
    val elapsedSeconds: Double = 0.0,
    val instantRPM: Double = 0.0,
    val meanRPM: Double? = null,
    val stats: SamplingStats? = null,
    val timestampBase: String? = null,
    val gyroName: String? = null,
    val gravityName: String? = null,
    val gravityIsFused: Boolean = true,
    val error: String? = null,
)

/**
 * 感測器接線。**這一層是 app 模組唯一碰 Android framework 的地方**，
 * 演算法全部在 `:core`（純 Kotlin、JVM 可測）。
 *
 * 三個 Android 特有的問題（交接文件 §5）：
 *
 * 1. **取樣率只是建議值。** `registerListener` 的 samplingPeriodUs 是給 HAL 的提示，
 *    實際頻率由廠商決定。所以每一次量測都同時統計實際間隔（`SamplingStats`）。
 * 2. **時間戳一律用 `SensorEvent.timestamp`**，不用 `System.nanoTime()`。
 *    前者是感測器自己的時鐘，後者會把派送延遲算進去。
 * 3. **陀螺儀與重力是兩個獨立的事件流**，速率未必相同。這裡以陀螺儀事件為準，
 *    配上「當下最新的一筆重力」——重力的變化遠慢於角速度，這個近似是安全的。
 */
class SensorEngine(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravityIsFused = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val lock = Any()
    private val samples = ArrayList<SpinSample>()
    private val rawTimestamps = ArrayList<Double>()
    private var latestGravity: Vector3? = null
    private var baseNanos: Long = 0
    private var timestampBase: String? = null

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    val isAvailable: Boolean get() = gyroscope != null && gravity != null

    /**
     * @param samplingPeriodUs 給 HAL 的**建議**取樣週期。10000 = 100 Hz，與 iOS 對齊。
     *   注意 Android 12 起超過 200 Hz 需要 HIGH_SAMPLING_RATE_SENSORS 權限 ——
     *   這個 app 不需要那麼快（50 Hz 以上只佔加權能量 0.72%），刻意不宣告。
     */
    fun start(samplingPeriodUs: Int = 10_000) {
        if (_state.value.running) return
        val gyro = gyroscope
        val grav = gravity
        if (gyro == null || grav == null) {
            _state.value = _state.value.copy(
                error = "這台裝置缺少陀螺儀或重力感測器，無法量測。",
            )
            return
        }
        synchronized(lock) {
            samples.clear()
            rawTimestamps.clear()
            latestGravity = null
            baseNanos = 0
            timestampBase = null
        }
        val t = HandlerThread("sensor").apply { start() }
        thread = t
        handler = Handler(t.looper)
        manager.registerListener(this, grav, samplingPeriodUs, handler)
        manager.registerListener(this, gyro, samplingPeriodUs, handler)
        _state.value = EngineState(
            running = true,
            gyroName = "${gyro.name} / ${gyro.vendor}",
            gravityName = "${grav.name} / ${grav.vendor}",
            gravityIsFused = gravityIsFused,
        )
    }

    fun stop() {
        if (!_state.value.running) return
        manager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        handler = null
        publish(running = false)
    }

    /** 交出目前累積的樣本，供離線分析。 */
    fun snapshotSamples(): List<SpinSample> = synchronized(lock) { ArrayList(samples) }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                synchronized(lock) {
                    latestGravity = Vector3(
                        event.values[0].toDouble(),
                        event.values[1].toDouble(),
                        event.values[2].toDouble(),
                    )
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val g = synchronized(lock) { latestGravity } ?: return
                val rate = Vector3(
                    event.values[0].toDouble(),
                    event.values[1].toDouble(),
                    event.values[2].toDouble(),
                )
                val omega = SpinProjector.project(rate, g)
                synchronized(lock) {
                    if (baseNanos == 0L) {
                        baseNanos = event.timestamp
                        timestampBase = SamplingStats.identifyTimestampBase(
                            event.timestamp,
                            SystemClock.elapsedRealtimeNanos(),
                            System.currentTimeMillis(),
                        ).first
                    }
                    // **一律用感測器自己的時間戳**，不要用 System.nanoTime()。
                    val t = (event.timestamp - baseNanos) / 1e9
                    samples += SpinSample(t = t, omega = omega)
                    rawTimestamps += t
                }
                maybePublish()
            }
        }
    }

    // 感測器以 100 Hz 進來，UI 不需要那麼快 —— 每 200 ms 才推一次。
    private var lastPublishMs = 0L

    private fun maybePublish() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPublishMs < 200) return
        lastPublishMs = now
        publish(running = true)
    }

    private fun publish(running: Boolean) {
        val (snapshot, times, base) = synchronized(lock) {
            Triple(ArrayList(samples), rawTimestamps.toDoubleArray(), timestampBase)
        }
        val previous = _state.value
        _state.value = previous.copy(
            running = running,
            sampleCount = snapshot.size,
            elapsedSeconds = if (snapshot.size >= 2) snapshot.last().t - snapshot.first().t else 0.0,
            instantRPM = (snapshot.lastOrNull()?.omega ?: 0.0) / 6.0,
            meanRPM = SpeedStatistics.meanRPM(snapshot),
            stats = SamplingStats.from(times),
            timestampBase = base,
        )
    }
}
