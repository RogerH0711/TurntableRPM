import SwiftUI
import Combine
import TurntableCore

/// 主畫面。
///
/// 排版順序反映的是「哪個數字可信」：轉速讀數在最上面，緊接著是碼錶校準
/// —— **那是唯一可信的校準來源**。兩條失敗的自動校準路徑收進「進階診斷」，
/// 它們對疑難排解仍然有用，但不該跟主要讀數搶版面。
struct LiveMeasurementView: View {
    @StateObject private var engine = MotionEngine()
    @StateObject private var store = CalibrationStore()
    @Environment(\.modelContext) private var context
    @State private var showingCalibrationSheet = false
    @State private var showingAbout = false
    @State private var showingDial = false
    @AppStorage("dialShowsElapsed") private var dialShowsElapsed = false
    @AppStorage("dialShowsRevolutions") private var dialShowsRevolutions = false
    /// 停止後畫面凍結幾秒。0 = 不凍結。規格 §6.3 的預設值。
    @AppStorage("freezeSeconds") private var freezeSeconds = 15
    /// 凍結的截止時刻。非 nil 代表正在凍結。
    @State private var freezeUntil: Date?
    @State private var now = Date()
    /// 量測畫面的轉向。跨次保留 —— 使用者站的位置通常不變，
    /// 上次調好的角度下次多半還是對的。
    @AppStorage("dialRotationOffset") private var dialRotationOffset = 0.0
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var showingOnboarding = false

    private var freezeRemaining: Int? {
        guard let until = freezeUntil else { return nil }
        return max(0, Int(until.timeIntervalSince(now).rounded(.up)))
    }

    private func dismissDial() {
        freezeUntil = nil
        showingDial = false
    }

