import SwiftUI
import SwiftData

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
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let e = r.errorPercent {
                    Text("\(e >= 0 ? "+" : "")\(String(format: "%.3f", e))%")
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(abs(e) <= 0.3 ? .green : .orange)
                }
                Spacer()
                if !r.isCalibrated {
                    Image(systemName: "exclamationmark.circle")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }
            HStack(spacing: 10) {
                Text(r.date.formatted(date: .abbreviated, time: .shortened))
                Text(String(format: "W&F %.3f%%", r.wrmsPercent))
                Text(String(format: "偏心 %.3f%%", r.onePerRevPercent))
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .monospacedDigit()

            if !r.note.isEmpty {
                Text(r.note)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
    }
}

struct HistoryDetailView: View {
    @Bindable var record: MeasurementRecord

    var body: some View {
        List {
            Section("轉速") {
                DiagnosticRow("平均轉速", String(format: "%.4f", record.meanRPM), "RPM")
                if let n = record.nominalLabel { DiagnosticRow("標稱", "\(n) 轉") }
                if let e = record.errorPercent {
                    DiagnosticRow("偏差", String(format: "%+.3f", e), "%")
                }
                if let k = record.calibrationFactor {
                    DiagnosticRow("套用的倍率", String(format: "%.5f", k))
                } else {
                    Label("這次量測沒有校準，偏差不能拿來調唱盤。",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
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
                            .font(.subheadline)
                            Text(p.interpretation)
                                .font(.caption)
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
