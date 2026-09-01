package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class PolarAccumulatorTest {

    /**
     * 每格的樣本數是 fs × T ÷ 格數，**與轉速無關** —— 60 秒 100 Hz、72 格時每格恆為 83 筆。
     *
     * **刻意不檢查各格之間的離散度。** 那個量跟轉速非常有關：取樣步長與格寬近乎可公度時，
     * 取樣會系統性地擠進特定格子。實測全距 16⅔ 轉 5、33⅓ 轉 36、45 轉 15、78 轉 6
     * （33⅓ 轉每取樣走 2°、格寬 5°，是最糟的組合）。
     * 這是離散取樣的必然結果，不是缺陷 —— 熱圖看的是各格的**平均**偏差，不是樣本數。
     */
    @Test
    fun `每格樣本數與轉速無關`() {
        val expected = Golden.number("polar_samples_per_bin_60s")
        for (rpm in listOf(50.0 / 3.0, 100.0 / 3.0, 45.0, 78.0)) {
            val acc = PolarAccumulator(binCount = 72)
            val fs = 100.0
            val n = (60 * fs).toInt()
            var angle = 0.0
            for (i in 0 until n) {
                acc.add(angle, 0.0)
                angle += rpm * 6.0 / fs
            }
            val counts = acc.bins.map { it.count }
            assertTrue(counts.sum() == n, "$rpm RPM：總數應為 $n，實得 ${counts.sum()}")
            val mean = counts.average()
            assertTrue(abs(mean - expected) < 1.0, "$rpm RPM：每格平均 $mean，期望 $expected")
        }
    }

    @Test
    fun `峰值角度落在注入偏差的那一格`() {
        val acc = PolarAccumulator(binCount = 72)
        for (i in 0 until 3600) {
            val angle = i % 360.0
            acc.add(angle, if (angle in 100.0..104.9) 1.0 else 0.0)
        }
        val peak = acc.peakAngleDegrees!!
        assertTrue(abs(peak - 102.5) < 2.5, "峰值角度 $peak，期望約 102.5°")
    }

    @Test
    fun `負角度與跨圈都要正規化`() {
        val acc = PolarAccumulator(binCount = 4)
        acc.add(-90.0, 1.0)      // 等同 270°
        acc.add(730.0, 2.0)      // 等同 10°
        val bins = acc.bins
        assertTrue(bins[3].count == 1 && abs(bins[3].meanDeviation - 1.0) < 1e-12)
        assertTrue(bins[0].count == 1 && abs(bins[0].meanDeviation - 2.0) < 1e-12)
    }
}
