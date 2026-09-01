package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.math.sqrt

/** 規格 §3.2：平均轉速、穩定閘門、標稱轉速自動辨識。 */
object SpeedStatistics {

    /** 梯形積分平均。**用真實 dt 加權**，容忍派送抖動 —— Android 上這點尤其重要。 */
    fun meanOmega(samples: List<SpinSample>): Double? {
        if (samples.size < 2) return null
        val span = samples.last().t - samples.first().t
        if (span <= 0) return null
        var area = 0.0
        for (i in 1 until samples.size) {
            val dt = samples[i].t - samples[i - 1].t
            area += dt * (samples[i].omega + samples[i - 1].omega) / 2.0
        }
        return area / span
    }

    fun meanRPM(samples: List<SpinSample>): Double? = meanOmega(samples)?.let { it / 6.0 }

    /** 穩定閘門：相對標準差夠小才開始累積統計，避免把啟動加速段算進去。 */
    fun isStable(samples: List<SpinSample>, relativeStdDevLimit: Double = 0.02): Boolean {
        if (samples.size < 2) return false
        val mean = meanOmega(samples) ?: return false
        if (mean <= 0) return false
        var sumSquares = 0.0
        for (s in samples) {
            val d = s.omega - mean
            sumSquares += d * d
        }
        val sd = sqrt(sumSquares / samples.size)
        return sd / mean <= relativeStdDevLimit
    }

    /** 最近鄰標稱轉速。相鄰標稱值最近的一對是 33⅓ 與 45（差 35%），所以 ±8% 的窗很安全。 */
    fun classify(
        rpm: Double,
        tolerance: Double = 0.08,
        candidates: List<TurntableSpeed> = TurntableSpeed.STANDARD,
    ): TurntableSpeed? {
        var best: TurntableSpeed? = null
        var bestDistance = Double.MAX_VALUE
        for (candidate in candidates) {
            val d = abs(rpm - candidate.rpm) / candidate.rpm
            if (d < bestDistance) {
                bestDistance = d
                best = candidate
            }
        }
        return if (bestDistance <= tolerance) best else null
    }

    fun errorPercent(rpm: Double, nominal: TurntableSpeed): Double =
        (rpm - nominal.rpm) / nominal.rpm * 100.0
}
