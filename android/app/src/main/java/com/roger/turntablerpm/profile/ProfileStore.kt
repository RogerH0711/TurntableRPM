package com.roger.turntablerpm.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 唱盤設定檔的存放處。
 *
 * 跟 [com.roger.turntablerpm.calibration.CalibrationStore] 同一套作法：
 * SharedPreferences 加手寫 org.json。欄位少，而且格式一旦寫進使用者的手機
 * 就不該隨著程式庫版本漂移。
 *
 * **不綁機型。** 校準是「這一支陀螺儀」的性質，換手機必須失效；
 * 唱盤設定檔是「這一台唱盤」的性質，換手機應該跟著搬過去。
 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<TurntableProfile>>(emptyList())
    val profiles: StateFlow<List<TurntableProfile>> = _profiles.asStateFlow()

    /** 目前使用中的那一台。分析頁拿它比對規格與傳動比。 */
    val active: TurntableProfile? get() = _profiles.value.firstOrNull { it.isActive }

    init { load() }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        _profiles.value = try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { decode(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<TurntableProfile>) {
        _profiles.value = list
        val array = JSONArray()
        list.forEach { array.put(encode(it)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    /** 新增一台。第一台自動設為使用中 —— 只有一台的時候不該還要使用者去選。 */
    fun add(): TurntableProfile {
        val list = _profiles.value
        val profile = TurntableProfile(id = System.currentTimeMillis(), isActive = list.isEmpty())
        persist(list + profile)
        return profile
    }

    /**
     * 更新一台。**設為使用中時會把其他台取消** —— 同時有兩台使用中的話，
     * 分析頁不知道要拿誰的規格比對。
     */
    fun update(profile: TurntableProfile) {
        persist(
            _profiles.value.map {
                when {
                    it.id == profile.id -> profile
                    profile.isActive -> it.copy(isActive = false)
                    else -> it
                }
            },
        )
    }

    fun delete(id: Long) {
        val remaining = _profiles.value.filterNot { it.id == id }
        // 刪掉的剛好是使用中的那台就把第一台頂上，否則規格比對會無聲地消失。
        persist(
            if (remaining.isEmpty() || remaining.any { it.isActive }) remaining
            else remaining.mapIndexed { i, p -> p.copy(isActive = i == 0) },
        )
    }

    private fun encode(p: TurntableProfile) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("maker", p.maker)
        put("note", p.note)
        put("isActive", p.isActive)
        // null 要寫成 JSON null，不是 0 —— 「還沒填」跟「填了 0」是兩回事。
        putOpt("specWowFlutterPercent", p.specWowFlutterPercent)
        putOpt("pulleyDiameterMM", p.pulleyDiameterMM)
        putOpt("platterDiameterMM", p.platterDiameterMM)
        putOpt("beltThicknessMM", p.beltThicknessMM)
        putOpt("loadSlopeRPMPerGram", p.loadSlopeRPMPerGram)
        putOpt("loadPhoneEffectRPM", p.loadPhoneEffectRPM)
        put("loadIsSignificant", p.loadIsSignificant)
        putOpt("loadMeasuredAtMillis", p.loadMeasuredAtMillis)
    }

    private fun decode(o: JSONObject): TurntableProfile? = try {
        TurntableProfile(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            maker = o.optString("maker", ""),
            note = o.optString("note", ""),
            isActive = o.optBoolean("isActive", false),
            specWowFlutterPercent = o.optDoubleOrNull("specWowFlutterPercent"),
            pulleyDiameterMM = o.optDoubleOrNull("pulleyDiameterMM"),
            platterDiameterMM = o.optDoubleOrNull("platterDiameterMM"),
            beltThicknessMM = o.optDoubleOrNull("beltThicknessMM"),
            loadSlopeRPMPerGram = o.optDoubleOrNull("loadSlopeRPMPerGram"),
            loadPhoneEffectRPM = o.optDoubleOrNull("loadPhoneEffectRPM"),
            loadIsSignificant = o.optBoolean("loadIsSignificant", false),
            loadMeasuredAtMillis = if (o.isNull("loadMeasuredAtMillis")) null
                else o.optLong("loadMeasuredAtMillis").takeIf { it > 0 },
        )
    } catch (_: Exception) {
        null
    }

    /** `optDouble` 缺值時回 NaN，而 NaN 會一路混進計算。這裡明確轉成 null。 */
    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    companion object {
        private const val KEY = "turntableProfiles"
    }
}
