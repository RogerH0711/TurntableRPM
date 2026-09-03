package com.roger.turntablerpm.sensor

import com.roger.turntablerpm.core.SamplingStats
import com.roger.turntablerpm.core.TurntableSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 取樣診斷頁跟量測畫面共用同一個引擎，但**跑診斷不是一次量測**。
 *
 * 實機上撞到的症狀（Pixel 3a）：只在診斷頁跑 10 秒再返回，主畫面就多出一筆
 * 「這次量測 10.0 s / 994 筆」加一個橘色的「分析不出來」，而使用者從沒按過開始。
 * 更嚴重的是分析成功時會 `onAnalysisComplete` 自動存一筆歷史 —— 把手機放在
 * 轉動的盤面上跑取樣診斷是很自然的事。
 */
class EngineStateMeasurementTest {

    private val measured = EngineState(
        instantRPM = 33.4,
        meanRPM = 33.33,
        rawMeanRPM = 33.36,
        appliedFactor = 0.99915,
        nominal = TurntableSpeed.RPM33,
        errorPercent = -0.01,
        revolutions = 107,
        analysisFailureReason = null,
        exportPath = "/data/export.json",
        sampleCount = 20377,
        elapsedSeconds = 201.1,
        stats = stats(count = 20377, rate = 100.13, duration = 201.1),
    )

    private fun stats(count: Int, rate: Double, duration: Double) = SamplingStats(
        count = count,
        durationSeconds = duration,
        effectiveRateHz = rate,
        medianIntervalMs = 1000.0 / rate,
        meanIntervalMs = 1000.0 / rate,
        stdDevIntervalMs = 0.0,
        minIntervalMs = 1000.0 / rate,
        maxIntervalMs = 1000.0 / rate,
        longGaps = 0,
        worstGapRatio = 1.0,
    )

    @Test
    fun `診斷跑不會蓋掉上一次量測的結果`() {
        // 診斷跑產生的新狀態：取樣欄位是新的，量測欄位是診斷跑的雜訊
        val duringDiagnostics = EngineState(
            sampleCount = 994,
            elapsedSeconds = 10.0,
            instantRPM = 0.008,
            meanRPM = 0.0079,
            rawMeanRPM = 0.0079,
            revolutions = 0,
            analysisFailureReason = "整段量測都沒有穩定的轉速",
            stats = stats(count = 994, rate = 99.25, duration = 10.0),
        )

        val kept = duringDiagnostics.keepingMeasurementOf(measured)
            .copy(samplingStats = duringDiagnostics.stats)

        // 量測結果原封不動
        assertEquals(33.33, kept.meanRPM!!, 1e-9)
        assertEquals(33.36, kept.rawMeanRPM!!, 1e-9)
        assertEquals(33.4, kept.instantRPM, 1e-9)
        assertEquals(0.99915, kept.appliedFactor!!, 1e-9)
        assertEquals(TurntableSpeed.RPM33, kept.nominal)
        assertEquals(107, kept.revolutions)
        assertEquals("/data/export.json", kept.exportPath)
        // 診斷跑不該憑空生出一個「分析不出來」
        assertNull(kept.analysisFailureReason)

        // **這三個兩邊都在用，一律留給量測** —— 第一版漏掉，實機上平均轉速保住了
        // 但「量測時間 8.1 s / 樣本數 801」被診斷跑的 6.1 s / 602 蓋掉。
        assertEquals(20377, kept.sampleCount)
        assertEquals(201.1, kept.elapsedSeconds, 1e-9)
        assertEquals(100.13, kept.stats!!.effectiveRateHz, 1e-9)

        // 診斷頁要看的取樣特性走自己的欄位，不干擾上面那些
        assertEquals(994, kept.samplingStats!!.count)
        assertEquals(99.25, kept.samplingStats!!.effectiveRateHz, 1e-9)
    }

    @Test
    fun `還沒量過的時候診斷跑不會生出一筆假量測`() {
        // 主畫面用 rawMeanRPM 判斷「有沒有量過」，所以它必須留在 null。
        val kept = EngineState(
            sampleCount = 994,
            elapsedSeconds = 10.0,
            meanRPM = 0.0079,
            rawMeanRPM = 0.0079,
            analysisFailureReason = "整段量測都沒有穩定的轉速",
        ).keepingMeasurementOf(EngineState())

        assertNull(kept.meanRPM)
        assertNull(kept.rawMeanRPM)
        assertNull(kept.analysisFailureReason)
        assertNull(kept.exportPath)
        assertEquals(0, kept.revolutions)
        // 沒量過就是沒量過，診斷跑不該讓主畫面冒出一張「這次量測」卡
        assertEquals(0, kept.sampleCount)
        assertNull(kept.stats)
    }
}
