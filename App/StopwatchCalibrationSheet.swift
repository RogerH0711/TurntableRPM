import SwiftUI
import TurntableCore

/// 碼錶校準的輸入畫面。
///
/// 量法：盤面邊緣貼個記號，碼錶按下去開始數圈，數滿 N 圈按停。
/// 圈數愈多精度愈好 —— 人為的按錶誤差是固定的 ±0.3 秒左右，被總時間攤薄。
struct StopwatchCalibrationSheet: View {
    /// App 這一次量到的轉速（未修正）。碼錶與這次量測必須是同一段轉動。
    let measuredRPM: Double
    let onSave: (StopwatchCalibration) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var revolutionsText = "100"
    @State private var secondsText = ""

    private var revolutions: Int? { Int(revolutionsText.trimmingCharacters(in: .whitespaces)) }
    private var seconds: Double? { Double(secondsText.trimmingCharacters(in: .whitespaces)) }

    private var result: StopwatchCalibration? {
        guard let revolutions, let seconds else { return nil }
        return StopwatchCalibration(revolutions: revolutions,
                                    seconds: seconds,
                                    measuredRPM: measuredRPM,
                                    deviceModel: CalibrationStore.deviceModel)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("這次量測") {
                    LabeledContent("App 量到的轉速",
                                   value: String(format: "%.4f RPM", measuredRPM))
                    Text("碼錶要量的是**同一段轉動**。中途調過速度或換過轉速檔位，這個 k 就不對了。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("碼錶讀數") {
                    LabeledContent("圈數") {
                        TextField("100", text: $revolutionsText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("秒數") {
                        TextField("187.91", text: $secondsText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                }

                if let result {
                    Section("結果") {
                        LabeledContent("碼錶推算轉速",
                                       value: String(format: "%.4f RPM", result.trueRPM))
                        LabeledContent("倍率 k",
                                       value: String(format: "%.5f", result.factor))
                        LabeledContent("陀螺儀偏差",
                                       value: String(format: "%+.3f %%",
                                                     (1.0 / result.factor - 1.0) * 100))
                        LabeledContent("這次校準的精度",
                                       value: String(format: "±%.3f %%", result.precision() * 100))

                        if !result.isPlausible {
                            Label("k = \(String(format: "%.3f", result.factor)) 不合理。"
                                  + "MEMS 陀螺儀的比例因子誤差是百分之幾的等級，不會到這種程度 —— "
                                  + "檢查圈數或秒數是不是打錯了。",
                                  systemImage: "exclamationmark.triangle.fill")
                                .font(.footnote)
                                .foregroundStyle(.orange)
                        }
                    }
                } else {
                    Section {
                        Text("填入圈數與秒數就會算出倍率。")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("怎麼量") {
                    Text("""
                    1. 盤面邊緣貼一個看得見的記號
                    2. 記號經過某個固定參考點時按下碼錶，同時開始數
                    3. 數滿設定的圈數，記號再次經過同一點時按停

                    100 圈在 33⅓ 轉大約 3 分鐘，精度 ±0.17%；200 圈約 6 分鐘，±0.08%。
                    圈數太少不值得做 —— 10 圈只有 1.7%，比不校準好不了多少。
                    """)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("碼錶校準")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("儲存") {
                        if let result { onSave(result) }
                        dismiss()
                    }
                    .disabled(result?.isPlausible != true)
                }
            }
        }
    }
}
