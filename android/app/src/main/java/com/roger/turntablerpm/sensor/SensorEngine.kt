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
import com.roger.turntablerpm.R
import com.roger.turntablerpm.core.MagneticRevolutionCounter
import com.roger.turntablerpm.core.PhaseIntegrator
import com.roger.turntablerpm.core.MeasurementAnalysis
import com.roger.turntablerpm.core.SamplingStats
import com.roger.turntablerpm.core.SpinProjector
import com.roger.turntablerpm.core.SpinSample
import com.roger.turntablerpm.export.MeasurementExport
import com.roger.turntablerpm.export.RawFrame
import com.roger.turntablerpm.core.SpeedStatistics
import com.roger.turntablerpm.core.TurntableSpeed
import com.roger.turntablerpm.core.Vector3
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 量測模式。
 *
 * **自動是預設。** 手動模式要在盤面轉動時去點畫面上的按鈕，那很難按；
 * 自動模式等轉速穩定才真正開始記錄、盤面停下時自己結束，使用者只要放好手機。
 */
enum class Mode { MANUAL, AUTOMATIC }

/** 量測的階段。自動模式會多一個「等待轉速穩定」。 */
enum class Phase { IDLE, WAITING_FOR_STABILITY, MEASURING, STOPPED }

/**
 * 「這是一次量測的結果」那一組欄位，原封不動地留住。
 *
 * 主畫面的「這次量測」卡、碼錶校準讀的 `meanRPM`、匯出按鈕、以及自動存進歷史，
 * 全部吃這一組。取樣診斷跟量測共用同一個引擎，但它不是量測 ——
 * 這個函式是「哪些欄位屬於量測」的**單一定義**，`start()` 與 `publish()` 都用它，
 * 以後新增欄位時只有一個地方要改。
 *
 * `sampleCount` / `elapsedSeconds` / `stats` 這三個**兩邊都要用** —— 主畫面的
 * 「這次量測」要顯示它們，診斷頁也要。所以診斷跑的取樣統計改走 `samplingStats`，
 * 這三個一律留給量測。第一版漏掉這點，實機上平均轉速保住了、樣本數卻被蓋掉。
 */
internal fun EngineState.keepingMeasurementOf(previous: EngineState) = copy(
    sampleCount = previous.sampleCount,
    elapsedSeconds = previous.elapsedSeconds,
    stats = previous.stats,
    instantRPM = previous.instantRPM,
    meanRPM = previous.meanRPM,
    rawMeanRPM = previous.rawMeanRPM,
    appliedFactor = previous.appliedFactor,
    nominal = previous.nominal,
    errorPercent = previous.errorPercent,
    revolutions = previous.revolutions,
    analysis = previous.analysis,
    analysisFailureReason = previous.analysisFailureReason,
    analyzing = previous.analyzing,
    exportPath = previous.exportPath,
)

