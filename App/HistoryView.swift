import SwiftUI
import SwiftData
import Charts

/// 量測歷史。
///
/// 這一頁真正的用途是**比較**：調過唱盤之後有沒有變好、冷機跟熱機差多少、
/// 33 轉跟 45 轉的偏差是不是等比例。所以列表直接把關鍵數字攤開，
/// 不用點進去才看得到。
struct HistoryView: View {
    @Query(sort: \MeasurementRecord.date, order: .reverse)
    private var records: [MeasurementRecord]
    @Environment(\.modelContext) private var context

    var body: some View {
        Group {
            if records.isEmpty {
                ContentUnavailableView("還沒有量測記錄",
                                       systemImage: "clock.arrow.circlepath",
                                       description: Text("完成一次量測之後會自動存進來。"))
            } else {
                List {
                    if records.count >= 2 {
                        Section {
                            TrendChart(records: records.reversed())
                        }
                    }
                    ForEach(records) { record in
                        NavigationLink {
                            HistoryDetailView(record: record)
                        } label: {
                            row(record)
                        }
                    }
                    .onDelete { offsets in
                        for i in offsets { context.delete(records[i]) }
                    }
                }
            }
        }
        .navigationTitle("歷史")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { if !records.isEmpty { EditButton() } }
    }

    private func row(_ r: MeasurementRecord) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(String(format: "%.3f", r.meanRPM))
                    .font(.headline)
                    .monospacedDigit()
                Text("RPM")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if let e = r.errorPercent {
                    Text("\(e >= 0 ? "+" : "")\(String(format: "%.3f", e))%")
                        .font(.body)
                        .monospacedDigit()
                        .foregroundStyle(abs(e) <= 0.3 ? .green : .orange)
                }
                Spacer()
                if !r.isCalibrated {
                    Image(systemName: "exclamationmark.circle")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }
            HStack(spacing: 10) {
                // 字級放大後 .abbreviated 會換行成兩行。年份對這個用途沒有意義，
                // 月日時分就夠。
                Text(r.date.formatted(.dateTime.month(.abbreviated).day()
                                        .hour().minute()))
                Text(String(format: "W&F %.3f%%", r.wrmsPercent))
                Text(String(format: String(localized: "偏心 %.3f%%"), r.onePerRevPercent))
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
            .monospacedDigit()

            if !r.note.isEmpty {
                Text(r.note)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
    }
}

/// 歷次量測的趨勢。
///
/// 這張圖回答的是「**我調完之後有沒有變好**」。清單看得到單次的數字，
/// 但看不出方向 —— 而調唱盤本來就是反覆量、反覆調的過程。
struct TrendChart: View {
    /// 由舊到新。
    let records: [MeasurementRecord]
    @State private var metric: Metric = .error

    enum Metric: String, CaseIterable, Identifiable {
        case error, wow, eccentricity
        var id: String { rawValue }
        /// 已經在地化過的字串。呼叫端用 `Text(變數)`，那是 verbatim 的初始化式，
        /// 所以這裡必須自己翻好 —— 每個 case 各一次 `String(localized:)`，
        /// 寫成 `String(localized: switch …)` 就抽不出來了。
        var label: String {
            switch self {
            case .error: return String(localized: "偏差")
            case .wow: return String(localized: "抖晃率")
            case .eccentricity: return String(localized: "偏心")
            }
        }
        var unit: String { "%" }
        /// 偏差有正負、目標是 0；另外兩個恆為正、愈小愈好。
        var hasZeroTarget: Bool { self == .error }
    }

    private func value(_ r: MeasurementRecord) -> Double? {
        switch metric {
        case .error: return r.errorPercent
        case .wow: return r.wrmsPercent
        case .eccentricity: return r.onePerRevPercent
        }
    }