    /// 有沒有可以拿來校準或匯出的量測結果。
    private var hasMeasurement: Bool { (engine.snapshot.rawMeanRPM ?? 0) > 0 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if case .unavailable = engine.availability {
                        unavailableBanner
                    }
                    speedReadout
                    modePicker
                    controlButton
                    if !engine.isRunning && !hasMeasurement { safetyBanner }
                    // 量測結果放在最上面 —— 那是使用者每次打開 app 要看的東西。
                    // 校準是設定性質的，一台裝置做一次就好，放下面。
                    if hasMeasurement { summaryPanel }
                    analysisLink
                    exportButton
                    historyLink
                    profileLink
                    stopwatchPanel
                    advancedLink
                }
                .padding()
            }
            .navigationTitle("轉速量測")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("說明", systemImage: "info.circle") { showingAbout = true }
                        Button("重看操作導覽", systemImage: "book") { showingOnboarding = true }
                    } label: {
                        Image(systemName: "info.circle")
                    }
                    .accessibilityLabel("說明")
                }
            }
            .onAppear { engine.calibrationFactor = store.calibration?.factor }
            .onChange(of: store.calibration) { _, new in
                engine.calibrationFactor = new?.factor
            }
            .sheet(isPresented: $showingCalibrationSheet) {
                // 用分析算出來的平均（已切掉加速段），不是整段的原始平均。
                // 拿含加速的資料去校準，k 會被永久寫錯。
                StopwatchCalibrationSheet(
                    measuredRPM: engine.analysis?.meanRPM ?? engine.snapshot.rawMeanRPM ?? 0
                ) { store.save($0) }
            }
            .sheet(isPresented: $showingAbout) { AboutView() }
            .sheet(isPresented: $showingOnboarding) {
                OnboardingView().onDisappear { hasSeenOnboarding = true }
            }
            .task {
                // 第一次開啟就顯示。放 .task 而不是 .onAppear —— onAppear 在
                // sheet 關閉時也會再觸發一次。
                if !hasSeenOnboarding { showingOnboarding = true }
            }
            // 反旋轉盤面。全螢幕，因為它要佔滿內接圓。
            .fullScreenCover(isPresented: $showingDial) {
                SpinningDialView(engine: engine,
                                 showsElapsed: dialShowsElapsed,
                                 showsRevolutions: dialShowsRevolutions,
                                 isFrozen: freezeUntil != nil,
                                 freezeRemaining: freezeRemaining,
                                 rotationOffset: $dialRotationOffset,
                                 onStop: { engine.stop() },
                                 onDismiss: dismissDial,
                                 onResume: {
                                     freezeUntil = nil
                                     engine.start()
                                 })
            }
            // 停止之後不要立刻收掉畫面 —— 手機還在盤上轉，使用者根本來不及看。
            // 凍結讀數，等他拿起手機（規格 §6.2）。
            .onChange(of: engine.isRunning) { _, running in
                guard !running, showingDial else { return }
                if freezeSeconds > 0 {
                    freezeUntil = Date().addingTimeInterval(Double(freezeSeconds))
                } else {
                    dismissDial()
                }
            }
            // 分析一完成就自動存進歷史。不做成手動按鈕 —— 使用者不會記得按，
            // 而歷史的價值在於「調整前後可以比較」，漏存一次就斷了。
            .onChange(of: engine.completedMeasurementID) { _, id in
                guard id != nil, let analysis = engine.analysis else { return }
                context.insert(MeasurementRecord(analysis: analysis,
                                                 snapshot: engine.snapshot,
                                                 calibration: store.calibration?.factor))
            }
            // 只在凍結倒數時才需要每 0.5 秒更新。原本是無條件訂閱，
            // 等於整個主畫面每 0.5 秒被重繪一次，App 開著就一直在做白工。
            .onReceive(freezeUntil == nil
                       ? Empty<Date, Never>().eraseToAnyPublisher()
                       : Timer.publish(every: 0.5, on: .main, in: .common)
                           .autoconnect().eraseToAnyPublisher()) { t in
                now = t
                if let until = freezeUntil, t >= until { dismissDial() }
            }
        }
    }

    // MARK: - 轉速讀數

    private var speedReadout: some View {
        VStack(spacing: 8) {
            // 沒有讀數時，72pt 的破折號會渲染成一條實心黑槓，看起來像畫面壞掉。
            // 改成淡色的佔位數字。
            let hasReading = engine.snapshot.instantRPM > 0
            Text(hasReading ? String(format: "%.3f", engine.snapshot.instantRPM) : "0.000")
                .font(.system(size: 72, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                .foregroundStyle(hasReading ? AnyShapeStyle(.primary) : AnyShapeStyle(.quaternary))

            HStack(spacing: 6) {
                Text("RPM")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                if engine.snapshot.appliedFactor != nil {
                    Label("已校準", systemImage: "checkmark.seal.fill")
                        .font(.footnote)
                        .foregroundStyle(.green)
                } else {
                    Label("未校準", systemImage: "exclamationmark.circle")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }

            if let nominal = engine.snapshot.nominal, let error = engine.snapshot.errorPercent {
                Text("\(nominal.label) 轉  \(error >= 0 ? "+" : "")\(String(format: "%.3f", error))%")
                    .font(.title3.weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(abs(error) <= 0.3 ? Color.green : Color.orange)
            } else if engine.isRunning {
                Text("轉速尚未穩定或不在標稱範圍內")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }

            Text(engine.statusMessage)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
        }
    }

    /// 手動 vs 自動。
    ///
    /// 自動模式解決的是「先按開始再放手機」的問題 —— 它會等轉速穩定才真正
    /// 開始記錄，盤面停下時自己結束。`StabilityGate` 是事後補救，這是事前避免。
    @ViewBuilder
    private var modePicker: some View {
        if !engine.isRunning {
            VStack(alignment: .leading, spacing: 6) {
                Picker("模式", selection: $engine.mode) {
                    ForEach(MotionEngine.Mode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                Text(engine.mode == .automatic
                     ? "按下按鈕後把手機放上轉盤，程式會等轉速穩定才開始記錄，盤面停下時自動結束。"
                     : "自己按開始與停止。記得先讓轉盤轉起來、手機放好，再按開始。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                DisclosureGroup("量測畫面顯示什麼") {
                    Toggle("經過時間", isOn: $dialShowsElapsed)
                    Toggle("累積圈數", isOn: $dialShowsRevolutions)
                    Stepper("停止後凍結 \(freezeSeconds) 秒",
                            value: $freezeSeconds, in: 0 ... 60, step: 5)
                    Text("量測結束時畫面會定住，讓你把手機從轉盤上拿起來再看。設 0 就是立刻關閉。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("量測畫面會反向旋轉，讓內容在轉動中看起來靜止。加的資訊愈多、字就得愈小，因為所有內容都必須落在螢幕的內接圓裡。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .font(.body)
                .padding(.top, 4)
            }
        }
    }

    /// 按鈕文案。自動模式按下去之後還要等轉速穩定，寫「開始量測」會讓人以為
    /// 已經在錄了 —— 使用者原本就會困惑自動模式到底要不要按這個鈕。
    private var controlLabel: String {
        if engine.isRunning {
            return engine.phase == .waitingForStability
                ? String(localized: "取消") : String(localized: "停止")
        }
        return engine.mode == .automatic
            ? String(localized: "準備好，開始偵測") : String(localized: "開始量測")
    }

    private var controlButton: some View {
        Button {
            if engine.isRunning {
                engine.stop()
            } else {
                engine.start()
                showingDial = true
            }
        } label: {
            Text(controlLabel)
                .font(.title3.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)          // 主要動作，做大一點
        }
        .buttonStyle(.borderedProminent)
        .tint(engine.isRunning ? .red : .accentColor)
    }

    // MARK: - 首次使用的安全提醒

    private var safetyBanner: some View {
        Button { showingAbout = true } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                VStack(alignment: .leading, spacing: 3) {
                    Text("放上唱盤之前")
                        .font(.body.weight(.medium))
                    Text("拿掉磁吸配件與含磁鐵的手機殼、鎖好唱臂、墊一張唱片再放手機。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            .measurementCard()
        }
        .buttonStyle(.plain)
    }

    // MARK: - 碼錶校準（唯一可信的校準來源）

    private var stopwatchPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("碼錶校準")
                    .font(.headline)
                Spacer()
                if let c = store.calibration {
                    Label(String(format: "k = %.5f", c.factor), systemImage: "checkmark.seal.fill")
                        .font(.body)
                        .monospacedDigit()
                        .foregroundStyle(.green)
                } else {
                    Text("未校準")
                        .font(.body)
                        .foregroundStyle(.orange)
                }
            }

            if let c = store.calibration {
                DiagnosticRow("陀螺儀偏差", String(format: "%+.3f", (1.0 / c.factor - 1.0) * 100), "%")
                DiagnosticRow("依據", String(localized: "\(c.revolutions) 圈 / \(String(format: "%.2f", c.seconds))"), "s")
                DiagnosticRow("校準精度", String(format: "±%.3f", c.precision() * 100), "%")
                DiagnosticRow("校準時間", c.recordedAt.formatted(date: .abbreviated, time: .shortened))
                Text("所有轉速讀數都已套用這個倍率。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else if store.mismatched != nil {
                Label(mismatchText, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            } else {
                Text("還沒校準。目前的「偏差 %」是唱盤誤差與陀螺儀誤差相乘的結果，兩者分不開，還不能拿來調唱盤。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            HStack {
                Button(store.calibration == nil ? "開始碼錶校準" : "重新校準") {
                    showingCalibrationSheet = true
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(engine.isRunning || !hasMeasurement)

                if store.calibration != nil {
                    Spacer()
                    Button("清除", role: .destructive) { store.clear() }
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                }
            }
            .padding(.top, 4)

            if !hasMeasurement {
                Text("要先完成一次量測，才有可以拿來比對的轉速。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .measurementCard()
    }

    /// k 綁定在特定一支陀螺儀上，換機或從備份還原之後不能沿用。
    private var mismatchText: String {
        let was = store.mismatched?.deviceModel ?? String(localized: "另一台裝置")
        let now = CalibrationStore.deviceModel
        return String(localized: "先前的校準是在 \(was) 上做的，這台是 \(now) —— 校準倍率綁定在特定一支陀螺儀上，不能沿用。請重新校準。")
    }

    // MARK: - 量測摘要

    private var summaryPanel: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("這次量測")
                .font(.headline)
                .padding(.bottom, 8)

            DiagnosticRow("平均轉速",
                          engine.snapshot.meanRPM.map { String(format: "%.4f", $0) } ?? "—", "RPM")
            DiagnosticRow("量測時間", formatted(engine.snapshot.elapsedSeconds, "%.1f"), "s")
            DiagnosticRow("累積圈數", "\(engine.snapshot.revolutions)")
            DiagnosticRow("樣本數", "\(engine.snapshot.sampleCount)")
            DiagnosticRow("取樣率", formatted(engine.snapshot.effectiveSampleRate, "%.1f"), "Hz")
        }
        .measurementCard()
    }

    // MARK: - 其他

    private var unavailableBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("讀不到動作感測器", systemImage: "exclamationmark.triangle.fill")
                .font(.headline)
            Text("模擬器沒有陀螺儀，這個 App 必須用實機測試。")
                .font(.body)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color.orange.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
    }

    /// 匯出整包原始資料。摘要數字診斷不了磁場問題，要看逐樣本的向量。
    @ViewBuilder
    private var exportButton: some View {
        if let url = engine.exportURL, !engine.isRunning {
            ShareLink(item: url) {
                Label("匯出原始資料（\(engine.snapshot.sampleCount) 筆）",
                      systemImage: "square.and.arrow.up")
                    .font(.body)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.bordered)
        }
    }

    /// 分析結果。這是這個 App 真正的價值所在 —— 平均轉速只說盤轉得快不快，
    /// 頻譜與極座標才說得出問題在哪個零件。
    @ViewBuilder
    private var analysisLink: some View {
        if let analysis = engine.analysis {
            NavigationLink {
                AnalysisView(analysis: analysis, nominal: engine.snapshot.nominal)
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Label("分析結果", systemImage: "waveform.path.ecg")
                            .font(.body.weight(.medium))
                        Text(String(format: String(localized: "抖晃率 %.3f%% WRMS  ·  每圈一次 %.3f%%"),
                                    analysis.wowFlutter.wrmsPercent,
                                    analysis.onePerRevolutionPercent))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.footnote)
                        .foregroundStyle(.tertiary)
                }
                .measurementCard()
            }
            .buttonStyle(.plain)
        } else if let reason = engine.analysisFailureReason {
            // 一定要有這個分支。少了它，分析失敗時畫面會永遠停在「分析中…」——
            // 而「轉盤沒轉就按停止」是很常見的操作。
            Label(reason, systemImage: "exclamationmark.triangle.fill")
                .font(.footnote)
                .foregroundStyle(.orange)
                .frame(maxWidth: .infinity, alignment: .leading)
                .measurementCard()
        } else if hasMeasurement && !engine.isRunning {
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("分析中…").font(.body).foregroundStyle(.secondary)
                Spacer()
            }
            .measurementCard()
        }
    }

    private var historyLink: some View {
        NavigationLink { HistoryView() } label: {
            HStack {
                Label("歷史記錄", systemImage: "clock.arrow.circlepath")
                    .font(.body)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            .measurementCard()
        }
        .buttonStyle(.plain)
    }

    private var profileLink: some View {
        NavigationLink { TurntableProfileListView() } label: {
            HStack {
                Label("唱盤設定", systemImage: "hifispeaker")
                    .font(.body)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            .measurementCard()
        }
        .buttonStyle(.plain)
    }

    private var advancedLink: some View {
        NavigationLink {
            AdvancedDiagnosticsView(engine: engine)
        } label: {
            HStack {
                Label("進階診斷", systemImage: "wrench.and.screwdriver")
                    .font(.body)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            .measurementCard()
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    LiveMeasurementView()
}
