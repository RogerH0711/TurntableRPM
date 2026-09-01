package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** 規格 §3.4。黃金值 = A × W(f) / √2，由 Python 參考實作獨立算出。 */
class WowFlutterTest {

    @Test
    fun `加權曲線通過 AES 錨點`() {
        // AES 公布的錨點；閉式近似的最大誤差 3.5%
        val anchors = listOf(
            Triple(0.2, 0.0296, 0.05),
            Triple(0.8, 0.5000, 0.04),
            Triple(4.0, 1.0000, 0.01),
            Triple(20.0, 0.5080, 0.01),
        )
        for ((f, standard, tolerance) in anchors) {
            val w = WowFlutterWeighting.weight(f)
            assertTrue(abs(w - standard) <= standard * tolerance, "W($f Hz) = $w，標準 $standard")
        }
        assertTrue(WowFlutterWeighting.weight(0.0) == 0.0)
        // 峰值落在 3.968 Hz，且值為 1
        assertTrue(abs(WowFlutterWeighting.weight(3.9683751) - 1.0) < 1e-9)
    }

    private fun deviation(amplitude: Double, frequency: Double): DoubleArray {
        val run = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 60.0,
            wow = listOf(WowComponent(amplitudePercent = amplitude, frequencyHz = frequency)),
        )
        return DeviationSeries.make(run.trueOmega)!!.deviationPercent
    }

    /** 黃金值直接讀 golden.json 的 wrms_sine。 */
    @Test
    fun `純正弦的 WRMS 符合黃金值`() {
        for (case in Golden.array("wrms_sine")) {
            val amp = Golden.number(case, "amp_pct")
            val freq = Golden.number(case, "freq_hz")
            val expected = Golden.number(case, "expected_wrms_pct")
            val result = WowFlutterAnalyzer.analyze(deviation(amp, freq), sampleRate = 100.0)
            assertTrue(result != null, "A=$amp% @ $freq Hz：分析回了 null")
            assertTrue(
                abs(result.wrmsPercent - expected) <= expected * 0.015,
                "A=$amp% @ $freq Hz：黃金值 $expected%，實得 ${result.wrmsPercent}%",
            )
        }
    }

    /**
     * 峰值/RMS 比可以區分來源型態，但**不要拿它當二分判準** ——
     * 實測同一台唱盤兩次得到 1.67 與 1.95，比值本身的隨機起伏就跨過了中點。
     * 正式判讀用的是譜峰功率佔比。
     */
    @Test
    fun `峰值對 RMS 比反映來源型態`() {
        // 單頻正弦 wow（偏心、皮帶接縫）
        val sine = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 120.0,
            wow = listOf(WowComponent(0.5, 4.0)),
        )
        val sineResult = WowFlutterAnalyzer.analyze(
            DeviationSeries.make(sine.trueOmega)!!.deviationPercent, 100.0,
        )!!
        val expectedSine = Golden.number("ratio_sine")
        assertTrue(
            abs(sineResult.peakToRMSRatio - expectedSine) < 0.06,
            "單頻：黃金值 $expectedSine，實得 ${sineResult.peakToRMSRatio}",
        )

        // 高斯型隨機抖動（軸承、馬達雜訊）。亂數產生器與 Python 不同，所以只比到統計量。
        val noise = SyntheticSignal.make(
            nominalRPM = 100.0 / 3.0, durationSeconds = 120.0, noisePercent = 0.5, seed = 7,
        )
        val noiseResult = WowFlutterAnalyzer.analyze(
            DeviationSeries.make(noise.trueOmega)!!.deviationPercent, 100.0,
        )!!
        val expectedGauss = Golden.number("ratio_gauss")
        assertTrue(
            abs(noiseResult.peakToRMSRatio - expectedGauss) < 0.12,
            "隨機：黃金值 $expectedGauss，實得 ${noiseResult.peakToRMSRatio}",
        )
    }

    @Test
    fun `加權會濾掉極慢的漂移`() {
        // 0.05 Hz 的慢漂移在標準曲線下幾乎不計分（W < 0.002）
        val result = WowFlutterAnalyzer.analyze(deviation(2.0, 0.05), 100.0)!!
        assertTrue(result.wrmsPercent < 0.02, "實得 ${result.wrmsPercent}%")
    }

    /** 這就是「最大偏差必須連同頻寬一起回報」的原因。 */
    @Test
    fun `最大偏差取決於平滑視窗`() {
        val d = deviation(0.5, 4.0)
        val tight = DeviationSeries.maxDeviation(d, 100.0, smoothingWindow = 5)
        val loose = DeviationSeries.maxDeviation(d, 100.0, smoothingWindow = 40)
        assertTrue(abs(tight.windowSeconds - 0.05) < 1e-12)
        assertTrue(abs(loose.windowSeconds - 0.4) < 1e-12)
        assertTrue(
            tight.value > loose.value * 3.0,
            "同一段訊號在不同平滑視窗下的最大偏差可以差好幾倍：${tight.value} vs ${loose.value}",
        )
    }
}
