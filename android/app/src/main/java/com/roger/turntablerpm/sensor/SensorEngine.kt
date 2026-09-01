package com.roger.turntablerpm.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.Executors
import com.roger.turntablerpm.core.MeasurementAnalysis
import com.roger.turntablerpm.core.SamplingStats
import com.roger.turntablerpm.core.SpinProjector
import com.roger.turntablerpm.core.SpinSample
import com.roger.turntablerpm.core.SpeedStatistics
import com.roger.turntablerpm.core.TurntableSpeed
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
    /** 牆鐘量到的時長，用來跟感測器時間戳對照。 */
    val wallElapsedSeconds: Double = 0.0,
    /** 感測器時間 ÷ 牆鐘時間。1.000 代表時間戳誠實。 */
    val clockRatio: Double? = null,
    val gyroName: String? = null,
    val gravityName: String? = null,
    val gravityIsFused: Boolean = true,
    val error: String? = null,

    /** 標稱轉速的自動辨識結果，認不出來是 null。 */
    val nominal: TurntableSpeed? = null,
    /** 相對標稱值的偏差 %。 */
    val errorPercent: Double? = null,
    /** 陀螺儀積分的累積圈數。 */
    val revolutions: Int = 0,
    /** 停止之後的離線分析。 */
    val analysis: MeasurementAnalysis? = null,
    /** 分析失敗的原因。「載入中」是最容易變成死路的狀態，每條失敗路徑都要有畫面。 */
    val analysisFailureReason: String? = null,
    val analyzing: Boolean = false,
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

    /**
     * **狀態守衛不能用發布給 UI 的 `running`。** 那個欄位會被感測器執行緒的 publish() 覆寫，
     * 競態一發生，再按一次開始就會重複 registerListener 而不解除 —— 監聽器就漏了。
     * 實測抓到：UI 顯示已停止，dumpsys sensorservice 卻顯示陀螺儀有 2 個連線。
     */
    @Volatile private var active = false

    /**
     * **統計不能算在感測器執行緒上。** `SamplingStats.from()` 內含排序，
     * 每 200 ms 對全部樣本排一次；3 分鐘的量測是兩萬筆，會直接卡住事件處理。
     * 實測：量測畫面的抖動比 0.811%，同一台在純診斷畫面是 0.160%。
     * 感測器回呼只負責丟一個工作進來，重活在這條執行緒上做。
     */
    private val publisher = Executors.newSingleThreadExecutor()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val lock = Any()
    private val samples = ArrayList<SpinSample>()
    private val rawTimestamps = ArrayList<Double>()
    private var latestGravity: Vector3? = null
    private var baseNanos: Long = 0
    private var baseWallNanos: Long = 0
    private var latestWallNanos: Long = 0
    private var totalDegrees: Double = 0.0
    private var lastSampleTime: Double? = null
    private var lastOmega: Double = 0.0
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
        if (active) return
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
            baseWallNanos = 0
            latestWallNanos = 0
            totalDegrees = 0.0
            lastSampleTime = null
            lastOmega = 0.0
            timestampBase = null
        }
        // 防禦性解除：萬一有殘留的註冊（例如上一輪的競態），先清乾淨再註冊。
        manager.unregisterListener(this)
        val t = HandlerThread("sensor").apply { start() }
        thread = t
        handler = Handler(t.looper)
        active = true
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
        if (!active) return
        active = false
        manager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        handler = null
        publish(running = false)
        runAnalysis()
    }

    /**
     * 離線分析。FFT 加加權捲積在兩萬筆樣本上要跑一下，不能擋主執行緒。
     *
     * **取樣率傳實測的有效速率，不要寫死 100。** XZ Premium 原生是 107.92 Hz，
     * 往下重取樣到 100 會讓線性內插對 50 Hz 以上的雜訊產生疊頻；用實測值的話
     * 網格間距與原生一致，內插幾乎是恆等映射。
     */
    private fun runAnalysis() {
        val snapshot = snapshotSamples()
        val rate = _state.value.stats?.effectiveRateHz
        if (snapshot.size <= 64 || rate == null || rate <= 0) {
            _state.value = _state.value.copy(
                analysisFailureReason = "量測時間太短，只錄到 ${snapshot.size} 筆資料。",
            )
            return
        }
        _state.value = _state.value.copy(analyzing = true, analysisFailureReason = null)
        Thread {
            val result = MeasurementAnalysis.analyze(snapshot, sampleRate = rate)
            // 失敗的原因幾乎都是「找不到夠長的穩定區間」，講清楚比只說「失敗」有用。
            // 三種失敗要分開講。**最容易搞混的是第二種**：靜止不動的手機非常穩，
            // 閘門會整段放行，錯誤訊息若說「轉速不穩」使用者會完全找不到方向。
            val reason = if (result == null) {
                val window = com.roger.turntablerpm.core.StabilityGate.find(snapshot)
                when {
                    window == null ->
                        "整段量測都沒有穩定的轉速。轉盤有轉起來嗎？至少要連續穩定 5 秒。"
                    window.medianOmega / 6.0 < MeasurementAnalysis.MINIMUM_RPM ->
                        "量到的轉速只有 %.3f RPM —— 轉盤沒有轉起來。".format(window.medianOmega / 6.0)
                    else ->
                        "資料不足以分析。試著量久一點，90 秒以上比較可靠。"
                }
            } else null
            _state.value = _state.value.copy(
                analysis = result, analysisFailureReason = reason, analyzing = false,
            )
        }.start()
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
                    // 牆鐘與感測器時間戳成對記錄 —— 兩者的比值就是時間戳誠不誠實。
                    val wall = SystemClock.elapsedRealtimeNanos()
                    if (baseNanos == 0L) {
                        baseNanos = event.timestamp
                        baseWallNanos = wall
                        timestampBase = SamplingStats.identifyTimestampBase(
                            event.timestamp,
                            SystemClock.elapsedRealtimeNanos(),
                            System.currentTimeMillis(),
                        ).first
                    }
                    // **一律用感測器自己的時間戳**，不要用 System.nanoTime()。
                    val t = (event.timestamp - baseNanos) / 1e9
                    // 梯形積分累積轉角。用真實時間戳的間隔，不假設等間隔。
                    lastSampleTime?.let { previous ->
                        totalDegrees += (t - previous) * (omega + lastOmega) / 2.0
                    }
                    lastSampleTime = t
                    lastOmega = omega
                    samples += SpinSample(t = t, omega = omega)
                    rawTimestamps += t
                    latestWallNanos = wall
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
        // 只丟工作，不在感測器執行緒上算。
        publisher.execute { publish(running = active) }
    }

    private fun publish(running: Boolean) {
        data class Snap(
            val samples: List<SpinSample>, val times: DoubleArray,
            val base: String?, val wallSpan: Double, val degrees: Double,
        )
        val snap = synchronized(lock) {
            Snap(
                ArrayList(samples), rawTimestamps.toDoubleArray(), timestampBase,
                if (baseWallNanos > 0) (latestWallNanos - baseWallNanos) / 1e9 else 0.0,
                totalDegrees,
            )
        }
        val snapshot = snap.samples
        val times = snap.times
        val base = snap.base
        val mean = SpeedStatistics.meanRPM(snapshot)
        val nominal = mean?.let { SpeedStatistics.classify(it) }
        val previous = _state.value
        _state.value = previous.copy(
            running = running,
            sampleCount = snapshot.size,
            elapsedSeconds = if (snapshot.size >= 2) snapshot.last().t - snapshot.first().t else 0.0,
            instantRPM = (snapshot.lastOrNull()?.omega ?: 0.0) / 6.0,
            meanRPM = mean,
            nominal = nominal,
            errorPercent = if (mean != null && nominal != null) {
                SpeedStatistics.errorPercent(mean, nominal)
            } else null,
            revolutions = (snap.degrees / 360.0).toInt(),
            stats = SamplingStats.from(times),
            timestampBase = base,
            wallElapsedSeconds = snap.wallSpan,
            clockRatio = if (snapshot.size >= 2) {
                SamplingStats.clockRatio(snapshot.last().t - snapshot.first().t, snap.wallSpan)
            } else null,
        )
    }
}
