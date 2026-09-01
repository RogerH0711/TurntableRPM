package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedStatisticsTest {

    /** ±8% 的辨識窗，邊界直接讀 golden.json。 */
    @Test
    fun `標稱辨識的邊界符合黃金值`() {
        val cases = listOf(
            Triple("33.333_lo", TurntableSpeed.RPM33, +1.0),   // 邊界內側往上一點點
            Triple("33.333_hi", TurntableSpeed.RPM33, -1.0),
            Triple("45_lo", TurntableSpeed.RPM45, +1.0),
            Triple("78_lo", TurntableSpeed.RPM78, +1.0),
        )
        for ((key, expected, inward) in cases) {
            val boundary = Golden.number("classify_boundaries", key)
            // 邊界內側必須認得出來
            val inside = SpeedStatistics.classify(boundary + inward * 0.01)
            assertEquals(expected, inside, "$key = $boundary 的內側應辨識為 ${expected.label}")
        }
        // 33⅓ 下邊界再往外就不該認了（45 的窗也構不著）
        val lo = Golden.number("classify_boundaries", "33.333_lo")
        assertNull(SpeedStatistics.classify(lo - 0.5), "$lo 以下不該辨識成任何標稱值")
    }

    @Test
    fun `梯形平均容忍不等間隔`() {
        // 刻意做出抖動的時間戳，平均值仍應是 200 °/s
        val rng = SplitMix64(3)
        val samples = ArrayList<SpinSample>()
        var t = 0.0
        while (t < 30.0) {
            samples += SpinSample(t = t, omega = 200.0)
            t += 0.01 * (0.5 + 1.0 * rng.nextUniform())
        }
        val mean = SpeedStatistics.meanOmega(samples)!!
        assertTrue(abs(mean - 200.0) < 1e-9, "實得 $mean")
        assertTrue(abs(SpeedStatistics.meanRPM(samples)!! - 100.0 / 3.0) < 1e-9)
    }

    @Test
    fun `偏差百分比`() {
        val e = SpeedStatistics.errorPercent(32.0232, TurntableSpeed.RPM33)
        assertTrue(abs(e - (-3.9303)) < 1e-3, "實得 $e")
    }

    @Test
    fun `穩定閘門擋掉加速段`() {
        val steady = (0 until 1000).map { SpinSample(it / 100.0, 200.0) }
        assertTrue(SpeedStatistics.isStable(steady))
        val ramp = (0 until 1000).map { SpinSample(it / 100.0, it * 0.2) }
        assertTrue(!SpeedStatistics.isStable(ramp))
    }
}
