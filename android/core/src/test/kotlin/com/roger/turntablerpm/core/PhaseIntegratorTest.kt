package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 規格 §3.6：熱圖的相位不能只靠陀螺儀積分。
 *
 * 這組測試不只看最後一筆的誤差，而是追蹤整段的誤差軌跡；而且**每個案例都跑正反兩個轉向**。
 * 真實唱盤從上方看是順時針轉，裝置姿態的 yaw 會遞減 —— 早期版本的合成訊號只產生遞增的 yaw，
 * 讓一個轉向的 bug 溜過了整組測試，直到放上真的唱盤才炸掉。
 */
class PhaseIntegratorTest {

    private data class Trace(
        val maxError: Double,
        val finalError: Double,
        val maxErrorFirstThird: Double,
        val maxErrorLastThird: Double,
        val revolutions: Int,
        val calibrationEstimate: Double?,
        val magneticTotalDegrees: Double,
        val gyroTotalDegrees: Double,
    )

    private fun circularError(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180) d -= 360.0
        if (d < -180) d += 360.0
        return abs(d)
    }

    private fun trace(
        rpm: Double,
        duration: Double,
        scaleError: Double,
        useMagnetometer: Boolean,
        reversedYaw: Boolean = false,
    ): Trace {
        val signal = SyntheticSignal.make(
            nominalRPM = rpm, durationSeconds = duration,
            scaleError = scaleError, reversedYaw = reversedYaw, seed = 4,
        )
        val integrator = PhaseIntegrator()
        val errors = ArrayList<Double>(signal.samples.size)

        for (i in signal.samples.indices) {
            val sample = signal.samples[i]
            integrator.add(
                if (useMagnetometer) sample
                else SpinSample(t = sample.t, omega = sample.omega, yaw = null),
            )
            var truePhase = signal.trueAngleDegrees[i] % 360.0
            if (truePhase < 0) truePhase += 360.0
            errors += circularError(integrator.phaseDegrees, truePhase)
        }

        val third = errors.size / 3
        return Trace(
            maxError = errors.max(),
            finalError = errors.last(),
            maxErrorFirstThird = errors.subList(0, third).max(),
            maxErrorLastThird = errors.subList(errors.size - third, errors.size).max(),
            revolutions = integrator.revolutions,
            calibrationEstimate = integrator.calibrationEstimate,
            magneticTotalDegrees = integrator.magneticTotalDegrees,
            gyroTotalDegrees = integrator.gyroTotalDegrees,
        )
    }

    /**
     * 誤差的理論上界：一個取樣間隔（偵測必然落在跨圈後的第一個取樣）加上圈內的比例因子漂移。
     * 乘 1.2 是浮點餘裕 —— 45 轉的超調量剛好逼近整整一個取樣，卡在等號上會讓測試變得脆弱。
     */
    private fun errorBound(rpm: Double, scaleError: Double, sampleRate: Double = 100.0): Double =
        (rpm * 6.0 / sampleRate + abs(scaleError) * 360.0) * 1.2

    private val cases = listOf(
        Triple(50.0 / 3.0, 90.0, 0.02),
        Triple(100.0 / 3.0, 60.0, 0.01),
        Triple(45.0, 60.0, 0.005),
        Triple(78.0, 60.0, 0.005),
    )

    /**
     * 回歸測試：真實唱盤的 yaw 是遞減的。
     *
     * 舊版無條件把錨點 += 2π，遇到遞減的 yaw 時錨點會朝反方向跑掉，
     * 之後每一個取樣都滿足「走了超過一圈」——圈數暴增到接近取樣數，相位恆為 0。
     * 真機上量到的就是這個：4921 個樣本、4730 圈、相位 0.0°。
     */
    @Test
    fun `反向旋轉的圈數要正確`() {
        val r = trace(100.0 / 3.0, 60.0, 0.0, useMagnetometer = true, reversedYaw = true)
        assertEquals(33, r.revolutions, "60 秒 33⅓ 轉應該是 33 圈，不是幾千圈")
        assertTrue(r.maxError < errorBound(100.0 / 3.0, 0.0))
        assertTrue(r.maxErrorLastThird < r.maxErrorFirstThird * 2.0 + 1.0)
    }

    /** 正反轉向必須得到完全一致的結果。 */
    @Test
    fun `兩個轉向的結果一致`() {
        for ((rpm, duration, scaleError) in cases) {
            val forward = trace(rpm, duration, scaleError, true, reversedYaw = false)
            val reverse = trace(rpm, duration, scaleError, true, reversedYaw = true)
            assertEquals(forward.revolutions, reverse.revolutions, "$rpm RPM：圈數不一致")
            assertTrue(
                abs(forward.maxError - reverse.maxError) < 0.01,
                "$rpm RPM：轉向不該影響誤差（${forward.maxError} vs ${reverse.maxError}）",
            )
            assertTrue(reverse.maxError < errorBound(rpm, scaleError))
            assertTrue(reverse.maxErrorLastThird < reverse.maxErrorFirstThird * 2.0 + 1.0)
        }
    }

    @Test
    fun `磁北錨定阻止相位漂移`() {
        for (reversed in listOf(false, true)) {
            val anchored = trace(100.0 / 3.0, 60.0, 0.01, true, reversedYaw = reversed)
            assertTrue(
                anchored.maxError < errorBound(100.0 / 3.0, 0.01),
                "錨定後的誤差應落在「一個取樣 + ε×360°」之內，實得 ${anchored.maxError}°",
            )
            assertEquals(33, anchored.revolutions)
        }
        val freeRunning = trace(100.0 / 3.0, 60.0, 0.01, useMagnetometer = false)
        assertTrue(
            freeRunning.finalError > 100.0,
            "純陀螺儀積分 60 秒應該漂移約 120° —— 這正是需要錨定的理由，實得 ${freeRunning.finalError}°",
        )
    }

    @Test
    fun `陀螺儀完美時不漂移`() {
        val r = trace(45.0, 30.0, 0.0, useMagnetometer = true)
        assertTrue(r.maxError < errorBound(45.0, 0.0))
        assertEquals(22, r.revolutions)
    }

    /**
     * 回歸測試：錨點若設成偵測到的實際位置而不是推進整整一圈，
     * 不足一個取樣的超調量會逐圈累積。45 轉 60 秒 44 圈會累積到 79°。
     */
    @Test
    fun `錨點超調量不會累積`() {
        val r = trace(45.0, 60.0, 0.0, useMagnetometer = true)
        assertTrue(r.maxError < errorBound(45.0, 0.0))
        assertTrue(
            r.maxErrorLastThird < r.maxErrorFirstThird * 2.0 + 1.0,
            "誤差不該隨時間成長：前 1/3 ${r.maxErrorFirstThird}°，後 1/3 ${r.maxErrorLastThird}°",
        )
        assertEquals(44, r.revolutions)
    }

    @Test
    fun `誤差上界在所有轉速都成立`() {
        for ((rpm, duration, scaleError) in cases) {
            val r = trace(rpm, duration, scaleError, useMagnetometer = true)
            val bound = errorBound(rpm, scaleError)
            assertTrue(r.maxError < bound, "$rpm RPM：最大誤差 ${r.maxError}°，上界 $bound°")
            assertTrue(
                r.maxErrorLastThird < r.maxErrorFirstThird * 2.0 + 1.0,
                "$rpm RPM：誤差隨時間成長了",
            )
        }
    }

    /**
     * 校準倍率的即時估計：合成訊號裡磁北是完美的，所以 k 應該精準回推 1/(1+ε)。
     * 真機上要跑滿數十圈磁力計才會主導，這裡只驗算式本身。
     */
    @Test
    fun `校準估計能回推比例因子`() {
        for (reversed in listOf(false, true)) {
            for (epsilon in listOf(0.03, 0.01, -0.015)) {
                val r = trace(100.0 / 3.0, 120.0, epsilon, true, reversedYaw = reversed)
                val k = r.calibrationEstimate
                assertTrue(k != null, "ε=$epsilon reversed=$reversed：估計不該是 null")
                val expected = 1.0 / (1.0 + epsilon)
                assertTrue(
                    abs(k - expected) <= expected * 0.002,
                    "ε=$epsilon reversed=$reversed：期望 $expected，實得 $k",
                )
            }
        }
    }

    @Test
    fun `沒有磁力計時估計為 null`() {
        val r = trace(100.0 / 3.0, 60.0, 0.0, useMagnetometer = false)
        assertNull(r.calibrationEstimate)
        assertTrue(abs(r.magneticTotalDegrees) < 1e-9)
        assertTrue(r.gyroTotalDegrees > 10_000)
    }
}
