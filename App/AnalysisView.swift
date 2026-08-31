import SwiftUI
import Charts
import TurntableCore

/// 量測結束後的分析結果。
///
/// **這一頁是這個 App 跟「只顯示一個 RPM 數字」的工具之間的差別。**
/// 平均轉速只告訴你盤轉得快不快；頻譜與極座標告訴你**問題出在哪個零件**。
struct AnalysisView: View {
    let analysis: MeasurementAnalysis
    /// 標稱轉速，用來標示規格線。
    var nominal: TurntableSpeed?

    /// 色階。
    ///
    /// 核心建議 ±2 × 加權 WRMS，理由是固定上下限才能跨次量測比較顏色。但慢速 wow
    /// 會被加權曲線大幅折減（0.55 Hz 的權重只有 0.29），所以 WRMS 常常遠低於原始
    /// 偏差 —— 實測 1× 振幅 0.40% 配上建議色階 0.19%，整張圖有一半是削平的，
    /// 形狀完全看不出來。
    ///
    /// 折衷：不夠時把上限撐到剛好包住最大值，**並且把數值印在圖例上**。
    /// 可比較性靠印出來的數字維持，不是靠固定色階犧牲掉圖面資訊。
    private var heatScale: Double {
        let suggested = PolarAccumulator.suggestedColorScale(
            wrmsPercent: analysis.wowFlutter.wrmsPercent)
        let peak = analysis.polarBins.map { abs($0.meanDeviation) }.max() ?? 0
        return max(suggested, peak * 1.05, 0.001)
    }

