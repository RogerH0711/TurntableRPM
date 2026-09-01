package com.roger.turntablerpm.calibration

import android.content.Context
import android.os.Build
import com.roger.turntablerpm.core.StopwatchCalibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 碼錶校準的存放處。
 *
 * k 是綁在**這一支**陀螺儀上的固定性質，所以連機型一起存。
 * 從備份還原到新手機時 SharedPreferences 會跟著搬過去，但那支陀螺儀的 k 不一樣 ——
 * **機型對不上就必須失效**，不能默默套用，否則之後每一次讀數都是錯的而且看不出來。
 *
 * 用 org.json 手動序列化而不是 kotlinx.serialization：只有六個欄位，
 * 而且格式一旦寫進使用者的手機就不該隨著程式庫版本漂移。
 */
class CalibrationStore(context: Context) {

    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    private val _calibration = MutableStateFlow<StopwatchCalibration?>(null)

    /** 目前生效的校準。機型對不上時為 null。 */
    val calibration: StateFlow<StopwatchCalibration?> = _calibration.asStateFlow()

    private val _mismatched = MutableStateFlow<StopwatchCalibration?>(null)

    /** 存著但因為機型不符而被停用的那一筆，用來在畫面上解釋為什麼校準不見了。 */
    val mismatched: StateFlow<StopwatchCalibration?> = _mismatched.asStateFlow()

    init { load() }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        val stored = try { decode(JSONObject(raw)) } catch (_: Exception) { null } ?: return
        if (stored.deviceModel == deviceModel) {
            _calibration.value = stored
            _mismatched.value = null
        } else {
            _calibration.value = null
            _mismatched.value = stored
        }
    }

    /** 存下新的校準。不合理的 k 直接拒絕 —— 幾乎一定是輸入打錯。 */
    fun save(c: StopwatchCalibration): Boolean {
        if (!c.isPlausible) return false
        prefs.edit().putString(KEY, encode(c).toString()).apply()
        _calibration.value = c
        _mismatched.value = null
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _calibration.value = null
        _mismatched.value = null
    }

    /** 套用到未修正的轉速。沒有校準時原樣回傳。 */
    fun apply(rpm: Double): Double = _calibration.value?.apply(rpm) ?: rpm

    private fun encode(c: StopwatchCalibration) = JSONObject().apply {
        put("factor", c.factor)
        put("revolutions", c.revolutions)
        put("seconds", c.seconds)
        put("measuredRPM", c.measuredRPM)
        put("recordedAt", c.recordedAtEpochMillis)
        put("deviceModel", c.deviceModel)
    }

    private fun decode(o: JSONObject) = StopwatchCalibration(
        factor = o.getDouble("factor"),
        revolutions = o.getInt("revolutions"),
        seconds = o.getDouble("seconds"),
        measuredRPM = o.getDouble("measuredRPM"),
        trueRPM = 60.0 * o.getInt("revolutions") / o.getDouble("seconds"),
        recordedAtEpochMillis = o.getLong("recordedAt"),
        deviceModel = o.getString("deviceModel"),
    )

    companion object {
        private const val KEY = "stopwatchCalibration"

        /**
         * 這台裝置的機型識別。`Build.MODEL` 是使用者看得懂的名稱（例如 G8142），
         * 但同型號不同批次的陀螺儀仍可能有差 —— 這個檢查擋的是「換機／備份還原」，
         * 不是「同型號之間的個體差異」。後者只能靠使用者重新校準。
         */
        val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "unknown" }
    }
}
