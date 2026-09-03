package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 對照 Swift 端的 MagneticRevolutionCounterTests，同一組情境同一組數字。 */
class MagneticRevolutionCounterTest {

    /**
     * 合成一組「手機平放在水平盤面上轉動」時，裝置座標系看到的地磁與重力。
     *
     * 房間座標系的地磁是 (H, 0, V)，盤面繞鉛直軸轉 θ，手機相對盤面有固定傾角 α。
     * 裝置座標 = R_x(−α) · R_z(−θ) 作用在房間向量上。
     */
    private fun synthesize(
        theta: Double,
        tilt: Double = 0.0,
        h: Double = 22.0,
        v: Double = 41.0,
        localOffset: Vector3 = Vector3(0.0, 0.0, 0.0),
    ): Pair<Vector3, Vector3> {
        // localOffset 在裝置座標系裡是靜止的（跟著手機一起轉的磁場），直接加上去。
        val field = Vector3(
            h * cos(theta) + localOffset.x,
            cos(tilt) * (-h * sin(theta)) + sin(tilt) * v + localOffset.y,
            sin(tilt) * (h * sin(theta)) + cos(tilt) * v + localOffset.z,
        )
        val gravity = Vector3(0.0, -sin(tilt), -cos(tilt))
        return Pair(field, gravity)
    }

    private fun run(
        revolutions: Double,
        tilt: Double = 0.0,
        samplesPerRevolution: Int = 180,
        reversed: Boolean = false,
        horizontal: Double = 22.0,
        localOffset: Vector3 = Vector3(0.0, 0.0, 0.0),
    ): MagneticRevolutionCounter {
        val counter = MagneticRevolutionCounter()
        val total = (revolutions * samplesPerRevolution).toInt()
        for (i in 0..total) {
            var theta = 2 * PI * i / samplesPerRevolution
            if (reversed) theta = -theta
            val (field, gravity) = synthesize(theta, tilt, horizontal, localOffset = localOffset)
            counter.add(field, gravity)
        }
        return counter
    }

    @Test
    fun `數得出完整圈數`() {
        val counter = run(10.5)
        assertEquals(10, counter.revolutions)
        assertEquals(3780.0, counter.totalDegrees, 1e-6)
    }

    /**
     * 唱盤從上方看是順時針，裝置姿態的角度遞減。這是 CLAUDE.md 坑 4 的同型測試：
     * 凡是會累積角度的東西都要測正反兩個方向。
     */
    @Test
    fun `反向轉動數出來一樣`() {
        val forward = run(10.5)
        val backward = run(10.5, reversed = true)
        assertEquals(forward.revolutions, backward.revolutions)
        assertEquals(forward.totalDegrees, backward.totalDegrees, 1e-6)
    }

    /** 固定傾角必須完全不影響角度 —— 那正是取重力方向當基底的用意。 */
    @Test
    fun `傾角在投影裡被消掉`() {
        for (degrees in listOf(0.0, 1.8, 5.0, 12.0)) {
            val counter = run(10.5, tilt = degrees * PI / 180)
            assertEquals(10, counter.revolutions, "傾角 $degrees°")
            assertEquals(3780.0, counter.totalDegrees, 1e-6, "傾角 $degrees°")
        }
    }

    /**
     * 這條路徑存在的全部理由：它讀不到陀螺儀，所以陀螺儀錯多少都不影響它。
     *
     * 1.01837 是刻意放大的合成比例因子誤差，不是實測值 —— 這支陀螺儀實測
     * k = 0.99915，太接近 1 反而測不出「有沒有真的還原」。融合路徑在這個情境
     * 會回報 1.0（同義反覆），這裡必須回報 1.01837。
     */
    @Test
    fun `還原融合路徑還原不了的比例因子`() {
        val counter = run(10.5)
        val k = counter.calibrationFactor(gyroTotalDegrees = 3780.0 / 1.01837)
        assertTrue(k != null)
        assertEquals(1.01837, k, 1e-5)
    }

    @Test
    fun `不滿一圈回 null`() {
        val counter = run(0.4)
        assertEquals(0, counter.revolutions)
        assertNull(counter.calibrationFactor(144.0))
    }

    /** 重力讀不到就建不出基底，不能當成「轉了 0 度」，要整批略過。 */
    @Test
    fun `重力為零時安全`() {
        val counter = MagneticRevolutionCounter()
        for (i in 0..360) {
            val (field, _) = synthesize(2 * PI * i / 180)
            counter.add(field, Vector3(0.0, 0.0, 0.0))
        }
        assertEquals(0, counter.sampleCount)
        assertEquals(0.0, counter.totalDegrees, 1e-12)
        assertNull(counter.calibrationFactor(3600.0))
    }

    /** 沒有本地磁場時，地磁圓的圓心就在原點，水平分量是定值。 */
    @Test
    fun `乾淨磁場的水平分量是定值`() {
        val counter = run(2.0)
        assertEquals(22.0, counter.horizontalMagnitude, 1e-6)
        assertEquals(22.0, counter.minHorizontal, 1e-6)
        assertEquals(22.0, counter.maxHorizontal, 1e-6)

        val range = counter.horizontalRange!!
        assertEquals(22.0, range.first, 1e-6, "半徑")
        assertEquals(0.0, range.second, 1e-6, "圓心偏移為零")
    }

    /** 偏移小於半徑時圓仍然包住原點，照樣繞得起來，只是水平分量開始擺盪。 */
    @Test
    fun `小偏移仍然繞得起來`() {
        val counter = run(10.5, localOffset = Vector3(8.0, 0.0, 0.0))
        assertEquals(10, counter.revolutions)
        assertEquals(3780.0, counter.totalDegrees, 0.5)

        val range = counter.horizontalRange!!
        assertEquals(22.0, range.first, 0.2, "會繞圈時，大的那個是半徑")
        assertEquals(8.0, range.second, 0.2, "小的那個是圓心偏移")
    }

