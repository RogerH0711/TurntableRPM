package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil

data class CalibrationResult(
    /** 修正倍率 k，實際使用時 ω_cal = k × ω_gyro。 */
    val factor: Double,
    val revolutions: Int,
    val gyroAngleDegrees: Double,
    val magneticAngleDegrees: Double,
) {
    /** 給定磁北 yaw 的角度雜訊，回推這次校準的相對精度（比例值，非百分比）。 */
    fun precision(yawNoiseDegrees: Double): Double =
        if (magneticAngleDegrees > 0) yawNoiseDegrees / magneticAngleDegrees
        else Double.POSITIVE_INFINITY
}

/**
 * 倍率 k 到底能不能採信。
 *
 * 這個型別的存在理由是一次真機實測（TD 235 EV，68.1 秒 36 圈）：
 * app 估出 k = 0.99994。碼錶同步量測的真值是 0.99915 —— **它猜對了，但那是矇的。**
 *
 * 融合出來的方位角（iOS 的 `attitude.yaw`、Android 的 `TYPE_ROTATION_VECTOR`）
 * 在盤面高速連續轉動時會把磁修正降權到幾乎沒有貢獻，方位角退化成陀螺儀積分本身。
 * 那次量測的兩條路徑是 12987° 與 12986° —— **只差 1°，遠低於磁力計 5° 的雜訊底線。**
 * 換句話說這個估計不管陀螺儀準不準，都會吐出 k ≈ 1。
 *
 * 關鍵在於：兩條路徑幾乎沒有分歧、k ≈ 1 時，你無法區分
 * 「陀螺儀真的很準」與「方位角根本就是陀螺儀積分」。**資料本身不含這個資訊。**
 * 這次剛好是前者，但那是運氣，不是量測。
 * 所以這種情況一律回報 [Indistinguishable]，UI 不准說「可以參考了」。
 *
 * 真正獨立的路徑見 [MagneticRevolutionCounter]。
 */
sealed interface CalibrationConfidence {
    /** 還不滿一圈，算不出倍率。 */
    data object Insufficient : CalibrationConfidence

    /** 兩條路徑的分歧量沉在磁力計雜訊底線以下 —— 倍率不可採信。 */
    data class Indistinguishable(
        val divergenceDegrees: Double,
        val noiseFloorDegrees: Double,
    ) : CalibrationConfidence

    /** 分歧量高出雜訊底線，倍率可以採信。[precision] 是相對精度（比例值，非百分比）。 */
    data class Usable(val precision: Double) : CalibrationConfidence

    val isUsable: Boolean get() = this is Usable
}

/**
 * 規格 §3.7：指南針自動校準。
 *
 * 陀螺儀積分的總角度會被比例因子誤差放大；磁北方位角是絕對量測、長期不漂移。
 * 比較同一時間窗內的總轉角就得到修正倍率。時間窗切在整數圈上，
 * 好讓房間座標系裡固定磁源造成的每圈一次失真在相減時抵消。
 *
 * **實測結論是這條路走不通**（見 [CalibrationConfidence]），留著是為了診斷，
 * 以及為了讓「它為什麼走不通」在畫面上看得見。
 */
object ScaleCalibrator {

    fun calibrate(samples: List<SpinSample>): CalibrationResult? {
        if (samples.size < 2) return null

        val gyroAngle = DoubleArray(samples.size)
        for (i in 1 until samples.size) {
            val dt = samples[i].t - samples[i - 1].t
            gyroAngle[i] = gyroAngle[i - 1] + dt * (samples[i].omega + samples[i - 1].omega) / 2.0
        }

        val magAngle = DoubleArray(samples.size)
        var accumulated = 0.0
        var previous: Double? = null
        for (i in samples.indices) {
            val yaw = samples[i].yaw ?: return null
            previous?.let { p ->
                var delta = yaw - p
                while (delta > PI) delta -= 2 * PI
                while (delta < -PI) delta += 2 * PI
                accumulated += delta
            }
            previous = yaw
            magAngle[i] = abs(accumulated * 180.0 / PI)
        }

        val total = magAngle[magAngle.size - 1]
        val revolutions = (total / 360.0).toInt()
        if (revolutions < 1) return null

        // 切在整數圈上 —— 固定磁源造成的每圈一次失真才會在相減時抵消。
        val target = revolutions * 360.0
        var end = samples.size - 1
        for (i in magAngle.indices) {
            if (magAngle[i] >= target) { end = i; break }
        }
        if (gyroAngle[end] <= 0) return null

        return CalibrationResult(
            factor = magAngle[end] / gyroAngle[end],
            revolutions = revolutions,
            gyroAngleDegrees = gyroAngle[end],
            magneticAngleDegrees = magAngle[end],
        )
    }

    /**
     * 判斷倍率 k 能不能採信。見 [CalibrationConfidence] 的說明。
     *
     * 判準不是圈數，是**兩條路徑有沒有真的分歧**。舊版 UI 用「圈數 >= 30」
     * 當判準，結果在 36 圈時對著一個「無法區分對錯」的數字說「可以參考了」。
     */
    fun confidence(
        gyroTotalDegrees: Double,
        magneticTotalDegrees: Double,
        revolutions: Int,
        yawNoiseDegrees: Double = 5.0,
        minimumSigma: Double = 3.0,
    ): CalibrationConfidence {
        if (revolutions < 1 || gyroTotalDegrees <= 0 || magneticTotalDegrees <= 0) {
            return CalibrationConfidence.Insufficient
        }
        val divergence = abs(magneticTotalDegrees - gyroTotalDegrees)
        val noiseFloor = yawNoiseDegrees * minimumSigma
        if (divergence <= noiseFloor) {
            return CalibrationConfidence.Indistinguishable(divergence, noiseFloor)
        }
        return CalibrationConfidence.Usable(yawNoiseDegrees / magneticTotalDegrees)
    }

    /** 手動備援：碼錶量 N 圈用了 T 秒。 */
    fun manualFactor(revolutions: Int, seconds: Double, measuredRPM: Double): Double? {
        if (revolutions <= 0 || seconds <= 0 || measuredRPM <= 0) return null
        return 60.0 * revolutions / seconds / measuredRPM
    }

    /**
     * 碼錶法的精度，用來在 UI 上老實告訴使用者手動不一定比較準。
     * 100 圈搭配 ±0.3 s 的人為誤差 → 0.17%；200 圈 → 0.08%。
     */
    fun manualPrecision(revolutions: Int, rpm: Double, timingErrorSeconds: Double): Double {
        val seconds = 60.0 * revolutions / rpm
        return if (seconds > 0) timingErrorSeconds / seconds else Double.POSITIVE_INFINITY
    }

    /** 指南針校準要跑幾圈才到得了目標精度。 */
    fun requiredRevolutions(yawNoiseDegrees: Double, targetPrecision: Double): Int {
        if (targetPrecision <= 0) return Int.MAX_VALUE
        return ceil(yawNoiseDegrees / (360.0 * targetPrecision)).toInt()
    }
}
