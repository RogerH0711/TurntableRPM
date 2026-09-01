package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplingStatsTest {

    @Test
    fun `完美等間隔的抖動是零`() {
        val t = DoubleArray(1000) { it / 100.0 }
        val s = SamplingStats.from(t)!!
        assertTrue(abs(s.effectiveRateHz - 100.0) < 1e-9, "實得 ${s.effectiveRateHz} Hz")
        assertTrue(abs(s.medianIntervalMs - 10.0) < 1e-9)
        assertTrue(s.stdDevIntervalMs < 1e-9)
        assertTrue(s.jitterRatio < 1e-9)
        assertEquals(0, s.longGaps)
        assertTrue(abs(s.worstGapRatio - 1.0) < 1e-9)
    }

    /** iOS 的實測基準：中位數 9.990 ms、標準差 0.005 ms → 抖動比 0.05%。 */
    @Test
    fun `重現 iOS 的抖動水準`() {
        val rng = SplitMix64(11)
        var t = 0.0
        val times = DoubleArray(2000) {
            val v = t
            t += 0.009990 + rng.nextGaussian() * 0.000005
            v
        }
        val s = SamplingStats.from(times)!!
        assertTrue(abs(s.medianIntervalMs - 9.990) < 0.01, "中位數 ${s.medianIntervalMs} ms")
        assertTrue(s.jitterRatio < 0.002, "抖動比 ${s.jitterRatio}")
    }

    /** 掉樣本會讓某些間隔變成兩倍，要被算成 long gap。 */
    @Test
    fun `掉樣本會被認出來`() {
        val times = ArrayList<Double>()
        for (i in 0 until 500) {
            if (i % 100 == 50) continue        // 每 100 筆掉一筆
            times += i / 100.0
        }
        val s = SamplingStats.from(times.toDoubleArray())!!
        assertTrue(s.longGaps >= 4, "應該認出約 5 個空隙，實得 ${s.longGaps}")
        assertTrue(abs(s.worstGapRatio - 2.0) < 0.01, "最糟的空隙應是兩倍，實得 ${s.worstGapRatio}")
    }

    @Test
    fun `樣本太少回 null`() {
        assertNull(SamplingStats.from(doubleArrayOf(0.0, 0.01)))
        assertNull(SamplingStats.from(DoubleArray(0)))
    }

    /**
     * 時間戳基準的廠商差異：這個 app 只用差值所以不影響計算，
     * 但要能認出來並記錄 —— 那是多台裝置對照時的必要資訊。
     */
    @Test
    fun `辨識時間戳基準`() {
        val elapsed = 123_456_789_000_000L          // 開機以來約 34 小時
        val epochMs = 1_756_000_000_000L
        val (base1, err1) = SamplingStats.identifyTimestampBase(elapsed + 500, elapsed, epochMs)
        assertEquals("elapsedRealtime", base1)
        assertTrue(err1 < 1e-3)

        val (base2, err2) = SamplingStats.identifyTimestampBase(epochMs * 1_000_000L, elapsed, epochMs)
        assertEquals("epoch", base2)
        assertTrue(err2 < 1e-3)
    }
}