/** 一次量測的即時狀態。 */
data class EngineState(
    val running: Boolean = false,
    val mode: Mode = Mode.AUTOMATIC,
    val phase: Phase = Phase.IDLE,
    val sampleCount: Int = 0,
    val elapsedSeconds: Double = 0.0,
    val instantRPM: Double = 0.0,
    val meanRPM: Double? = null,
    val stats: SamplingStats? = null,
    /**
     * 取樣特性診斷頁專用的統計。**只有 samplingOnly 的那一輪會寫這裡。**
     * 診斷跑不是量測，不能去動 `stats`（主畫面的「這次量測」在用）。
     */
    val samplingStats: SamplingStats? = null,
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
    /** 未修正的平均轉速。校準倍率只影響對外讀數，診斷要看原始值。 */
    val rawMeanRPM: Double? = null,
    /** 目前生效的校準倍率。null 代表未校準 —— 那時偏差 % 不能拿來調唱盤。 */
    val appliedFactor: Double? = null,
    /** 停止之後的離線分析。 */
    val analysis: MeasurementAnalysis? = null,
    /** 分析失敗的原因。「載入中」是最容易變成死路的狀態，每條失敗路徑都要有畫面。 */
    val analysisFailureReason: String? = null,
    val analyzing: Boolean = false,
    /** 這次量測的原始資料 JSON 檔路徑。分析失敗時也會有 —— 那時最需要它。 */
    val exportPath: String? = null,

    // ── 進階診斷 ──────────────────────────────────────────────
    //
    // 下面這些是**兩條已經證實失敗的自動校準路徑**留下來的診斷資料。
    // 它們不參與任何對外讀數，唯一可信的校準是碼錶（見 CLAUDE.md 坑 11、15）。

    /** 瞬時角速度，°/s。 */
    val latestOmega: Double = 0.0,
    /** 圈內相位，0–360°。 */
    val phaseDegrees: Double = 0.0,
    /** 陀螺儀積分的總轉角。 */
    val gyroTotalDegrees: Double = 0.0,
    /** 融合方位角（TYPE_ROTATION_VECTOR）解捲出來的總轉角。 */
    val magneticTotalDegrees: Double = 0.0,
    /** 最新一筆的融合方位角，度。 */
    val magneticYawDegrees: Double? = null,
    /** 融合路徑估出來的 k。**幾乎一定是 1.0，那正是它不可信的證據。** */
    val fusedCalibration: Double? = null,
    val gravityVector: Vector3? = null,
    val rotationRate: Vector3? = null,
    /** 已校準磁場（TYPE_MAGNETIC_FIELD），µT。 */
    val calibratedField: Vector3? = null,
    /** 未校準磁力計（TYPE_MAGNETIC_FIELD_UNCALIBRATED），µT。 */
    val rawField: Vector3? = null,
    val fieldAccuracy: String = "—",
    /** 已校準磁場路徑：直接解捲。 */
    val magneticTotal: Double = 0.0,
    val magneticRevolutions: Int = 0,
    val magneticSampleCount: Int = 0,
    val magneticHorizontal: Double = 0.0,
    /** (較大, 較小)。會繞圈時大的是半徑，繞不起來時大的是圓心偏移。 */
    val magneticHorizontalRange: Pair<Double, Double>? = null,
    /** 扣掉擬合圓心之後的結果。 */
    val refined: MagneticRevolutionCounter.Refined? = null,
    /** 未校準磁力計路徑：完全獨立於任何融合器。 */
    val rawMagneticTotal: Double = 0.0,
    val rawMagneticRevolutions: Int = 0,
    val rawMagneticSampleCount: Int = 0,
    val rawRefined: MagneticRevolutionCounter.Refined? = null,
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

    /**
     * 錯誤訊息要在地化，而感測器層看不到 Compose 的 `stringResource`。
     * 拿 applicationContext 而不是傳進來的那個 —— 引擎的生命週期比 Activity 長。
     */
    private val app: Context = context.applicationContext
    private val gyroscope: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravity: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravityIsFused = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null

    /**
     * 診斷用的三顆感測器。**全部是選配** —— 少了任何一顆只是那一區沒有數字，
     * 量測本身完全不受影響。
     *
     * `TYPE_ROTATION_VECTOR` 是 Android 的融合姿態，對應 iOS 的 `attitude.yaw`；
     * 兩顆磁力計對應 iOS 的 `CMDeviceMotion.magneticField` 與 `CMMagnetometerData`。
     * 並排記錄兩種來源才能判斷偏置估計器有沒有在量測過程中改動讀數（坑 12）。
     */
    private val rotationVector: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rawMagnetometer: Sensor? =
        manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)

    /**
     * 匯出目錄。用 app 專屬的外部空間而不是 filesDir：不需要任何權限，
     * 而且接上電腦 `adb pull` 就拿得到 —— 診斷時常常要整包搬到 Mac 上跑分析腳本。
     */
    private val exportDir: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")

    /**
     * **狀態守衛不能用發布給 UI 的 `running`。** 那個欄位會被感測器執行緒的 publish() 覆寫，
     * 競態一發生，再按一次開始就會重複 registerListener 而不解除 —— 監聽器就漏了。
     * 實測抓到：UI 顯示已停止，dumpsys sensorservice 卻顯示陀螺儀有 2 個連線。
     */
    @Volatile private var active = false

    /**
     * 這一輪是不是「只量取樣特性」。
     *
     * 取樣診斷頁跟量測畫面共用同一個引擎（陀螺儀的註冊與時間戳邏輯只該有一份），
     * 但**跑診斷不是一次量測**：它不該蓋掉主畫面的「這次量測」、不該餵給碼錶校準、
     * 更不該觸發 `onAnalysisComplete` 把一筆假記錄自動存進歷史。
     *
     * 跟 `active` 一樣是獨立的 `@Volatile` 旗標，不用發布給 UI 的欄位當守衛 ——
     * 那個會被感測器執行緒覆寫（坑 35）。
     */
    @Volatile private var samplingOnly = false

    /**
     * **統計不能算在感測器執行緒上。** `SamplingStats.from()` 內含排序，
     * 每 200 ms 對全部樣本排一次；3 分鐘的量測是兩萬筆，會直接卡住事件處理。
     * 實測：量測畫面的抖動比 0.811%，同一台在純診斷畫面是 0.160%。
     * 感測器回呼只負責丟一個工作進來，重活在這條執行緒上做。
     */
    private val publisher = Executors.newSingleThreadExecutor()

    /**
     * 碼錶校準倍率。設進來之後所有對外讀數都會套用，**包含標稱辨識**：
     * 未修正的讀數可能落在辨識窗外，套用之後才認得出來。
     */
    @Volatile var calibrationFactor: Double? = null

    /**
     * 分析成功時自動存進歷史。
     *
     * **不做手動的「儲存」按鈕** —— 使用者不會記得按，而歷史的價值就在
     * 「調整前後能比較」，漏存一次就斷了（iOS 端的結論，見 CLAUDE.md 坑 40）。
     */
    var onAnalysisComplete: ((MeasurementAnalysis, rawMeanRPM: Double, revolutions: Int) -> Unit)? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val lock = Any()
    private val samples = ArrayList<SpinSample>()
    // 匯出要逐筆的重力向量：離線工具靠它把感測器座標系的東西投影到水平面。
    // 只留一個 latestGravity 不夠 —— 盤面章動時每一筆的重力方向都不一樣（坑 14）。
    private val gravities = ArrayList<Vector3>()
    // 磁場逐筆存下來，讓匯出的檔案跟 iOS 一樣可以拿去分段圓擬合。
    private val fields = ArrayList<Vector3?>()
    private val rawFields = ArrayList<Vector3?>()
    private val rawTimestamps = ArrayList<Double>()
    private var latestGravity: Vector3? = null
    private var latestRotationRate: Vector3? = null
    private var latestYawRadians: Double? = null
    private var latestField: Vector3? = null
    private var latestRawField: Vector3? = null
    private var fieldAccuracy: String = "—"
    // 相位與兩條校準路徑的累積。核心的 PhaseIntegrator 已經處理好「錨點要推進
    // 剛好一整圈」與「yaw 可能遞減」這兩個坑（CLAUDE.md 坑 3、4）。
    private var phase = PhaseIntegrator()
    private var magneticCounter = MagneticRevolutionCounter()
    private var rawMagneticCounter = MagneticRevolutionCounter()
    private var baseNanos: Long = 0
    private var baseWallNanos: Long = 0
    private var latestWallNanos: Long = 0
    private var totalDegrees: Double = 0.0

    /**
     * 反旋轉盤面用的顯示角度。
     *
     * **它的零點必須是「使用者按下開始的那一刻」**，而不是自動模式決定開始記錄的
     * 那一刻 —— 後者是程式挑的隨機時刻。零點決定畫面上的文字朝哪個方向，
     * 只有在使用者還照著指示擺手機的時候歸零，文字才會正對著他（iOS 坑 21）。
     * 所以它跟 totalDegrees 分開，不隨自動模式的 reset 歸零。
     */
    @Volatile private var displayDegrees: Double = 0.0
    @Volatile private var lastDisplayTime: Double = 0.0
    @Volatile private var lastDisplayOmega: Double = 0.0
    private var stableSinceMs: Long = 0
    private var lastSampleTime: Double? = null
    private var lastOmega: Double = 0.0
    private var timestampBase: String? = null

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    val isAvailable: Boolean get() = gyroscope != null && gravity != null

    /**
     * 為什麼不能量。**要指名是哪一個感測器缺席。**
     *
     * 不是每一支 Android 手機都有陀螺儀 —— 中階機常常省掉，這是 iOS 端從來不必
     * 交代的限制（每一支 iPhone 都有）。實測 Sony Xperia XA2 Ultra（H4233）：
     * 只有 BMA255 加速度計與 AK09916C 磁力計，Gravity / Rotation Vector 都是
     * Qualcomm 從那兩者算出來的**虛擬**感測器。沒有真的角速度來源，這個 app 就跑不了。
     *
     * 用磁力計的方位角微分來代替？iOS 端證實那條路會被每圈一次的空間磁場失真蓋掉
     * （見 CLAUDE.md 坑 13–15），而且這台的磁力計上限只有 50 Hz。不可行。
     */
    val unavailableReason: String?
        get() = when {
            gyroscope == null && gravity == null ->
                app.getString(R.string.engine_no_gyro_no_gravity)
            gyroscope == null ->
                // Compose 的 Text 不吃 markdown，這裡是純文字。
                app.getString(R.string.engine_no_gyro)
            gravity == null ->
                app.getString(R.string.engine_no_gravity)
            else -> null
        }

    /**
     * @param samplingPeriodUs 給 HAL 的**建議**取樣週期。10000 = 100 Hz，與 iOS 對齊。
     *   注意 Android 12 起超過 200 Hz 需要 HIGH_SAMPLING_RATE_SENSORS 權限 ——
     *   這個 app 不需要那麼快（50 Hz 以上只佔加權能量 0.72%），刻意不宣告。
     */
    fun start(
        samplingPeriodUs: Int = 10_000,
        mode: Mode = Mode.AUTOMATIC,
        samplingOnly: Boolean = false,
    ) {
        if (active) return
        val gyro = gyroscope
        val grav = gravity
        if (gyro == null || grav == null) {
            _state.update { it.copy(error = unavailableReason) }
            return
        }
        synchronized(lock) {
            samples.clear()
            gravities.clear()
            fields.clear()
            rawFields.clear()
            rawTimestamps.clear()
            latestGravity = null
            baseNanos = 0
            baseWallNanos = 0
            latestWallNanos = 0
            totalDegrees = 0.0
            lastSampleTime = null
            lastOmega = 0.0
            timestampBase = null
            latestRotationRate = null
            latestYawRadians = null
            latestField = null
            latestRawField = null
            fieldAccuracy = "—"
            phase = PhaseIntegrator()
            magneticCounter = MagneticRevolutionCounter()
            rawMagneticCounter = MagneticRevolutionCounter()
        }
        // 防禦性解除：萬一有殘留的註冊（例如上一輪的競態），先清乾淨再註冊。
        manager.unregisterListener(this)
        val t = HandlerThread("sensor").apply { start() }
        thread = t
        handler = Handler(t.looper)
        active = true
        this.samplingOnly = samplingOnly
        manager.registerListener(this, grav, samplingPeriodUs, handler)
        manager.registerListener(this, gyro, samplingPeriodUs, handler)
        // 診斷用的三顆用 SENSOR_DELAY_GAME（約 50 Hz）而不是 100 Hz：
        // 磁力計本來就跟不上 100 Hz，而且事件流愈多、陀螺儀那條愈容易被排擠
        // （坑 37 的教訓是量測本身的成本會算進被量的東西裡）。
        // 解捲只要求相鄰樣本之間不超過半圈，50 Hz 對 33 轉綽綽有餘。
        rotationVector?.let { manager.registerListener(this, it, DIAGNOSTIC_PERIOD_US, handler) }
        magnetometer?.let { manager.registerListener(this, it, DIAGNOSTIC_PERIOD_US, handler) }
        rawMagnetometer?.let { manager.registerListener(this, it, DIAGNOSTIC_PERIOD_US, handler) }
        displayDegrees = 0.0          // **只有這裡歸零** —— 手機此刻還照著指示擺著
        lastDisplayTime = 0.0
        lastDisplayOmega = 0.0
        stableSinceMs = 0
        val carried = _state.value
        val fresh = EngineState(
            running = true,
            mode = mode,
            phase = if (mode == Mode.AUTOMATIC) Phase.WAITING_FOR_STABILITY else Phase.MEASURING,
            gyroName = "${gyro.name} / ${gyro.vendor}",
            gravityName = "${grav.name} / ${grav.vendor}",
            gravityIsFused = gravityIsFused,
        )
        // 診斷跑不能把使用者上一次的量測結果洗掉 —— 光是「按下開始」就清空，
        // 主畫面在他返回時已經沒東西了。
        _state.value = if (samplingOnly) fresh.keepingMeasurementOf(carried) else fresh
    }

    fun stop() {
        if (!active) return
        active = false
        manager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        handler = null
        _state.update { it.copy(phase = Phase.STOPPED) }
        publish(running = false)
        // 診斷跑不分析：分析會寫對外讀數、寫匯出檔，而且成功時會 onAnalysisComplete
        // 自動存一筆歷史。把手機放在轉動的盤面上跑取樣診斷是很自然的事，
        // 那不該變成一筆使用者沒按過「開始量測」的記錄。
        if (!samplingOnly) runAnalysis()
    }

    /**
     * 離線分析。FFT 加加權捲積在兩萬筆樣本上要跑一下，不能擋主執行緒。
     *
     * **取樣率傳實測的有效速率，不要寫死 100。** XZ Premium 原生是 107.92 Hz，
     * 往下重取樣到 100 會讓線性內插對 50 Hz 以上的雜訊產生疊頻；用實測值的話
     * 網格間距與原生一致，內插幾乎是恆等映射。
     */
    private fun runAnalysis() {
        // **分析要吃校準後的 ω。** 譜峰倍數 = 峰值頻率 ÷ 轉盤基頻，而基頻是從 ω 算的、
        // 峰值頻率是從時間戳算的。只修正其中一邊，倍數就會整體偏 1/k
        // （k≈0.999 時是 0.1%，遠在 4% 諧波容差內，但沒有理由留著這個偏差）。
        val factor = calibrationFactor ?: 1.0
        val snapshot = snapshotSamples().let { raw ->
            if (factor == 1.0) raw else raw.map { it.copy(omega = it.omega * factor) }
        }
        val rate = _state.value.stats?.effectiveRateHz
        if (snapshot.size <= 64 || rate == null || rate <= 0) {
            // 失敗時也要把上一次的分析清掉，否則畫面會留著舊結果 —— 那比沒有結果更糟。
            // 樣本太少分析不了，但**匯出還是要做** —— 短量測的原始資料一樣有診斷價值，
            // 而且「分析失敗」本身就是最需要拿原始資料出來看的時候。
            val path = writeExport(null)
            _state.update {
                it.copy(
                    analysis = null,
                    analysisFailureReason = app.getString(R.string.engine_too_short, snapshot.size),
                    analyzing = false,
                    exportPath = path,
                )
            }
            return
        }
        _state.update { it.copy(analyzing = true, analysisFailureReason = null, analysis = null) }
        Thread {
            val result = MeasurementAnalysis.analyze(snapshot, sampleRate = rate)
            // 失敗的原因幾乎都是「找不到夠長的穩定區間」，講清楚比只說「失敗」有用。
            // 三種失敗要分開講。**最容易搞混的是第二種**：靜止不動的手機非常穩，
            // 閘門會整段放行，錯誤訊息若說「轉速不穩」使用者會完全找不到方向。
            val reason = if (result == null) {
                val window = com.roger.turntablerpm.core.StabilityGate.find(snapshot)
                when {
                    window == null ->
                        app.getString(R.string.engine_no_stable_window)
                    window.medianOmega / 6.0 < MeasurementAnalysis.MINIMUM_RPM ->
                        app.getString(
                            R.string.engine_platter_not_spinning, window.medianOmega / 6.0,
                        )
                    else ->
                        app.getString(R.string.engine_not_enough_data)
                }
            } else null
            // **分析完成後，對外讀數換成切過的平均值。**
            // 大字讀數原本是整段量測的平均，含開頭的加速段；分析用的是穩定區間。
            // 兩個數字不一致本來就會讓人困惑，但真正危險的是**校準拿到污染的值** ——
            // 那個 k 會被永久寫進 SharedPreferences，之後每一次讀數都錯。
            // 實測：整段平均 31.546 RPM，切掉 1.4 秒加速後是 32.15，差 1.9%。
            val path = writeExport(result)
            _state.update { previous ->
                previous.copy(
                    analysis = result,
                    analysisFailureReason = reason,
                    analyzing = false,
                    exportPath = path,
                    meanRPM = result?.meanRPM ?: previous.meanRPM,
                    rawMeanRPM = result?.let { it.meanRPM / factor } ?: previous.rawMeanRPM,
                    nominal = result?.let { SpeedStatistics.classify(it.meanRPM) } ?: previous.nominal,
                    errorPercent = result?.let { a ->
                        SpeedStatistics.classify(a.meanRPM)?.let {
                            SpeedStatistics.errorPercent(a.meanRPM, it)
                        }
                    } ?: previous.errorPercent,
                )
            }
            if (result != null) {
                onAnalysisComplete?.invoke(result, result.meanRPM / factor, _state.value.revolutions)
            }
        }.start()
    }

    /** 診斷欄位的快照。必須在持有 lock 的情況下呼叫。 */
    private data class Diagnostics(
        val phaseDegrees: Double,
        val gyroTotalDegrees: Double,
        val magneticTotalDegrees: Double,
        val yawDegrees: Double?,
        val fusedCalibration: Double?,
        val gravity: Vector3?,
        val rotationRate: Vector3?,
        val field: Vector3?,
        val rawField: Vector3?,
        val accuracy: String,
        val magneticTotal: Double,
        val magneticRevolutions: Int,
        val magneticSampleCount: Int,
        val magneticHorizontal: Double,
        val magneticRange: Pair<Double, Double>?,
        val refined: MagneticRevolutionCounter.Refined?,
        val rawMagneticTotal: Double,
        val rawMagneticRevolutions: Int,
        val rawMagneticSampleCount: Int,
        val rawRefined: MagneticRevolutionCounter.Refined?,
    )

    private fun snapshotDiagnostics(includeRefined: Boolean) = Diagnostics(
        phaseDegrees = phase.phaseDegrees,
        gyroTotalDegrees = phase.gyroTotalDegrees,
        magneticTotalDegrees = phase.magneticTotalDegrees,
        yawDegrees = latestYawRadians?.let { Math.toDegrees(it) },
        fusedCalibration = phase.calibrationEstimate,
        gravity = latestGravity,
        rotationRate = latestRotationRate,
        field = latestField,
        rawField = latestRawField,
        accuracy = fieldAccuracy,
        magneticTotal = magneticCounter.totalDegrees,
        magneticRevolutions = magneticCounter.revolutions,
        magneticSampleCount = magneticCounter.sampleCount,
        magneticHorizontal = magneticCounter.horizontalMagnitude,
        magneticRange = magneticCounter.horizontalRange,
        refined = if (includeRefined) magneticCounter.refined() else null,
        rawMagneticTotal = rawMagneticCounter.totalDegrees,
        rawMagneticRevolutions = rawMagneticCounter.revolutions,
        rawMagneticSampleCount = rawMagneticCounter.sampleCount,
        rawRefined = if (includeRefined) rawMagneticCounter.refined() else null,
    )

    /** 磁力計校準等級。Android 的等級是 0–3，直接給數字沒人看得懂。 */
    private fun accuracyLabel(accuracy: Int): String = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> app.getString(R.string.accuracy_high)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> app.getString(R.string.accuracy_medium)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> app.getString(R.string.accuracy_low)
        SensorManager.SENSOR_STATUS_UNRELIABLE -> app.getString(R.string.accuracy_unreliable)
        else -> "—"
    }

    /**
     * 把整包原始資料寫成 JSON。已經在分析執行緒上，不會擋畫面 ——
     * 3 分鐘的量測是兩萬筆樣本，編碼在主執行緒會頓一下。
     */
    private fun writeExport(analysis: MeasurementAnalysis?): String? = try {
        exportDir.mkdirs()
        MeasurementExport.write(snapshotFrames(), summaryMap(analysis), exportDir)?.absolutePath
    } catch (e: Exception) {
        // 匯出失敗不該讓量測結果跟著消失。
        null
    }

    /**
     * 摘要。鍵名跟 iOS 對齊，`tools/analyze_export.py` 才不用分兩套讀。
     * iOS 有而 Android 沒有的（磁力計那一組）就不寫，不要填 0 —— 0 會被讀成量到 0。
     */
    private fun summaryMap(analysis: MeasurementAnalysis?): Map<String, Any?> {
        val s = _state.value
        val d = LinkedHashMap<String, Any?>()
        d["meanRPM"] = s.meanRPM ?: 0.0
        d["rawMeanRPM"] = s.rawMeanRPM ?: 0.0
        d["appliedFactor"] = s.appliedFactor ?: 0.0
        d["instantRPM"] = s.instantRPM
        d["sampleCount"] = s.sampleCount
        d["elapsedSeconds"] = s.elapsedSeconds
        d["effectiveSampleRate"] = s.stats?.effectiveRateHz ?: 0.0
        d["revolutions"] = s.revolutions
        // 整圈數乘 360 會丟掉不足一圈的餘數，而校準比對要的正是總轉角本身。
        d["gyroTotalDegrees"] = synchronized(lock) { totalDegrees }
        // 標稱辨識失敗時不要寫 0 —— 那會被讀成「偏差正好是 0%」。
        s.errorPercent?.let { d["errorPercent"] = it }
        s.nominal?.let { d["nominalRPM"] = it.rpm }
        // Android 特有：時間戳誠不誠實、實際拿到多少取樣率，是這個平台的主要疑點。
        d["platformModel"] = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        d["androidSdk"] = android.os.Build.VERSION.SDK_INT
        d["timestampBase"] = s.timestampBase
        d["clockRatio"] = s.clockRatio
        d["wallElapsedSeconds"] = s.wallElapsedSeconds
        d["gyroName"] = s.gyroName
        d["gravityName"] = s.gravityName
        d["gravityIsFused"] = s.gravityIsFused
        s.stats?.let {
            d["jitterRatio"] = it.jitterRatio
            d["longGaps"] = it.longGaps
        }
        analysis?.let { a ->
            d["analysisWrmsPercent"] = a.wowFlutter.wrmsPercent
            d["analysisPeak2SigmaPercent"] = a.wowFlutter.peak2SigmaPercent
            d["analysisPeakToRMSRatio"] = a.wowFlutter.peakToRMSRatio
            d["analysisOnePerRevPercent"] = a.onePerRevolutionPercent
            d["analysisRotationHz"] = a.rotationHz
            d["analysisDurationSeconds"] = a.durationSeconds
            d["analysisTrimmedStartSeconds"] = a.trimmedStartSeconds
            d["analysisTrimmedEndSeconds"] = a.trimmedEndSeconds
            a.peakAngleDegrees?.let { p -> d["analysisPeakAngleDegrees"] = p }
            d["analysisPeaks"] = a.peaks.take(8).map {
                linkedMapOf<String, Any?>(
                    "hz" to it.frequencyHz,
                    "percent" to it.amplitudePercent,
                    "order" to it.orderOfRotation,
                    "harmonic" to it.isRotationHarmonic,
                )
            }
        }
        return d
    }

    /** 交出目前累積的樣本，供離線分析。 */
    fun snapshotSamples(): List<SpinSample> = synchronized(lock) { ArrayList(samples) }

    /**
     * 匯出用的逐樣本快照。長度一定對得起來 —— 兩個 list 在同一個鎖裡一起 append。
     *
     * 磁場只有最新一筆（那三顆走 50 Hz，跟陀螺儀的 100 Hz 對不齊），
     * 所以每一筆樣本帶的是「當下最新的那一次磁場讀數」。離線分析要的是軌跡形狀，
     * 重複幾筆相同的值不影響圓擬合。
     */
    fun snapshotFrames(): List<RawFrame> = synchronized(lock) {
        val n = minOf(samples.size, gravities.size, fields.size, rawFields.size)
        (0 until n).map {
            RawFrame(
                t = samples[it].t,
                omega = samples[it].omega,
                gravity = gravities[it],
                yaw = samples[it].yaw,
                field = fields[it],
                rawField = rawFields[it],
            )
        }
    }

    /**
     * 反旋轉盤面要顯示的角度，度。
     *
     * 用最後一筆的時間戳往前外推 —— 感測器是 100 Hz、畫面是 60/120 Hz，
     * 直接讀累積值會讓畫面一頓一頓的。
     */
    fun displayAngleDegrees(): Double {
        val base = displayDegrees
        val last = lastDisplayTime
        val wallBase = baseWallNanos
        if (last <= 0.0 || wallBase <= 0L) return base
        // 感測器時間戳與 elapsedRealtime 同一個時基（實測比值 0.99995），可以直接外推。
        val now = (SystemClock.elapsedRealtimeNanos() - wallBase) / 1e9
        val ahead = (now - last).coerceIn(0.0, 0.05)   // 最多補 50 ms，掉幀時不要暴衝
        return base + ahead * lastDisplayOmega
    }

    /**
     * 自動模式的階段推進。**不歸零顯示角度** —— 它的零點必須留在使用者按下開始的那一刻，
     * 否則反旋轉的文字方向會變成程式挑的隨機時刻決定（iOS 坑 21）。
     */
    private fun advanceAutoPhase(recentRPM: Double?) {
        if (_state.value.mode != Mode.AUTOMATIC) return
        val now = SystemClock.uptimeMillis()
        when (_state.value.phase) {
            Phase.WAITING_FOR_STABILITY -> {
                val stable = recentRPM != null && SpeedStatistics.classify(recentRPM) != null
                if (!stable) { stableSinceMs = 0; return }
                if (stableSinceMs == 0L) { stableSinceMs = now; return }
                if (now - stableSinceMs >= AUTO_STABLE_MS) {
                    // 丟掉等待期間的資料，從乾淨的狀態開始記錄。
                    synchronized(lock) {
                        samples.clear(); gravities.clear()
                    fields.clear(); rawFields.clear(); rawTimestamps.clear()
                        totalDegrees = 0.0; lastSampleTime = null; lastOmega = 0.0
                        baseNanos = 0; baseWallNanos = 0; latestWallNanos = 0
                    }
                    _state.update { it.copy(phase = Phase.MEASURING) }
                }
            }
            Phase.MEASURING -> {
                // 盤面停下就自動結束。門檻取最慢標稱轉速（16⅔）的一半。
                if (recentRPM != null && recentRPM < STOP_RPM) stop()
            }
            else -> Unit
        }
    }

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

            Sensor.TYPE_ROTATION_VECTOR -> {
                // 融合姿態 → 方位角。這是 iOS `attitude.yaw` 的對應物，
                // **也就是那條已經證實是同義反覆的路徑**（坑 11）。
                val r = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(r, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                synchronized(lock) { latestYawRadians = orientation[0].toDouble() }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val f = Vector3(
                    event.values[0].toDouble(),
                    event.values[1].toDouble(),
                    event.values[2].toDouble(),
                )
                synchronized(lock) {
                    latestField = f
                    fieldAccuracy = accuracyLabel(event.accuracy)
                    latestGravity?.let { magneticCounter.add(f, it) }
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                // 前三個是未扣偏置的原始讀數，後三個是系統估的偏置。
                // 這裡要的是原始值 —— 拿它跟已校準的並排，才看得出偏置估計器
                // 有沒有在量測過程中改動讀數。
                val f = Vector3(
                    event.values[0].toDouble(),
                    event.values[1].toDouble(),
                    event.values[2].toDouble(),
                )
                synchronized(lock) {
                    latestRawField = f
                    latestGravity?.let { rawMagneticCounter.add(f, it) }
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
                    displayDegrees += (t - (lastSampleTime ?: t)) * (omega + lastOmega) / 2.0
                    lastDisplayTime = t
                    lastDisplayOmega = omega
                    lastSampleTime = t
                    lastOmega = omega
                    latestRotationRate = rate
                    val sample = SpinSample(t = t, omega = omega, yaw = latestYawRadians)
                    samples += sample
                    gravities += g
                    fields += latestField
                    rawFields += latestRawField
                    rawTimestamps += t
                    phase.add(sample)
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

    /**
     * **一律用 `update {}`，不要 `_state.value = _state.value.copy(...)`。**
     *
     * 後者是讀-改-寫，而這裡有三個執行緒在寫：主執行緒的 stop()、publisher executor、
     * 分析執行緒。實測踩到：分析寫完之後，一個排在後面的舊 publish 任務用它先前
     * 讀到的舊值覆蓋回去 —— **分析結果靜默消失，畫面留著上一次的舊結果**，
     * 連失敗訊息都沒有。使用者連做兩次量測都以為沒反應。
     */
    private fun publish(running: Boolean) {
        data class Snap(
            val samples: List<SpinSample>, val times: DoubleArray,
            val base: String?, val wallSpan: Double, val degrees: Double,
            val diagnostics: Diagnostics,
        )
        val snap = synchronized(lock) {
            Snap(
                ArrayList(samples), rawTimestamps.toDoubleArray(), timestampBase,
                if (baseWallNanos > 0) (latestWallNanos - baseWallNanos) / 1e9 else 0.0,
                totalDegrees,
                // 圓擬合是 O(n)，但 refined() 每次都會重跑整包點；量測中不必那麼勤，
                // 所以只在停止之後算一次。
                snapshotDiagnostics(includeRefined = !running),
            )
        }
        val snapshot = snap.samples
        val times = snap.times
        val base = snap.base
        // 最近約 2 秒的平均，用來判斷「轉穩了沒」與「停了沒」。
        // 用累積平均不行 —— 那個量的是整段，反應太慢。
        val recent = snapshot.takeLast(200).let {
            if (it.size >= 20) SpeedStatistics.meanRPM(it) else null
        }
        val raw = SpeedStatistics.meanRPM(snapshot)
        val factor = calibrationFactor
        val mean = if (raw != null && factor != null) raw * factor else raw
        val nominal = mean?.let { SpeedStatistics.classify(it) }
        _state.update { previous ->
            previous.copy(
                running = running,
                sampleCount = snapshot.size,
                elapsedSeconds = if (snapshot.size >= 2) snapshot.last().t - snapshot.first().t else 0.0,
                instantRPM = (snapshot.lastOrNull()?.omega ?: 0.0) / 6.0 * (factor ?: 1.0),
                meanRPM = mean,
                rawMeanRPM = raw,
                appliedFactor = factor,
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

                latestOmega = snapshot.lastOrNull()?.omega ?: 0.0,
                phaseDegrees = snap.diagnostics.phaseDegrees,
                gyroTotalDegrees = snap.diagnostics.gyroTotalDegrees,
                magneticTotalDegrees = snap.diagnostics.magneticTotalDegrees,
                magneticYawDegrees = snap.diagnostics.yawDegrees,
                fusedCalibration = snap.diagnostics.fusedCalibration,
                gravityVector = snap.diagnostics.gravity,
                rotationRate = snap.diagnostics.rotationRate,
                calibratedField = snap.diagnostics.field,
                rawField = snap.diagnostics.rawField,
                fieldAccuracy = snap.diagnostics.accuracy,
                magneticTotal = snap.diagnostics.magneticTotal,
                magneticRevolutions = snap.diagnostics.magneticRevolutions,
                magneticSampleCount = snap.diagnostics.magneticSampleCount,
                magneticHorizontal = snap.diagnostics.magneticHorizontal,
                magneticHorizontalRange = snap.diagnostics.magneticRange,
                refined = snap.diagnostics.refined ?: previous.refined,
                rawMagneticTotal = snap.diagnostics.rawMagneticTotal,
                rawMagneticRevolutions = snap.diagnostics.rawMagneticRevolutions,
                rawMagneticSampleCount = snap.diagnostics.rawMagneticSampleCount,
                rawRefined = snap.diagnostics.rawRefined ?: previous.rawRefined,
            ).let { next ->
                if (!samplingOnly) next
                // 診斷跑：新算的取樣統計走自己的欄位，量測那一組原封不動還回去。
                else next.keepingMeasurementOf(previous).copy(samplingStats = next.stats)
            }
        }
        // 自動模式的相位推進會在「轉穩了」時把已收的樣本清掉、盤面停下時自己 stop()。
        // 那兩件事對取樣統計都是災難，所以診斷跑一律不進這條。
        if (running && !samplingOnly) advanceAutoPhase(recent)
    }

    private companion object {
        /** 連續穩定這麼久才真正開始記錄。 */
        const val AUTO_STABLE_MS = 3_000L

        /** 低於這個轉速視為盤面停下。16⅔ 的一半。 */
        const val STOP_RPM = 8.0

        /** 診斷感測器的取樣週期。20000 µs = 50 Hz。 */
        const val DIAGNOSTIC_PERIOD_US = 20_000
    }
}
