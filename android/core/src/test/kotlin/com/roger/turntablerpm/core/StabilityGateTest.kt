package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StabilityGateTest {

    /** 造一段量測：前面接一段加速（或任何前綴），後面接一段減速。 */
    private fun run(
        leadInSeconds: Double = 0.0,
        steadySeconds: Double = 60.0,
        tailSeconds: Double = 0.0,
        rpm: Double = 100.0 / 3.0,
        fs: Double = 100.0,
        leadInFrom: Double = 0.0,
        tailTo: Double = 0.0,
    ): MutableList<SpinSample> {
        val steady = rpm * 6.0
        val out = ArrayList<SpinSample>()
        var t = 0.0
        for (i in 0 until (leadInSeconds * fs).toInt()) {
            val frac = i / maxOf(1.0, leadInSeconds * fs)
            out += SpinSample(t, leadInFrom + (steady - leadInFrom) * frac)
            t += 1 / fs
        }
        for (i in 0 until (steadySeconds * fs).toInt()) {
            val wow = steady * 0.005 * sin(2 * PI * 0.55 * i / fs)   // 正常抖晃 ±0.5%
            out += SpinSample(t, steady + wow)
            t += 1 / fs
        }
        for (i in 0 until (tailSeconds * fs).toInt()) {
            val frac = i / maxOf(1.0, tailSeconds * fs)
            out += SpinSample(t, steady + (tailTo - steady) * frac)
            t += 1 / fs
        }
        return out
    }

    /** 乾淨的量測不該被動到。使用者照正確順序操作時，程式不能自作聰明砍資料。 */
    @Test
    fun `乾淨的量測原封不動`() {
        assertTrue(StabilityGate.find(run(steadySeconds = 60.0))!!.isPristine)
    }

    @Test
    fun `切掉開頭的加速段`() {
        val s = run(leadInSeconds = 6.0, steadySeconds = 60.0)
        val w = StabilityGate.find(s)!!
        assertTrue(w.droppedAtStart > 500, "6 秒的加速段幾乎要全丟，實得 ${w.droppedAtStart}")
        assertTrue(w.droppedAtEnd == 0 && w.droppedInMiddle == 0)
        // 保留區間的平均要回到標稱值，不被加速段拉低
        val kept = s.subList(w.startIndex, w.endIndex)
        assertTrue(abs(SpeedStatistics.meanRPM(kept)!! - 100.0 / 3.0) < 0.05)
    }

    @Test
    fun `切掉尾端的減速段`() {
        val w = StabilityGate.find(run(steadySeconds = 60.0, tailSeconds = 6.0))!!
        assertTrue(w.droppedAtEnd > 500, "實得 ${w.droppedAtEnd}")
        assertTrue(w.droppedAtStart == 0)
    }

    /** 單一根毛刺不該把資料切成兩半，然後丟掉其中一半。 */
    @Test
    fun `單根毛刺不切斷區間`() {
        val s = run(steadySeconds = 60.0)
        s[3000] = SpinSample(s[3000].t, s[3000].omega * 3)
        val w = StabilityGate.find(s)!!
        assertTrue(w.count > s.size - 100, "毛刺只該被忽略，實際保留 ${w.count} / ${s.size}")
    }

    @Test
    fun `正常的抖晃不會被切`() {
        val fs = 100.0
        val steady = (100.0 / 3.0) * 6.0
        val s = (0 until 6000).map {
            SpinSample(it / fs, steady + steady * 0.015 * sin(2 * PI * 0.53 * it / fs))
        }
        assertTrue(StabilityGate.find(s)!!.isPristine)
    }

    /**
     * 回歸測試（CLAUDE.md 坑 18）：一段純加速的 MAD 本來就很大，
     * 沒有 maximumTolerance 上限的話門檻會被撐開，**加速段自己把自己判成正常**。
     */
    @Test
    fun `純加速不能自己判成正常`() {
        val s = (0 until 3000).map { SpinSample(it / 100.0, it * 0.1) }
        assertNull(StabilityGate.find(s), "整段都在加速，應該承認量測失敗而不是回一個區間")
    }

    @Test
    fun `穩定段太短就失敗`() {
        assertNull(StabilityGate.find(run(leadInSeconds = 10.0, steadySeconds = 2.0), minimumSeconds = 5.0))
    }

    @Test
    fun `樣本太少回 null`() {
        assertNull(StabilityGate.find(emptyList()))
        assertNull(StabilityGate.find(listOf(SpinSample(0.0, 200.0))))
    }

    /** 中途被碰到：保留最長的一段，並如實回報中間丟了多少。 */
    @Test
    fun `回報中途的干擾`() {
        val s = run(steadySeconds = 60.0)
        for (i in 2000 until 2200) s[i] = SpinSample(s[i].t, s[i].omega * 0.5)
        val w = StabilityGate.find(s)!!
        assertTrue(w.droppedInMiddle > 0 || w.droppedAtStart > 0, "中途的干擾要反映出來")
        assertTrue(w.count > 3000, "應該保留較長的那一段，實得 ${w.count}")
    }
}
