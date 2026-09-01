package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FFTTest {

    @Test
    fun `正逆轉換能還原原訊號`() {
        val n = 256
        val rng = SplitMix64(9)
        val original = DoubleArray(n) { rng.nextGaussian() }
        val real = original.copyOf()
        val imag = DoubleArray(n)
        FFT.transform(real, imag, inverse = false)
        FFT.transform(real, imag, inverse = true)
        for (i in 0 until n) {
            assertTrue(abs(real[i] - original[i]) < 1e-9, "第 $i 筆")
            assertTrue(abs(imag[i]) < 1e-9)
        }
    }

    @Test
    fun `已知正弦的振幅與頻率都正確`() {
        val fs = 100.0
        val n = 1024
        val freq = fs * 16 / n          // 剛好落在一個頻率格上，避免扇形損失
        val amplitude = 2.5
        val x = DoubleArray(n) { amplitude * sin(2.0 * PI * freq * it / fs) }
        val (frequencies, amplitudes) = FFT.amplitudeSpectrum(x, fs)
        val peak = amplitudes.indices.maxBy { amplitudes[it] }
        assertTrue(abs(frequencies[peak] - freq) < fs / n, "峰值頻率 ${frequencies[peak]}，期望 $freq")
        assertTrue(abs(amplitudes[peak] - amplitude) < amplitude * 0.02,
            "峰值振幅 ${amplitudes[peak]}，期望 $amplitude")
    }

    @Test
    fun `2 的次冪判斷`() {
        assertTrue(FFT.isPowerOfTwo(1) && FFT.isPowerOfTwo(1024))
        assertTrue(!FFT.isPowerOfTwo(0) && !FFT.isPowerOfTwo(1000))
        assertEquals(1024, FFT.nextPowerOfTwo(1000))
        assertEquals(1024, FFT.nextPowerOfTwo(1024))
    }
}
