import SwiftUI
import SwiftData
import TurntableCore

/// 載重測試：手機自己的重量會不會把唱盤拖慢。
///
/// **不做成特殊的量測模式。** 使用者只要正常量兩次（一次只放手機、一次加配重），
/// 然後在這裡挑那兩筆記錄就好 —— 少一套流程要學，也不必擔心中途操作錯誤。
///
/// 為什麼要實測而不是查表：手機是幾克本身不說明任何事，影響量完全取決於馬達
/// 型式。同步交流馬達幾乎為零，無調速直流馬達最大。所以量斜率再外插回零負載。
struct LoadTestView: View {
    @Bindable var profile: TurntableProfile
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \MeasurementRecord.date, order: .reverse)
    private var records: [MeasurementRecord]

    @State private var baseID: PersistentIdentifier?
    @State private var loadedID: PersistentIdentifier?
    @AppStorage("phoneMassGrams") private var phoneMassGrams = 221.0
    @State private var addedMassGrams = 100.0

    private var base: MeasurementRecord? { records.first { $0.id == baseID } }
    private var loaded: MeasurementRecord? { records.first { $0.id == loadedID } }

    private var result: LoadCompensationResult? {
        guard let base, let loaded, addedMassGrams > 0, phoneMassGrams > 0,
              base.id != loaded.id else { return nil }
        return LoadCompensator.extrapolate(rpmWithPhone: base.meanRPM,
                                           rpmWithAddedMass: loaded.meanRPM,
                                           addedMassGrams: addedMassGrams,
                                           phoneMassGrams: phoneMassGrams)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("""
                    量兩次：一次只放手機，一次在盤面上再加一個已知重量的東西（放對稱一點）。\
                    然後在下面挑出那兩筆。
                    """)
                } header: {
                    Text("怎麼做")
                }

                Section("挑出兩次量測") {
                    picker("只放手機", selection: $baseID)
                    picker("加了配重", selection: $loadedID)
                }

                Section("重量") {
                    stepperRow("手機重量", value: $phoneMassGrams, step: 1, range: 100 ... 400)
                    stepperRow("加上去的配重", value: $addedMassGrams, step: 10, range: 10 ... 1000)
                    Text("iPhone 15 Pro Max 是 221 g。配重用食譜秤量一下就好，不必很精確 —— 誤差只會等比例反映在斜率上。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let result {
                    Section("結果") {
                        DiagnosticRow("載重斜率",
                                      String(format: "%.5f", result.slopeRPMPerGram), "RPM/g")
                        DiagnosticRow("手機造成的變化",
                                      String(format: "%+.4f", result.phoneEffectRPM), "RPM")
                        DiagnosticRow("外插回零負載",
                                      String(format: "%.4f", result.zeroLoadRPM), "RPM")

                        Text(verdict(result))
                            .font(.footnote)
                            .foregroundStyle(result.isSignificant ? .orange : .secondary)

                        Button("存進「\(profile.displayName)」") { save(result) }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                    }
                } else {
                    Section {
                        Text(records.count < 2
                             ? "至少要有兩筆量測記錄才能做這個測試。"
                             : "挑出兩筆不同的量測。")
                            .foregroundStyle(.secondary)
                    }
                }

                if profile.hasLoadTest {
                    Section("已存的結果") {
                        DiagnosticRow("斜率",
                                      String(format: "%.5f", profile.loadSlopeRPMPerGram ?? 0),
                                      "RPM/g")
                        if let d = profile.loadMeasuredAt {
                            DiagnosticRow("測於", d.formatted(.dateTime.month().day().hour().minute()))
                        }
                    }
                }
            }
            .navigationTitle("載重測試")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
            }
        }
    }

    /// 斜率有沒有超過量測雜訊，決定要不要當一回事。
    private func verdict(_ r: LoadCompensationResult) -> String {
        guard r.isSignificant else {
            return String(localized: "斜率在量測雜訊以內 —— 這台唱盤對載重不敏感，手機的重量不影響讀數。")
        }
        let pct = abs(r.phoneEffectRPM / max(r.zeroLoadRPM, 0.001)) * 100
        let dir = r.phoneEffectRPM < 0 ? String(localized: "拖慢") : String(localized: "加快")
        return String(format: String(localized: "手機的重量把轉速%@了 %.4f RPM（%.3f%%）。這是量測方法本身造成的偏差，不是唱盤的問題 —— 真實的無載轉速是 %.4f RPM。"), dir, abs(r.phoneEffectRPM), pct,
                      r.zeroLoadRPM)
    }

    private func save(_ r: LoadCompensationResult) {
        profile.loadSlopeRPMPerGram = r.slopeRPMPerGram
        profile.loadPhoneEffectRPM = r.phoneEffectRPM
        profile.loadIsSignificant = r.isSignificant
        profile.loadMeasuredAt = Date()
        dismiss()
    }

    @ViewBuilder
    private func picker(_ title: LocalizedStringKey,
                        selection: Binding<PersistentIdentifier?>) -> some View {
        Picker(title, selection: selection) {
            Text("尚未選擇").tag(PersistentIdentifier?.none)
            ForEach(records) { r in
                Text(String(format: "%.4f RPM · %@", r.meanRPM,
                            r.date.formatted(.dateTime.month().day().hour().minute())))
                    .tag(PersistentIdentifier?.some(r.id))
            }
        }
    }

    private func stepperRow(_ title: LocalizedStringKey, value: Binding<Double>,
                            step: Double, range: ClosedRange<Double>) -> some View {
        Stepper(value: value, in: range, step: step) {
            HStack {
                Text(title)
                Spacer()
                Text(String(format: "%.0f g", value.wrappedValue))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
        }
    }
}