    /// 色階是不是被撐開過。有的話要在說明裡講，不然使用者會誤以為兩張圖的紅色一樣深。
    private var heatScaleExpanded: Bool {
        heatScale > PolarAccumulator.suggestedColorScale(
            wrmsPercent: analysis.wowFlutter.wrmsPercent) * 1.001
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if !analysis.stableWindow.isPristine { trimBanner }
                wowFlutterCard
                peaksCard
                spectrumCard
                heatmapCard
                rollingCard
            }
            .padding()
        }
        .navigationTitle("分析")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 資料被裁切時一定要講。使用者有權知道分析用的不是他錄的全部 ——
    /// 而且「開頭被切掉 8 秒」本身就是在告訴他操作順序可以改進。
    private var trimBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "scissors")
                .foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 3) {
                Text("已自動略過轉速不穩的區間")
                    .font(.subheadline.weight(.medium))
                Text(trimDescription)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .measurementCard()
    }

    private var trimDescription: String {
        var parts: [String] = []
        if analysis.trimmedStartSeconds > 0.05 {
            parts.append(String(format: "開頭 %.1f 秒", analysis.trimmedStartSeconds))
        }
        if analysis.trimmedEndSeconds > 0.05 {
            parts.append(String(format: "結尾 %.1f 秒", analysis.trimmedEndSeconds))
        }
        if analysis.stableWindow.droppedInMiddle > 0 {
            parts.append(String(format: "中途 %.1f 秒",
                                Double(analysis.stableWindow.droppedInMiddle) / analysis.sampleRate))
        }
        let what = parts.isEmpty ? "部分區間" : parts.joined(separator: "、")
        return "\(what)的轉速偏離太多（放上手機、盤面加速或減速、量測中被碰到），"
             + String(format: "已排除。下面所有數字都是剩下 %.0f 秒算出來的。",
                      analysis.durationSeconds)
    }

    // MARK: - 抖晃率

    private var wowFlutterCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("抖晃率")
                .font(.headline)
                .padding(.bottom, 2)

            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(String(format: "%.3f", analysis.wowFlutter.wrmsPercent))
                    .font(.system(size: 40, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                Text("% WRMS")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            DiagnosticRow("DIN 2σ 峰值",
                          String(format: "%.3f", analysis.wowFlutter.peak2SigmaPercent), "%")
            DiagnosticRow("峰值 / RMS",
                          String(format: "%.2f", analysis.wowFlutter.peakToRMSRatio))
            DiagnosticRow("最強成分佔比",
                          String(format: "%.0f", analysis.dominantPeakShare * 100), "%")
            DiagnosticRow("每圈一次成分",
                          String(format: "%.3f", analysis.onePerRevolutionPercent), "%")

            Text(ratioInterpretation)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .measurementCard()
    }

    /// 判斷「單頻主導」還是「隨機」用譜峰的功率分布，不用峰值/RMS 比。
    ///
    /// 實測同一台唱盤兩次得到 1.67 與 1.95，譜峰內容卻一模一樣 —— 比值本身的
    /// 隨機起伏就跨過了 1.41 與 1.96 的中點，拿它當門檻會給出前後矛盾的結論。
    private var ratioInterpretation: String {
        let share = analysis.dominantPeakShare
        guard let top = analysis.peaks.first else {
            return "沒有找到顯著的週期性成分。"
        }
        let hz = String(format: "%.3f Hz", top.frequencyHz)
        let pct = Int((share * 100).rounded())
        if share > 0.6 {
            return "抖動由 \(hz) 這一根主導（佔譜峰功率 \(pct)%）—— 是某個零件的"
                 + "週期性問題，不是隨機雜訊。看下面的判讀。"
        }
        if share < 0.3 {
            return "沒有單一主導的頻率（最強的 \(hz) 只佔 \(pct)%），"
                 + "抖動比較像分散的隨機雜訊。"
        }
        return "\(hz) 是最強的成分（佔 \(pct)%），但還有其他來源，兩種成分都有。"
    }

    // MARK: - 譜峰判讀（這是最有價值的一區）

    private var peaksCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("問題出在哪")
                .font(.headline)

            if analysis.peaks.isEmpty {
                Text("沒有找到顯著的週期性成分 —— 這是好事，代表沒有單一零件在主導誤差。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(analysis.peaks.prefix(5).enumerated()), id: \.offset) { _, peak in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(String(format: "%.3f Hz", peak.frequencyHz))
                                .font(.subheadline.weight(.medium))
                                .monospacedDigit()
                            Spacer()
                            Text(String(format: "%.3f %%", peak.amplitudePercent))
                                .font(.subheadline)
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                        }
                        Text(peak.interpretation)
                            .font(.caption)
                            .foregroundStyle(peak.isRotationHarmonic ? .orange : .secondary)
                    }
                    .padding(.vertical, 3)
                    Divider()
                }
                Text("整數倍 = 跟著盤面轉的東西（偏心、變形）。非整數倍 = 傳動鏈上"
                     + "轉速不同的零件（馬達、皮帶輪）。")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .measurementCard()
    }

    // MARK: - 頻譜

    private var spectrumCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("頻譜")
                .font(.headline)

            Chart {
                ForEach(spectrumPoints, id: \.f) { p in
                    LineMark(x: .value("頻率", p.f), y: .value("振幅", p.a))
                        .foregroundStyle(.blue)
                }
                ForEach(Array(analysis.peaks.prefix(3).enumerated()), id: \.offset) { _, peak in
                    PointMark(x: .value("頻率", peak.frequencyHz),
                              y: .value("振幅", peak.amplitudePercent))
                        .foregroundStyle(peak.isRotationHarmonic ? .orange : .gray)
                        .symbolSize(60)
                }
            }
            .chartXScale(domain: 0.1 ... 50, type: .log)
            .chartXAxis {
                AxisMarks(values: [0.1, 0.5, 1, 5, 10, 50]) { v in
                    AxisGridLine()
                    AxisValueLabel {
                        if let d = v.as(Double.self) {
                            Text(d < 1 ? String(format: "%.1f", d) : String(format: "%.0f", d))
                        }
                    }
                }
            }
            .frame(height: 180)

            Text("橫軸對數，Hz。橘點是轉盤諧波，灰點不是。")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .measurementCard()
    }

    /// 頻譜點數可達數萬，全部丟給 Chart 會很慢。抽樣到大約 400 點，
    /// 但每段取**最大值**而不是平均 —— 取平均會把窄峰洗掉，那正是要看的東西。
    private var spectrumPoints: [(f: Double, a: Double)] {
        let freqs = analysis.spectrumFrequencies
        let amps = analysis.spectrumAmplitudes
        guard freqs.count > 1 else { return [] }
        let usable = freqs.indices.filter { freqs[$0] >= 0.1 && freqs[$0] <= 50 }
        guard !usable.isEmpty else { return [] }
        let stride = max(1, usable.count / 400)
        var out: [(f: Double, a: Double)] = []
        var i = 0
        while i < usable.count {
            let chunk = usable[i ..< min(i + stride, usable.count)]
            if let best = chunk.max(by: { amps[$0] < amps[$1] }) {
                out.append((freqs[best], amps[best]))
            }
            i += stride
        }
        return out
    }

    // MARK: - 極座標熱圖

    private var heatmapCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("誤差在盤面的分布")
                .font(.headline)

            PolarHeatmapView(bins: analysis.polarBins,
                             scale: heatScale,
                             peakAngleDegrees: analysis.peakAngleDegrees)
                .frame(maxWidth: .infinity)
                .frame(height: 240)

            HeatmapLegend(scale: heatScale)

            if let peak = analysis.peakAngleDegrees {
                Text(String(format: "誤差最大的角度在 %.0f°（指針處）。顏色只集中在一邊"
                            + "代表偏心；均勻散開代表隨機抖動。", peak))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            // 單一字串字面值 —— Text 的 markdown 只在字面值生效（今晚第三次踩到）。
            Text("**角度不能跨次量測比較。** 0° 是「按下開始的那一瞬間」盤面所在的位置，而那是隨機的。要能比較，盤面貼一個記號，每次都等記號轉到同一個位置（例如唱臂那側）才按開始。")
                .font(.caption2)
                .foregroundStyle(.secondary)
            if heatScaleExpanded {
                Text("色階已放大到容納最大值 —— 跟別次量測比較時要看圖例上的數字，"
                     + "不能只比顏色深淺。")
                    .font(.caption2)
                    .foregroundStyle(.orange)
            }
        }
        .measurementCard()
    }

    // MARK: - 滾動圖

    private var rollingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("瞬時偏差")
                .font(.headline)

            Chart {
                ForEach(rollingPoints, id: \.t) { p in
                    LineMark(x: .value("時間", p.t), y: .value("偏差", p.d))
                        .foregroundStyle(.blue)
                }
                RuleMark(y: .value("零", 0))
                    .foregroundStyle(.gray.opacity(0.4))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            }
            .frame(height: 140)

            Text("未平滑的原始偏差，%。分析路徑一律不套移動平均 —— 平滑會把 4 Hz"
                 + "附近的抖晃挖掉，數字會漂亮得沒有意義。")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .measurementCard()
    }

    private var rollingPoints: [(t: Double, d: Double)] {
        let d = analysis.deviationPercent
        guard !d.isEmpty else { return [] }
        let stride = max(1, d.count / 600)
        return Swift.stride(from: 0, to: d.count, by: stride).map {
            (Double($0) / analysis.sampleRate, d[$0])
        }
    }
}
