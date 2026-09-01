package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 純 Kotlin 的 radix-2 Cooley–Tukey FFT。
 *
 * 刻意不用任何外部函式庫：這個模組要能在純 JVM 上跑測試。
 * iOS 端實測（純 Swift、無 vDSP）：1 分鐘 0.13 s、3 分鐘 0.50 s、10 分鐘 1.25 s，
 * 所以加速不是必要的 —— 但 Android 端要在實機上重測，2017 年的 SoC 未必一樣。
 */
object FFT {

    fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    fun nextPowerOfTwo(atLeast: Int): Int {
        var size = 1
        while (size < atLeast) size = size shl 1
        return size
    }

    /**
     * 原地轉換。`real.size` 必須是 2 的次冪，且與 `imag.size` 相同。
     * `inverse == true` 時輸出已除以 N。
     */
    fun transform(real: DoubleArray, imag: DoubleArray, inverse: Boolean = false) {
        val n = real.size
        require(n == imag.size) { "real 與 imag 長度必須相同" }
        require(isPowerOfTwo(n)) { "長度必須是 2 的次冪" }
        if (n == 1) return

        // 位元反轉排序
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // 蝶形運算
        var length = 2
        while (length <= n) {
            val half = length / 2
            val base = (if (inverse) 2.0 else -2.0) * PI / length
            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val angle = base * k
                    val wr = cos(angle)
                    val wi = sin(angle)
                    val a = start + k
                    val b = a + half
                    val tr = real[b] * wr - imag[b] * wi
                    val ti = real[b] * wi + imag[b] * wr
                    real[b] = real[a] - tr
                    imag[b] = imag[a] - ti
                    real[a] += tr
                    imag[a] += ti
                }
                start += length
            }
            length = length shl 1
        }

        if (inverse) {
            val scale = 1.0 / n
            for (i in 0 until n) {
                real[i] *= scale
                imag[i] *= scale
            }
        }
    }

    data class Spectrum(val frequencies: DoubleArray, val amplitudes: DoubleArray)

    /** 實數輸入的單邊振幅頻譜（含 Hann 窗與振幅修正），供頻譜圖使用。 */
    fun amplitudeSpectrum(x: DoubleArray, sampleRate: Double): Spectrum {
        val n = x.size
        if (n < 4 || sampleRate <= 0) return Spectrum(DoubleArray(0), DoubleArray(0))
        val size = nextPowerOfTwo(n)
        val real = DoubleArray(size)
        val imag = DoubleArray(size)
        var windowSum = 0.0
        for (i in 0 until n) {
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
            real[i] = x[i] * w
            windowSum += w
        }
        transform(real, imag, inverse = false)

        val bins = size / 2 + 1
        val frequencies = DoubleArray(bins)
        val amplitudes = DoubleArray(bins)
        val df = sampleRate / size
        for (k in 0 until bins) {
            frequencies[k] = k * df
            val magnitude = sqrt(real[k] * real[k] + imag[k] * imag[k])
            val scale = if (k == 0 || k == size / 2) 1.0 else 2.0
            amplitudes[k] = magnitude * scale / windowSum
        }
        return Spectrum(frequencies, amplitudes)
    }
}
