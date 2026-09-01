package com.roger.turntablerpm.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs

/**
 * 拿 iOS app 匯出的**真實**逐樣本資料餵進 Kotlin 核心，跟 Swift 算出來的摘要對照。
 *
 * 合成訊號驗證的是「數學有沒有照規格實作」；這個驗證的是
 * **兩個獨立實作對同一段實體錄音會不會得到同樣的結論**。
 * 那份錄音來自一台已標定的 Thorens TD 235 EV，特徵是已知的
 * （偏心 1×、皮帶 0.9107×、馬達 35.29×）。
 *
 * 匯出檔不進版控（一次 3 分鐘約 2 MB），所以這不是自動測試，而是手動跑的工具：
 *
 *     make android-crosscheck FILE=TurntableRPM-20260901-155337.json
 */
object ExportCrossCheck {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("用法：ExportCrossCheck <匯出的 JSON>")
            return
        }
        val root = Json.parseToJsonElement(File(args[0]).readText()) as JsonObject
        val columns = (root["columns"] as JsonArray).map { it.jsonPrimitive.content }
        val idx = columns.withIndex().associate { (i, n) -> n to i }
        val rows = root["samples"] as JsonArray
        val summary = root["summary"] as JsonObject
        fun num(key: String) = summary[key]?.jsonPrimitive?.content?.toDoubleOrNull()

        val samples = rows.map { row ->
            val v = (row as JsonArray).map { it.jsonPrimitive.content.toDouble() }
            SpinSample(t = v[idx["t"]!!], omega = v[idx["omega"]!!], yaw = v[idx["yaw"]!!])
        }
        println("樣本 ${samples.size}，時長 %.1f s".format(samples.last().t - samples.first().t))

        // 注意欄位語意：g 是重力，b 是已校準磁場，**r 是未校準的原始磁力計**（不是角速度）。
        // 匯出檔裡沒有原始三軸角速度 —— omega 已經是重力投影之後的結果，
        // 所以 SpinProjector 沒辦法用這份資料驗證，只能靠合成訊號的黃金值。
        // （第一版我把 r 當成角速度，算出來差 28410 °/s，資料自己把錯誤指出來了。）
        val verticalField = rows.take(1).map { row ->
            val v = (row as JsonArray).map { it.jsonPrimitive.content.toDouble() }
            abs(v[idx["rz"]!!])
        }.first()
        println("未校準磁力計的垂直分量 %.0f µT（磁吸配件的量級，見 CLAUDE.md 坑 13）".format(verticalField))

        // 完整分析
        val a = MeasurementAnalysis.analyze(samples, sampleRate = 100.0)
        if (a == null) { println("分析回 null"); return }

        fun compare(label: String, kotlin: Double, swift: Double?, unit: String = "") {
            if (swift == null) { println("  %-16s Kotlin %.5f%s   (Swift 摘要沒這項)".format(label, kotlin, unit)); return }
            val rel = if (swift != 0.0) abs(kotlin - swift) / abs(swift) * 100 else 0.0
            println("  %-16s Kotlin %.5f%s   Swift %.5f%s   差 %+.4f%%".format(label, kotlin, unit, swift, unit, rel))
        }
        println("\n分析結果對照（Swift 的值來自匯出檔的 summary）")
        compare("平均轉速", a.meanRPM, num("rawMeanRPM"), " RPM")
        compare("轉盤基頻", a.rotationHz, num("analysisRotationHz"), " Hz")
        compare("加權 WRMS", a.wowFlutter.wrmsPercent, num("analysisWrmsPercent"), " %")
        compare("DIN 2σ 峰值", a.wowFlutter.peak2SigmaPercent, num("analysisPeak2SigmaPercent"), " %")
        compare("每圈一次", a.onePerRevolutionPercent, num("analysisOnePerRevPercent"), " %")
        compare("峰值/RMS", a.wowFlutter.peakToRMSRatio, num("analysisPeakToRMSRatio"))

        println("\n譜峰（Kotlin）")
        println("  %9s %9s %8s  %s".format("Hz", "振幅%", "倍數", "判讀"))
        for (p in a.peaks.take(8)) {
            println("  %9.4f %9.4f %8.3f  %s".format(
                p.frequencyHz, p.amplitudePercent, p.orderOfRotation, p.kind))
        }
        println("\n最強成分佔比 %.1f%%   切掉開頭 %.1f s、尾端 %.1f s".format(
            a.dominantPeakShare * 100, a.trimmedStartSeconds, a.trimmedEndSeconds))
    }
}
