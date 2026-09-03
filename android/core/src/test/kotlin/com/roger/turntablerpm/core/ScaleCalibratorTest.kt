package com.roger.turntablerpm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 規格 §3.7。對照 Swift 端的 ScaleCalibratorTests。 */
class ScaleCalibratorTest {

    @Test
    fun `還原比例因子`() {
        for (epsilon in listOf(0.03, 0.01, -0.015, 0.001)) {
            val signal = SyntheticSignal.make(
                nominalRPM = 100.0 / 3.0, durationSeconds = 120.0,
                scaleError = epsilon, yawNoiseDegrees = 2.0, seed = 3,
            )
            val result = ScaleCalibrator.calibrate(signal.samples)
            assertTrue(result != null, "ε=$epsilon")
            val expected = 1.0 / (1.0 + epsilon)
            assertEquals(expected, result.factor, expected * 0.003, "ε=$epsilon 時應回推 k=$expected")
            assertTrue(result.revolutions > 60)
        }
    }

    @Test
    fun `校準後落在目標精度內`() {
        // 未校準 3% 誤差 → 校準後應落在 0.1% 以內
        val epsilon = 0.03
        val signal = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 120.0,
            scaleError = epsilon, yawNoiseDegrees = 2.0, seed = 11,
        )
        val raw = SpeedStatistics.meanRPM(signal.samples)!!
        assertEquals((100.0 / 3.0) * (1 + epsilon), raw, 0.01)

        val k = ScaleCalibrator.calibrate(signal.samples)!!.factor
        assertEquals(100.0 / 3.0, raw * k, (100.0 / 3.0) * 0.001)
    }

    @Test
    fun `所需圈數符合規格表`() {
        // 目標精度 0.05%：乾淨環境 2° → 12 圈；一般客廳 5° → 28 圈；干擾大 10° → 56 圈
        assertEquals(12, ScaleCalibrator.requiredRevolutions(2.0, 0.0005))
        assertEquals(28, ScaleCalibrator.requiredRevolutions(5.0, 0.0005))
        assertEquals(56, ScaleCalibrator.requiredRevolutions(10.0, 0.0005))
    }

    @Test
    fun `碼錶備援`() {
        // 100 圈的 33⅓ 轉需要 180 秒
        val k = ScaleCalibrator.manualFactor(100, 180.0, 33.5)
        assertTrue(k != null)
        assertEquals((100.0 / 3.0) / 33.5, k, 1e-9)

        // 人為計時誤差 ±0.3 s：100 圈 → 0.17%，200 圈 → 0.08%
        assertEquals(0.001667, ScaleCalibrator.manualPrecision(100, 100.0 / 3.0, 0.3), 1e-5)
        assertEquals(0.000833, ScaleCalibrator.manualPrecision(200, 100.0 / 3.0, 0.3), 1e-5)
    }

    @Test
    fun `沒有磁力計就回 null`() {
        val samples = (0 until 500).map { SpinSample(it / 100.0, 200.0, null) }
        assertNull(ScaleCalibrator.calibrate(samples))
    }

    @Test
    fun `不滿一圈回 null`() {
        val signal = SyntheticSignal.make(nominalRPM = 100.0 / 3.0, durationSeconds = 1.0)
        assertNull(ScaleCalibrator.calibrate(signal.samples))
    }
}

/** 對照 Swift 端的 CalibrationConfidenceTests。 */
class CalibrationConfidenceTest {

    /**
     * 真機實測的回歸測試：這組數字曾經讓 UI 說出「這個倍率可以參考了」，
     * 而那個倍率（0.99994）當時被認為跟真值差 1.8%。
     */
    @Test
    fun `真實量測被判為同義反覆`() {
        val c = ScaleCalibrator.confidence(
            gyroTotalDegrees = 12987.0, magneticTotalDegrees = 12986.0, revolutions = 36,
        )
        assertTrue(c is CalibrationConfidence.Indistinguishable, "兩條路徑只差 1°，得到 $c")
        assertEquals(1.0, c.divergenceDegrees, 1e-9)
        assertEquals(15.0, c.noiseFloorDegrees, 1e-9)
        assertTrue(!c.isUsable)
    }

    /** 舊判準的回歸測試：圈數再多，只要兩條路徑沒有分歧就不能採信。 */
    @Test
    fun `圈數多不代表可信`() {
        for (revolutions in listOf(30, 100, 1000)) {
            val c = ScaleCalibrator.confidence(
                gyroTotalDegrees = 360.0 * revolutions,
                magneticTotalDegrees = 360.0 * revolutions,
                revolutions = revolutions,
            )
            assertTrue(!c.isUsable, "$revolutions 圈但零分歧，不該可用")
        }
    }

    /**
     * 分歧量超過雜訊底線時才算數。這裡用「陀螺儀真的低估 1.804%」的情境：
     * 真實轉角 13225°，陀螺儀只積到 12987°，差 238° 遠高於 15° 的底線。
     */
    @Test
    fun `真的有分歧就可用`() {
        val c = ScaleCalibrator.confidence(
            gyroTotalDegrees = 12987.0, magneticTotalDegrees = 13225.0, revolutions = 36,
        )
        assertTrue(c is CalibrationConfidence.Usable, "238° 的分歧遠高於底線，得到 $c")
        assertEquals(5.0 / 13225.0, c.precision, 1e-12)
    }

    @Test
    fun `不滿一圈是資料不足`() {
        assertEquals(
            CalibrationConfidence.Insufficient,
            ScaleCalibrator.confidence(200.0, 240.0, 0),
        )
    }

    @Test
    fun `退化輸入被擋掉`() {
        assertEquals(CalibrationConfidence.Insufficient, ScaleCalibrator.confidence(0.0, 3600.0, 10))
        assertEquals(CalibrationConfidence.Insufficient, ScaleCalibrator.confidence(3600.0, 0.0, 10))
    }

    /** 雜訊底線是可調的：安靜環境雜訊小，同樣的分歧量就變得可信。 */
    @Test
    fun `雜訊底線可調`() {
        val borderline = ScaleCalibrator.confidence(3600.0, 3620.0, 10, yawNoiseDegrees = 5.0)
        assertTrue(borderline.isUsable, "20° 分歧 > 15° 底線")

        val noisy = ScaleCalibrator.confidence(3600.0, 3620.0, 10, yawNoiseDegrees = 10.0)
        assertTrue(!noisy.isUsable, "雜訊 10° 時底線是 30°，20° 的分歧不夠")
    }
}
