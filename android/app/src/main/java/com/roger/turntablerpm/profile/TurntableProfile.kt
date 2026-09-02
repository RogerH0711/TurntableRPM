package com.roger.turntablerpm.profile

/**
 * 一台唱盤的設定檔。
 *
 * 存的是**唱盤自己的性質**，跟單次量測無關的那些：原廠規格、傳動鏈尺寸。
 * 有了它，量測結果才能自動跟規格比對，頻譜也才知道「35.3 倍」對不對得上
 * 這台盤的傳動比 —— 那是把「有一根 18.85 Hz 的峰」變成「那是你的馬達」的關鍵。
 *
 * 選填的欄位一律用 null 表示「還沒填」，不要用 0 —— 「規格 0%」跟「不知道規格」
 * 是完全不同的兩件事，後者不該讓分析頁跳出「超規格無限倍」。
 */
data class TurntableProfile(
    val id: Long,
    val name: String = "",
    val maker: String = "",
    /** 原廠的抖晃率規格，%。用來在分析頁直接標出超規格多少。 */
    val specWowFlutterPercent: Double? = null,
    val note: String = "",
    /** 目前選用的那一台。同時只會有一個為 true。 */
    val isActive: Boolean = false,

    // ── 傳動鏈尺寸（選填）────────────────────────────────────────────
    //
    // 知道這幾個就能預測馬達的轉速頻率，把頻譜上「非諧波的某某倍」對上實體零件。
    // 皮帶厚度會加在兩邊的有效直徑上，所以比值是 (D+t)/(d+t) 而不是 D/d。

    /** 馬達皮帶輪直徑，mm。 */
    val pulleyDiameterMM: Double? = null,
    /** 皮帶接觸的盤面直徑，mm（外盤緣或內盤，看皮帶跑在哪）。 */
    val platterDiameterMM: Double? = null,
    /** 皮帶厚度，mm。 */
    val beltThicknessMM: Double? = null,

    // ── 載重測試 ────────────────────────────────────────────────────
    //
    // 手機的重量會不會把唱盤拖慢，完全取決於馬達型式（同步交流馬達幾乎為零，
    // 無調速直流馬達最大）。查表沒有用，要實測。

    /** 兩點外插量到的斜率，RPM/g。null 代表還沒測。 */
    val loadSlopeRPMPerGram: Double? = null,
    /** 量測時手機造成的轉速變化，RPM。負值代表被拖慢。 */
    val loadPhoneEffectRPM: Double? = null,
    /** 斜率有沒有超出量測雜訊。false 代表這台盤對載重不敏感。 */
    val loadIsSignificant: Boolean = false,
    val loadMeasuredAtMillis: Long? = null,
) {
    val hasLoadTest: Boolean get() = loadSlopeRPMPerGram != null

    /** 預期的傳動比 = 馬達轉速 ÷ 轉盤轉速。尺寸不齊時為 null。 */
    val expectedDriveRatio: Double?
        get() {
            val d = pulleyDiameterMM ?: return null
            val bigD = platterDiameterMM ?: return null
            if (d <= 0 || bigD <= 0) return null
            return (bigD + (beltThicknessMM ?: 0.0)) / (d + (beltThicknessMM ?: 0.0))
        }

    val displayName: String
        get() = listOf(maker, name).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "未命名唱盤" }
}
