package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementAnalysisTest {

    /** 33⅓ 轉 = 0.5556 Hz。每圈一次的 wow 就是這個頻率。 */
    private val rot33 = (100.0 / 3.0) / 60.0

    // MARK: - 譜峰判讀

    /**
     * 每圈一次的偏心必須被認出來，而且振幅要對得上。
     *
     * 這是真機上最重要的一項：實測 TD 235 EV 置中量測的 1× 成分是 0.2724%，
     * 佔了譜峰功率的 86.7%。
     */
    @Test
    fun `認出每圈一次的偏心`() {
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(WowComponent(0.40, rot33)),
        )
        val a = MeasurementAnalysis.analyze(run.samples)
        assertTrue(a != null, "分析不該回 null")
        val top = a.peaks.firstOrNull()
        assertTrue(top != null, "應該找得到譜峰")
        assertTrue(abs(top.frequencyHz - rot33) < 0.02, "頻率 ${top.frequencyHz}")
        assertTrue(abs(top.orderOfRotation - 1.0) < 0.04, "倍數 ${top.orderOfRotation}")
        assertTrue(top.isRotationHarmonic)
        assertTrue(abs(top.amplitudePercent - 0.40) < 0.04, "振幅 ${top.amplitudePercent}")
        assertTrue(abs(a.onePerRevolutionPercent - 0.40) < 0.04)
        assertEquals(SpectralPeak.Kind.Eccentricity, top.kind)
    }

    /**
     * 非整數倍的峰要被判成傳動鏈零件，不能誤認成轉盤諧波。
     *
     * 實測 TD 235 EV 在 18.85 Hz 有一根峰，是轉盤基頻的 35.29 倍 —— 不是整數，
     * 所以它來自馬達而不是盤面。這個判別是頻譜真正的診斷價值所在。
     */
    @Test
    fun `把傳動鏈的峰跟諧波分開`() {
        val driveHz = rot33 * 35.32          // 刻意非整數倍
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(WowComponent(0.30, rot33), WowComponent(0.20, driveHz)),
        )
        val a = MeasurementAnalysis.analyze(run.samples)!!
        val drive = a.peaks.firstOrNull { abs(it.frequencyHz - driveHz) < 0.05 }
        assertTrue(drive != null, "傳動鏈那根峰要找得到")
        assertTrue(!drive.isRotationHarmonic)
        assertTrue(abs(drive.orderOfRotation - 35.32) < 0.1, "倍數 ${drive.orderOfRotation}")
        assertEquals(SpectralPeak.Kind.DriveChain, drive.kind)

        val one = a.peaks.firstOrNull { it.isRotationHarmonic }
        assertTrue(one != null)
        assertTrue(abs(one.orderOfRotation - 1.0) < 0.04)
    }

    /** 2× 是盤面橢圓，判讀要跟 1× 不同。 */
    @Test
    fun `二倍諧波有自己的判讀`() {
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(WowComponent(0.35, rot33 * 2)),
        )
        val top = MeasurementAnalysis.analyze(run.samples)!!.peaks.first()
        assertTrue(abs(top.orderOfRotation - 2.0) < 0.04)
        assertTrue(top.isRotationHarmonic)
        assertEquals(SpectralPeak.Kind.Ovality, top.kind)
    }

    // MARK: - 極座標

    @Test
    fun `極座標分箱定位偏心`() {
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(WowComponent(0.50, rot33)),
        )
        val a = MeasurementAnalysis.analyze(run.samples)!!
        assertTrue(a.peakAngleDegrees != null)
        val peak = a.polarBins.maxOf { abs(it.meanDeviation) }
        assertTrue(peak > 0.3, "偏心應該在某個角度累積出可見的偏差，實得 $peak")

        // 每格樣本數的保證是「每圈最多差一個樣本」，不是「幾乎相等」 ——
        // 33⅓ 轉、100 Hz、72 格時每格每圈只拿到 2.5 個樣本，只能是 2 或 3。
        // 真正該擋的是「有格子完全沒被走到」或「差距遠超過圈數」。
        val counts = a.polarBins.map { it.count }
        val revolutions = (a.durationSeconds * a.rotationHz).roundToInt()
        assertTrue(counts.min() > 0, "不能有格子完全沒被走到")
        assertTrue(
            counts.max() - counts.min() <= revolutions + 2,
            "每格每圈最多差一個樣本：全距 ${counts.max() - counts.min()}，圈數 $revolutions",
        )
    }

    /** 沒有 wow 的乾淨訊號不該生出假峰。 */
    @Test
    fun `乾淨訊號不生假峰`() {
        val run = SyntheticSignal.make(nominalRPM = 100.0 / 3.0, durationSeconds = 60.0)
        val a = MeasurementAnalysis.analyze(run.samples)!!
        assertTrue(a.wowFlutter.wrmsPercent < 0.01, "實得 ${a.wowFlutter.wrmsPercent}")
        assertTrue(abs(a.onePerRevolutionPercent) < 0.02)
    }

    // MARK: - 基本量

    @Test
    fun `回報轉速與時長`() {
        val a = MeasurementAnalysis.analyze(
            SyntheticSignal.make(nominalRPM = 45.0, durationSeconds = 30.0).samples,
        )!!
        assertTrue(abs(a.meanRPM - 45.0) < 0.01, "轉速 ${a.meanRPM}")
        assertTrue(abs(a.rotationHz - 45.0 / 60.0) < 0.001)
        assertTrue(abs(a.durationSeconds - 30.0) < 0.2, "時長 ${a.durationSeconds}")
    }

    /** 樣本太少要回 null，不能硬給一個沒有意義的分析。 */
    @Test
    fun `樣本太少回 null`() {
        val run = SyntheticSignal.make(nominalRPM = 100.0 / 3.0, durationSeconds = 0.3)
        assertNull(MeasurementAnalysis.analyze(run.samples))
    }

    // MARK: - 主導成分

    @Test
    fun `單頻時主導佔比高`() {
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(WowComponent(0.40, rot33)),
        )
        val share = MeasurementAnalysis.analyze(run.samples)!!.dominantPeakShare
        assertTrue(share > 0.9, "實得 $share")
    }

    @Test
    fun `成分分散時主導佔比低`() {
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(
                WowComponent(0.20, rot33),
                WowComponent(0.20, rot33 * 2),
                WowComponent(0.20, rot33 * 5),
                WowComponent(0.20, 4.0),
            ),
        )
        val share = MeasurementAnalysis.analyze(run.samples)!!.dominantPeakShare
        assertTrue(share < 0.45, "實得 $share")
    }

    /**
     * 這個指標比峰值/RMS 穩定：實測同一台唱盤兩次得到 1.67 與 1.95，
     * 譜峰內容卻一樣。換成功率佔比之後，同樣的頻譜要給出同樣的結論。
     */
    @Test
    fun `同樣的頻譜內容給出一致的佔比`() {
        val shares = (1L..4L).map { seed ->
            val run = SyntheticSignal.make(
                nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
                wow = listOf(
                    WowComponent(0.42, rot33),
                    WowComponent(0.047, rot33 * 35.32),
                ),
                noisePercent = 0.03, seed = seed,
            )
            MeasurementAnalysis.analyze(run.samples)!!.dominantPeakShare
        }
        assertTrue(
            shares.max() - shares.min() < 0.05,
            "同樣的頻譜內容應該得到一致的佔比，實際 $shares",
        )
        assertTrue(shares.min() > 0.6, "0.42% 對 0.047% 顯然是單頻主導，實際 $shares")
    }

    /**
     * 回歸測試：靜止不動的手機不能被當成一次量測。
     *
     * MEMS 陀螺儀靜止時輸出的是幾乎固定的偏置而不是雜訊，所以那段資料**非常穩**，
     * 穩定閘門會整段放行；接著除以趨近零的平均值，偏差百分比就爆掉。
     * 實測 XZ Premium 靜置 15 秒得到「抖晃率 24.593% WRMS」。
     */
    @Test
    fun `靜止不動不是一次量測`() {
        val rng = SplitMix64(5)
        val bias = 0.036                       // °/s，實測 0.006 RPM 的偏置
        val samples = (0 until 1600).map {
            SpinSample(t = it / 107.9, omega = bias + rng.nextGaussian() * 0.002)
        }
        // 閘門本身會說「這段很穩」—— 因為它確實很穩
        assertTrue(StabilityGate.find(samples) != null, "偏置很穩定，閘門本來就會放行")
        // 但那不是一次量測
        assertNull(
            MeasurementAnalysis.analyze(samples, sampleRate = 107.9),
            "靜止的手機不該產生分析結果",
        )
    }

    /** 下限要遠低於最慢的標稱轉速，不能誤擋真實唱盤。 */
    @Test
    fun `最慢的標稱轉速不會被下限擋掉`() {
        val run = SyntheticSignal.make(nominalRPM = 50.0 / 3.0, durationSeconds = 60.0)
        assertTrue(MeasurementAnalysis.analyze(run.samples) != null, "16⅔ 轉不該被擋")
        assertTrue(MeasurementAnalysis.MINIMUM_RPM < 50.0 / 3.0 / 3)
    }

    /**
     * 穩定閘門要真的把開頭的加速切掉 —— 沒有這一步，低頻端的巨大能量會把
     * 每圈一次的偏心峰淹掉，「問題出在哪」那一區就會給出錯誤的診斷。
     */
    @Test
    fun `閘門切掉加速段之後診斷才正確`() {
        val steady = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 90.0,
            wow = listOf(WowComponent(0.40, rot33)),
        )
        // 前面接 8 秒從靜止開始的加速
        val fs = 100.0
        val ramp = ArrayList<SpinSample>()
        val target = (100.0 / 3.0) * 6.0
        val rampCount = (8 * fs).toInt()
        for (i in 0 until rampCount) {
            ramp += SpinSample(t = i / fs, omega = target * i / rampCount)
        }
        val shifted = steady.samples.map { SpinSample(it.t + 8.0, it.omega, it.yaw) }
        val a = MeasurementAnalysis.analyze(ramp + shifted)!!

        assertTrue(a.trimmedStartSeconds > 5.0, "8 秒的加速段幾乎要全切，實得 ${a.trimmedStartSeconds}")
        assertTrue(abs(a.meanRPM - 100.0 / 3.0) < 0.05, "平均轉速不該被加速段拉低：${a.meanRPM}")
        val top = a.peaks.first()
        assertEquals(SpectralPeak.Kind.Eccentricity, top.kind, "偏心峰不該被低頻能量淹掉")
        assertTrue(abs(top.amplitudePercent - 0.40) < 0.05, "振幅 ${top.amplitudePercent}")
    }
}