    private var points: [(date: Date, value: Double, calibrated: Bool, note: String)] {
        records.compactMap { r in
            value(r).map { (r.date, $0, r.isCalibrated, r.note) }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Picker("指標", selection: $metric) {
                ForEach(Metric.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)

            if points.count < 2 {
                Text("這個指標還沒有足夠的資料。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(height: 60)
            } else {
                Chart {
                    ForEach(Array(points.enumerated()), id: \.offset) { _, p in
                        LineMark(x: .value("時間", p.date), y: .value(metric.label, p.value))
                            .foregroundStyle(.blue)
                            .interpolationMethod(.monotone)
                        PointMark(x: .value("時間", p.date), y: .value(metric.label, p.value))
                            // 未校準的點用空心 —— 它的偏差不可採信，不該跟其他點
                            // 看起來一樣有份量。
                            .symbol(p.calibrated ? .circle : .diamond)
                            .foregroundStyle(p.calibrated ? .blue : .orange)
                    }
                    if metric.hasZeroTarget {
                        RuleMark(y: .value("標稱", 0))
                            .foregroundStyle(.green.opacity(0.6))
                            .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    }
                }
                .chartYAxisLabel("%")
                .frame(height: 170)

                if points.contains(where: { !$0.calibrated }) {
                    Label("橘色菱形是未校準的量測，那些數字不能拿來比較。",
                          systemImage: "exclamationmark.circle")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                }
                if let change = changeDescription {
                    Text(change)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }

    /// 直接講出「變好還是變差」。看圖要自己判讀，寫出來就不用。
    private var changeDescription: String? {
        let usable = points.filter { $0.calibrated }
        guard let first = usable.first, let last = usable.last, usable.count >= 2 else { return nil }
        let delta = abs(last.value) - abs(first.value)
        guard abs(delta) > 0.001 else { return String(localized: "跟第一次相比幾乎沒有變化。") }
        let word = delta < 0 ? String(localized: "改善") : String(localized: "變差")
        return String(format: String(localized: "跟第一次相比%@了 %.3f 個百分點（%.3f%% → %.3f%%）。"),
                      word, abs(delta), first.value, last.value)
    }
}

struct HistoryDetailView: View {
    @Bindable var record: MeasurementRecord

    var body: some View {
        List {
            Section("轉速") {
                DiagnosticRow("平均轉速", String(format: "%.4f", record.meanRPM), "RPM")
                if let n = record.nominalLabel { DiagnosticRow("標稱", String(localized: "\(n) 轉")) }
                if let e = record.errorPercent {
                    DiagnosticRow("偏差", String(format: "%+.3f", e), "%")
                }
                if let k = record.calibrationFactor {
                    DiagnosticRow("套用的倍率", String(format: "%.5f", k))
                } else {
                    Label("這次量測沒有校準，偏差不能拿來調唱盤。",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }

            Section("抖晃率") {
                DiagnosticRow("加權 WRMS", String(format: "%.4f", record.wrmsPercent), "%")
                DiagnosticRow("DIN 2σ 峰值",
                              String(format: "%.4f", record.peak2SigmaPercent), "%")
                DiagnosticRow("每圈一次成分",
                              String(format: "%.4f", record.onePerRevPercent), "%")
                DiagnosticRow("最強成分佔比",
                              String(format: "%.0f", record.dominantPeakShare * 100), "%")
            }

            if !record.peaks.isEmpty {
                Section("譜峰") {
                    ForEach(record.peaks) { p in
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text(String(format: "%.3f Hz", p.frequencyHz))
                                    .monospacedDigit()
                                Spacer()
                                Text(String(format: "%.3f %%", p.amplitudePercent))
                                    .monospacedDigit()
                                    .foregroundStyle(.secondary)
                            }
                            .font(.body)
                            Text(p.interpretation)
                                .font(.footnote)
                                .foregroundStyle(p.isHarmonic ? .orange : .secondary)
                        }
                    }
                }
            }

            Section("量測條件") {
                DiagnosticRow("時間", record.date.formatted(date: .long, time: .standard))
                DiagnosticRow("時長", String(format: "%.0f", record.durationSeconds), "s")
                DiagnosticRow("圈數", "\(record.revolutions)")
                if record.trimmedSeconds > 0.05 {
                    DiagnosticRow("自動略過",
                                  String(format: "%.1f", record.trimmedSeconds), "s")
                }
            }

            Section("備註") {
                TextField("例如：調整速度微調之後、冷機、45 轉", text: $record.note,
                          axis: .vertical)
                    .lineLimit(1 ... 4)
            }
        }
        .navigationTitle(record.date.formatted(date: .abbreviated, time: .shortened))
        .navigationBarTitleDisplayMode(.inline)
    }
}
