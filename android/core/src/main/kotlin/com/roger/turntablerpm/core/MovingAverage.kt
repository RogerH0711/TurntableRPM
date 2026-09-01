package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 規格 §3.3：**顯示路徑**用的移動平均。
 *
 * 這是低通濾波器，N 個樣本在 fs/N 有第一個零點。N=25（0.25 s）的零點正好落在
 * 加權曲線的 4 Hz 峰值上，所以抖晃率**絕對不能**算在平滑後的訊號上。
 */
object MovingAverage {

    /** 中心對齊移動平均，邊界以可用樣本數平均。 */
    fun apply(x: DoubleArray, window: Int): DoubleArray {
        if (window <= 1 || x.isEmpty()) return x
        val prefix = DoubleArray(x.size + 1)
        for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]
        val half = window / 2
        val out = DoubleArray(x.size)
        for (i in x.indices) {
            val lo = maxOf(0, i - half)
            val hi = minOf(x.size, i - half + window)
            out[i] = (prefix[hi] - prefix[lo]) / (hi - lo)
        }
        return out
    }

    /** 振幅響應 |H(f)|。 */
    fun magnitudeResponse(frequency: Double, window: Int, sampleRate: Double): Double {
        if (window <= 1) return 1.0
        if (frequency == 0.0) return 1.0
        val denominator = window * sin(PI * frequency / sampleRate)
        if (abs(denominator) < 1e-15) return 1.0
        return abs(sin(PI * frequency * window / sampleRate) / denominator)
    }

    /** 第一個零點 —— 這個視窗完全砍掉的頻率。 */
    fun firstNullFrequency(window: Int, sampleRate: Double): Double = sampleRate / window

    /** −3 dB 截止頻率（近似係數 0.4429）。 */
    fun cutoffFrequency(window: Int, sampleRate: Double): Double = 0.4429 * sampleRate / window
}
