package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FFT 前必做。Android 的取樣間隔抖動比 iOS 大得多，這一層是整條分析路徑的前提。 */
class UniformResamplerTest {

    @Test
    fun `從抖動的時間戳還原已知正弦`() {
        val rng = SplitMix64(31)
        val samples = ArrayList<SpinSample>()
        var t = 0.0
        while (t < 20.0) {
            samples += SpinSample(t = t, omega = 200.0 + 1.0 * sin(2.0 * PI * 0.5 * t))
            t += 0.01 * (0.6 + 0.8 * rng.nextUniform())
        }
        val resampled = UniformResampler.resample(samples, sampleRate = 100.0)
        assertTrue(resampled != null, "重採樣不該回 null")
        val values = resampled.values
        for (i in values.indices) {
            val time = i / 100.0
            val expected = 200.0 + 1.0 * sin(2.0 * PI * 0.5 * time)
            assertTrue(abs(values[i] - expected) < 0.01, "第 $i 筆：期望 $expected，實得 ${values[i]}")
        }
    }

    @Test
    fun `樣本太少要回 null`() {
        assertNull(UniformResampler.resample(listOf(SpinSample(0.0, 200.0)), 100.0))
    }
}
