package com.roger.turntablerpm.core

import kotlin.math.abs

/** 規格 §3.3：瞬時偏差 d(t) = (ω(t) − ω̄) / ω̄ × 100%。 */
object DeviationSeries {

    data class Result(val mean: Double, val deviationPercent: DoubleArray)

    fun make(omega: DoubleArray): Result? {
        if (omega.isEmpty()) return null
        val mean = omega.average()
        if (mean <= 0) return null
        return Result(mean, DoubleArray(omega.size) { (omega[it] - mean) / mean * 100.0 })
    }

    data class MaxDeviation(val value: Double, val windowSeconds: Double)

    /** 最大偏差。**必須連同 windowSeconds 一起回報** —— 不標頻寬的最大偏差是無法比較的數字。 */
    fun maxDeviation(
        deviationPercent: DoubleArray,
        sampleRate: Double,
        smoothingWindow: Int,
    ): MaxDeviation {
        val smoothed = MovingAverage.apply(deviationPercent, smoothingWindow)
        var peak = 0.0
        for (v in smoothed) if (abs(v) > peak) peak = abs(v)
        return MaxDeviation(peak, smoothingWindow / sampleRate)
    }
}
