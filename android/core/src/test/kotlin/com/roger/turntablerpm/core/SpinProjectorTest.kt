package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SpinProjectorTest {

    private val nominalRPM = 100.0 / 3.0        // 33⅓
    private val trueOmega = nominalRPM * 6.0    // 200 °/s

    /** 規格 §2.2：手機傾斜多少都不該影響讀數。這是投影法存在的唯一理由。 */
    @Test
    fun `重力投影不受傾斜影響`() {
        for (tilt in listOf(0.0, 5.0, 15.0, 30.0)) {
            val run = SyntheticSignal.make(nominalRPM, durationSeconds = 5.0, tiltDegrees = tilt)
            val mean = run.samples.map { it.omega }.average()
            assertTrue(
                abs(mean - trueOmega) < 1e-9,
                "傾斜 $tilt° 時投影應為 $trueOmega °/s，實得 $mean",
            )
        }
    }

    /** 只讀 z 軸的錯誤做法，低估量要對得上 Python 參考實作。 */
    @Test
    fun `只讀 z 軸的低估量符合黃金值`() {
        for (tilt in listOf(0, 5, 15, 30)) {
            val run = SyntheticSignal.make(nominalRPM, durationSeconds = 5.0, tiltDegrees = tilt.toDouble())
            val naive = run.rotationRates.map { SpinProjector.projectNaiveZ(it) }.average()
            val errorPercent = (naive / trueOmega - 1.0) * 100.0
            val expected = Golden.number("projection_naive_z_error_pct", tilt.toString())
            assertTrue(
                abs(errorPercent - expected) < 1e-3,
                "傾斜 $tilt°：黃金值 $expected%，實得 $errorPercent%",
            )
        }
    }
}
