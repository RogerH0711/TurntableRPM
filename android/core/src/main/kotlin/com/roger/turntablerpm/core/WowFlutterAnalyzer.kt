package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.math.sqrt

data class WowFlutterResult(
    /** 加權均方根，單位 %。 */
    val wrmsPercent: Double,
    /** DIN 2σ 峰值：加權後 |d(t)| 的第 95 百分位（有 5% 的時間超過它）。 */
    val peak2SigmaPercent: Double,
    /** 加權後的時域序列，供繪圖用。 */
    val weightedSeries: DoubleArray,
) {
    /** 比值本身有診斷價值：約 1.96 是高斯型隨機抖動，約 1.41 是單頻正弦 wow。 */
    val peakToRMSRatio: Double
        get() = if (wrmsPercent > 0) peak2SigmaPercent / wrmsPercent else 0.0
}

/**
 * 規格 §3.4：加權抖晃率。
 *
 * 輸入必須是**未平滑**的偏差序列。把 WRMS 算在移動平均過的訊號上，
 * 等於把 4 Hz 加權峰值挖掉，得到的數字會漂亮得毫無意義。
 */
object WowFlutterAnalyzer {

    fun analyze(
        deviationPercent: DoubleArray,
        sampleRate: Double,
        guardSeconds: Double = 2.0,
    ): WowFlutterResult? {
        if (deviationPercent.size <= 16 || sampleRate <= 0) return null
        val weighted = applyWeighting(deviationPercent, sampleRate)

        val maxGuard = maxOf(0, (weighted.size - 1) / 2)
        val guardCount = minOf((guardSeconds * sampleRate).toInt(), maxGuard)
        val core = weighted.copyOfRange(guardCount, weighted.size - guardCount)
        if (core.isEmpty()) return null

        var sumSquares = 0.0
        for (v in core) sumSquares += v * v
        val rms = sqrt(sumSquares / core.size)

        val magnitudes = DoubleArray(core.size) { abs(core[it]) }
        magnitudes.sort()
        val index = minOf(magnitudes.size - 1, ((magnitudes.size - 1) * 0.95).toInt())

        return WowFlutterResult(rms, magnitudes[index], weighted)
    }

    /** 整段 FFT → 乘上 W(f) → IFFT。補零到 2 倍長度的 2 次冪，避免循環卷積把尾端繞回開頭。 */
    fun applyWeighting(deviationPercent: DoubleArray, sampleRate: Double): DoubleArray {
        val n = deviationPercent.size
        val size = FFT.nextPowerOfTwo(n * 2)
        val real = DoubleArray(size)
        val imag = DoubleArray(size)
        deviationPercent.copyInto(real, 0, 0, n)

        FFT.transform(real, imag, inverse = false)

        val df = sampleRate / size
        for (k in 0..size / 2) {
            val w = WowFlutterWeighting.weight(k * df)
            real[k] *= w
            imag[k] *= w
            val mirror = (size - k) % size
            if (mirror != k) {
                real[mirror] *= w
                imag[mirror] *= w
            }
        }

        FFT.transform(real, imag, inverse = true)
        return real.copyOfRange(0, n)
    }
}
