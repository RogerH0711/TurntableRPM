import SwiftUI
import SwiftData

/// 唱盤設定檔的清單。
struct TurntableProfileListView: View {
    @Query(sort: \TurntableProfile.createdAt) private var profiles: [TurntableProfile]
    @Environment(\.modelContext) private var context

    var body: some View {
        Group {
            if profiles.isEmpty {
                ContentUnavailableView {
                    Label("還沒有唱盤設定檔", systemImage: "hifispeaker")
                } description: {
                    Text("記下原廠規格與傳動鏈尺寸之後，量測結果就能自動跟規格比對。")
                } actions: {
                    Button("新增唱盤") { add() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
            } else {
                List {
                    ForEach(profiles) { p in
                        NavigationLink { TurntableProfileEditor(profile: p) } label: { row(p) }
                    }
                    .onDelete { for i in $0 { context.delete(profiles[i]) } }
                }
            }
        }
        .navigationTitle("唱盤")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !profiles.isEmpty {
                Button { add() } label: { Image(systemName: "plus") }
            }
        }
    }

    private func row(_ p: TurntableProfile) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(p.displayName)
                    .font(.headline)
                HStack(spacing: 10) {
                    if let spec = p.specWowFlutterPercent {
                        Text(String(format: String(localized: "規格 W&F %.3f%%"), spec))
                    }
                    if let ratio = p.expectedDriveRatio {
                        Text(String(format: String(localized: "傳動比 %.1f×"), ratio))
                    }
                    if p.hasLoadTest { Text("已測載重") }
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
            Spacer()
            if p.isActive {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            }
        }
        .padding(.vertical, 2)
    }

    private func add() {
        let p = TurntableProfile()
        p.isActive = profiles.isEmpty        // 第一台自動設為使用中
        context.insert(p)
    }
}

/// 單一唱盤的編輯畫面。
struct TurntableProfileEditor: View {
    @Bindable var profile: TurntableProfile
    @Query private var allProfiles: [TurntableProfile]
    @State private var showingLoadTest = false

    var body: some View {
        Form {
            Section("這台唱盤") {
                TextField("型號，例如 TD 235 EV", text: $profile.name)
                TextField("廠牌，例如 Thorens", text: $profile.maker)
                Toggle("目前使用中", isOn: Binding(
                    get: { profile.isActive },
                    set: { on in
                        // 同時只能有一台使用中，否則分析頁不知道要拿誰的規格比對。
                        if on { for p in allProfiles { p.isActive = false } }
                        profile.isActive = on
                    }))
            }

            Section {
                optionalNumber("抖晃率規格", value: $profile.specWowFlutterPercent,
                               unit: "%", format: "%.3f", step: 0.01)
            } header: {
                Text("原廠規格")
            } footer: {
                Text("填了之後，分析頁會直接標出你的盤超規格多少。手冊上通常寫成 WRMS 或 DIN。")
            }

            Section {
                optionalNumber("馬達皮帶輪直徑", value: $profile.pulleyDiameterMM,
                               unit: "mm", format: "%.2f", step: 0.1)
                optionalNumber("皮帶接觸的盤面直徑", value: $profile.platterDiameterMM,
                               unit: "mm", format: "%.1f", step: 0.5)
                optionalNumber("皮帶厚度", value: $profile.beltThicknessMM,
                               unit: "mm", format: "%.2f", step: 0.05)
                if let ratio = profile.expectedDriveRatio {
                    DiagnosticRow("預期傳動比", String(format: "%.2f", ratio), "×")
                }
            } header: {
                Text("傳動鏈尺寸（選填）")
            } footer: {
                Text("量了這幾個，頻譜上「非諧波的某某倍」就能對上實體零件 —— 剛好等於傳動比的那根峰就是馬達。皮帶跑在外盤緣就量外盤，跑在內盤就量內盤。")
            }

            Section {
                if profile.hasLoadTest {
                    DiagnosticRow("載重斜率",
                                  String(format: "%.5f", profile.loadSlopeRPMPerGram ?? 0),
                                  "RPM/g")
                    DiagnosticRow("手機造成的變化",
                                  String(format: "%+.4f", profile.loadPhoneEffectRPM ?? 0), "RPM")
                    Text(profile.loadIsSignificant
                         ? "手機的重量確實會影響讀數。"
                         : "這台盤對載重不敏感，手機的重量不影響讀數。")
                        .font(.footnote)
                        .foregroundStyle(profile.loadIsSignificant ? .orange : .secondary)
                }
                Button(profile.hasLoadTest ? "重做載重測試" : "做載重測試") {
                    showingLoadTest = true
                }
                .controlSize(.large)
            } header: {
                Text("載重")
            } footer: {
                Text("手機的重量會不會把唱盤拖慢，完全取決於馬達型式，查表沒有用，要實測。")
            }

            Section("備註") {
                TextField("例如：2026 換過皮帶、速度微調在底板右側", text: $profile.note,
                          axis: .vertical)
                    .lineLimit(1 ... 5)
            }
        }
        .navigationTitle(profile.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingLoadTest) { LoadTestView(profile: profile) }
    }

    /// 可以留白的數值欄位。留白代表「還沒量」，跟「量到 0」是不同的意思。
    private func optionalNumber(_ title: LocalizedStringKey, value: Binding<Double?>,
                                unit: String, format: String, step: Double) -> some View {
        HStack {
            Text(title)
            Spacer()
            if let v = value.wrappedValue {
                Text(String(format: format, v) + " " + unit)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                Stepper("", value: Binding(get: { v }, set: { value.wrappedValue = $0 }),
                        in: 0 ... 10000, step: step)
                    .labelsHidden()
                Button { value.wrappedValue = nil } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
            } else {
                Button("填寫") { value.wrappedValue = step * 10 }
                    .font(.footnote)
            }
        }
    }
}