    /**
     * 真機遇到的情況：本地磁場蓋過地磁，圓心被推到半徑之外，角度只能來回擺盪。
     * 實測是盤面轉了 35 圈（12950°），地磁總轉角卻只累積 576°。
     */
    @Test
    fun `大偏移就繞不起來`() {
        val counter = run(10.5, localOffset = Vector3(60.0, 0.0, 0.0))
        assertEquals(0, counter.revolutions, "圓沒包住原點就繞不起來")
        assertTrue(counter.totalDegrees < 360)

        val range = counter.horizontalRange!!
        assertEquals(60.0, range.first, 0.2, "繞不起來時，大的那個是圓心偏移")
        assertEquals(22.0, range.second, 0.2, "小的那個才是地磁半徑")
    }

    /** 取樣密度不影響總轉角 —— 解捲只要求相鄰樣本之間不超過半圈。 */
    @Test
    fun `取樣密度不影響角度`() {
        for (perRevolution in listOf(12, 60, 180, 400)) {
            val counter = run(10.5, samplesPerRevolution = perRevolution)
            assertEquals(3780.0, counter.totalDegrees, 1e-6, "每圈 $perRevolution 個取樣")
        }
    }

    // ── 扣掉圓心偏移之後的重新解捲 ──────────────────────────────────

    /**
     * 真機遇到的邊緣情況：圓心偏移 20.45 µT 只比半徑 19.75 µT 大一點點，
     * 圓幾乎剛好通過原點，直接解捲繞不起來。擬合圓心減掉之後必須完全救回來。
     *
     * 用 35.5 圈而不是剛好 35 圈：整數圈會落在取整的邊界上，浮點誤差讓結果
     * 在 34/35 之間跳，那是測試的假象而不是程式的問題。
     */
    @Test
    fun `擬合圓心救回邊緣情況`() {
        val counter = run(35.5, horizontal = 19.75, localOffset = Vector3(20.45, 0.0, 0.0))
        assertEquals(0, counter.revolutions, "直接解捲：圓沒包住原點，繞不起來")

        val refined = counter.refined()!!
        assertEquals(35, refined.revolutions, "扣掉圓心之後應該完全數得出來")
        assertEquals(35.5 * 360, refined.totalDegrees, 1.0)
        assertEquals(20.45, refined.centerOffset, 0.01)
        assertEquals(19.75, refined.radius, 0.01)
        assertTrue(refined.isTrustworthy)
    }

    @Test
    fun `擬合處理得了大偏移`() {
        val counter = run(20.5, localOffset = Vector3(120.0, -80.0, 0.0))
        assertEquals(0, counter.revolutions)

        val refined = counter.refined()!!
        assertEquals(20, refined.revolutions)
        assertEquals(hypot(120.0, 80.0), refined.centerOffset, 0.05)
        assertEquals(22.0, refined.radius, 0.05)
    }

    /** 沒有偏移時，擬合不該把好好的訊號弄壞。 */
    @Test
    fun `乾淨資料的擬合與直接解捲一致`() {
        val counter = run(10.5)
        val refined = counter.refined()!!
        assertEquals(counter.revolutions, refined.revolutions)
        assertEquals(counter.totalDegrees, refined.totalDegrees, 0.5)
        assertEquals(0.0, refined.centerOffset, 0.01)
        assertEquals(22.0, refined.radius, 0.01)
    }

    /** 擬合殘差是這個結果可不可信的把關指標。乾淨的圓殘差應該趨近零。 */
    @Test
    fun `乾淨圓的殘差很小`() {
        val refined = run(10.5, localOffset = Vector3(15.0, 0.0, 0.0)).refined()!!
        assertTrue(refined.residual < 0.01)
        assertTrue(refined.isTrustworthy)
    }

    /** 樣本太少擬合不出圓，要回 null 而不是硬給一個數字。 */
    @Test
    fun `樣本太少不擬合`() {
        val counter = MagneticRevolutionCounter()
        for (i in 0 until 10) {
            val (field, gravity) = synthesize(i * 0.05)
            counter.add(field, gravity)
        }
        assertNull(counter.refined())
    }

    /**
     * 真機的坑：強垂直磁場 + 盤面軸不鉛直 = 垂直分量洩漏進水平投影。
     *
     * 自轉軸相對鉛直傾斜（所以重力在裝置座標系裡每圈擺動），垂直磁場放大到
     * 470 µT（實測 iPhone 自帶磁鐵環的量級）。沒有逐樣本扣掉垂直分量的話，
     * 洩漏量會蓋過 22 µT 的水平訊號。
     */
    @Test
    fun `強垂直磁場不會洩漏進水平面`() {
        val counter = MagneticRevolutionCounter()
        val wobble = 1.8 * PI / 180
        val samplesPerRevolution = 180
        val revolutions = 10.5
        val h = 22.0
        val v = 470.0

        for (i in 0..(revolutions * samplesPerRevolution).toInt()) {
            val theta = 2 * PI * i / samplesPerRevolution
            val g = Vector3(sin(wobble) * cos(theta), sin(wobble) * sin(theta), -cos(wobble))
            val field = Vector3(
                h * cos(theta) + v * g.x,
                -h * sin(theta) + v * g.y,
                v * g.z,
            )
            counter.add(field, g)
        }

        assertEquals(10, counter.revolutions, "垂直分量必須被完整扣掉")
        assertEquals(3780.0, counter.totalDegrees, 5.0)
    }
}
