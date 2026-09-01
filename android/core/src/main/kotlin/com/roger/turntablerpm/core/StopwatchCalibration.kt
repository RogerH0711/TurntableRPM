package com.roger.turntablerpm.core

/**
 * 碼錶校準的結果。
 *
 * **為什麼碼錶是主要路徑而不是備援。** iOS 端的指南針自動校準走過兩條路都失敗了：
 * `attitude.yaw` 是融合結果，跟陀螺儀是同義反覆；原始磁力計則被每圈一次的空間磁場失真
 * 蓋掉，實測失真振幅 29.9 µT 還大過訊號振幅 26.0 µT。碼錶反而乾淨：
 * 100 圈搭配 ±0.3 秒的人為誤差就是 0.17%，而且完全不經過手機的任何感測器。
 *
 * k 是這支陀螺儀的固定性質（比例因子誤差是乘性的、不隨時間變），
 * 所以量一次存起來就能一直用。
 *
 * `recordedAt` 用 epoch 毫秒而不是 java.time —— minSdk 28 沒有 desugaring 就沒有 java.time，
 * 而這個模組刻意不依賴任何平台設施。
 */
data class StopwatchCalibration(
    /** 修正倍率。實際使用時 RPM_true = k × RPM_measured。 */
    val factor: Double,
    val revolutions: Int,
    val seconds: Double,
    /** 校準當下 App 自己量到的轉速（未修正）。 */
    val measuredRPM: Double,
    /** 碼錶推算出來的真實轉速。 */
    val trueRPM: Double,
    val recordedAtEpochMillis: Long,
    /**
     * 機型識別字串（Android 上是 `Build.MODEL`）。k 是綁定在這支陀螺儀上的，
     * 換機或從備份還原之後必須失效，不能默默套用到別台裝置。
     */
    val deviceModel: String,
) {

    /**
     * 這次校準的相對精度（比例值，非百分比）。
     *
     * 主要誤差來自按碼錶的人為時間誤差，圈數愈多攤得愈薄：
     * 33⅓ 轉 100 圈是 180 秒，±0.3 秒 → 0.17%；200 圈 → 0.08%。
     */
    fun precision(timingErrorSeconds: Double = 0.3): Double =
        if (seconds > 0) timingErrorSeconds / seconds else Double.POSITIVE_INFINITY

    /**
     * 這個 k 合不合理。
     *
     * MEMS 陀螺儀的比例因子誤差是百分之幾的等級，不可能到幾十趴。
     * 落在範圍外幾乎一定是輸入打錯（把 100 圈打成 10 圈就會得到 k≈0.1），
     * 存下去會讓之後每一次讀數都錯，所以要擋。
     */
    val isPlausible: Boolean get() = factor > 0.8 && factor < 1.25

    /** 套用到一個未修正的轉速上。 */
    fun apply(rpm: Double): Double = rpm * factor

    companion object {
        fun create(
            revolutions: Int,
            seconds: Double,
            measuredRPM: Double,
            deviceModel: String,
            recordedAtEpochMillis: Long = System.currentTimeMillis(),
        ): StopwatchCalibration? {
            if (revolutions <= 0 || seconds <= 0 || measuredRPM <= 0) return null
            val trueRPM = 60.0 * revolutions / seconds
            return StopwatchCalibration(
                factor = trueRPM / measuredRPM,
                revolutions = revolutions,
                seconds = seconds,
                measuredRPM = measuredRPM,
                trueRPM = trueRPM,
                recordedAtEpochMillis = recordedAtEpochMillis,
                deviceModel = deviceModel,
            )
        }
    }
}
