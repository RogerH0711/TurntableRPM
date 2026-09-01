package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 碼錶是唯一可信的校準路徑（iOS 端兩條自動校準都失敗了，見 CLAUDE.md 坑 11、15）。
 */
class StopwatchCalibrationTest {

    /**
     * 陀螺儀有 ε 的比例因子誤差時，App 會量到 rpm×(1+ε)，
     * 碼錶給出真值，所以 k 應該精準回推 1/(1+ε)。黃金值直接讀 golden.json。
     */
    @Test
    fun `k 精準回推比例因子`() {
        val trueRPM = 100.0 / 3.0
        for (case in Golden.array("calibration")) {
            val epsilon = Golden.number(case, "scale_err")
            val expectedK = Golden.number(case, "expected_k")
            val measured = trueRPM * (1.0 + epsilon)
            // 100 圈的真實秒數
            val seconds = 60.0 * 100 / trueRPM
            val c = StopwatchCalibration.create(
                revolutions = 100, seconds = seconds,
                measuredRPM = measured, deviceModel = "G8142",
            )!!
            assertTrue(
                abs(c.factor - expectedK) < 1e-5,
                "ε=$epsilon：黃金值 $expectedK，實得 ${c.factor}",
            )
            assertTrue(abs(c.apply(measured) - trueRPM) < 1e-9)
        }
    }

    /** 100 圈 33⅓ 轉是 180 秒，±0.3 秒 → 0.17%；200 圈 → 0.08%。 */
    @Test
    fun `精度隨圈數改善`() {
        val hundred = StopwatchCalibration.create(100, 180.0, 33.33, "G8142")!!
        val twoHundred = StopwatchCalibration.create(200, 360.0, 33.33, "G8142")!!
        assertTrue(abs(hundred.precision() - 0.3 / 180.0) < 1e-12)
        assertTrue(abs(twoHundred.precision() - 0.3 / 360.0) < 1e-12)
        assertTrue(twoHundred.precision() < hundred.precision() / 1.9)
    }

    /** 把 100 圈打成 10 圈會得到 k≈0.1，存下去之後每一次讀數都錯，所以要擋。 */
    @Test
    fun `離譜的 k 要被擋下來`() {
        val typo = StopwatchCalibration.create(10, 180.0, 33.33, "G8142")!!
        assertTrue(!typo.isPlausible, "k=${typo.factor} 應該被判為不合理")
        val sane = StopwatchCalibration.create(100, 180.0, 33.33, "G8142")!!
        assertTrue(sane.isPlausible)
    }

    @Test
    fun `無效輸入回 null`() {
        assertNull(StopwatchCalibration.create(0, 180.0, 33.33, "G8142"))
        assertNull(StopwatchCalibration.create(100, 0.0, 33.33, "G8142"))
        assertNull(StopwatchCalibration.create(100, 180.0, 0.0, "G8142"))
    }
}
