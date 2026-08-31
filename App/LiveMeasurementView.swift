import SwiftUI
import TurntableCore

/// 主畫面。
///
/// 排版順序反映的是「哪個數字可信」：轉速讀數在最上面，緊接著是碼錶校準
/// —— **那是唯一可信的校準來源**。兩條失敗的自動校準路徑收進「進階診斷」，
/// 它們對疑難排解仍然有用，但不該跟主要讀數搶版面。
struct LiveMeasurementView: View {
    @StateObject private var engine = MotionEngine()
    @StateObject private var store = CalibrationStore()
    @State private var showingCalibrationSheet = false
    @State private var showingAbout = false

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
                    controlButton
                    if !engine.isRunning && !hasMeasurement { safetyBanner }
                    stopwatchPanel
                    if hasMeasurement { summaryPanel }
                    analysisLink
                    exportButton
                    advancedLink
                }
                .padding()
            }
            .navigationTitle("轉速量測")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingAbout = true } label: {
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
                StopwatchCalibrationSheet(
                    measuredRPM: engine.snapshot.rawMeanRPM ?? 0
                ) { store.save($0) }
            }
            .sheet(isPresented: $showingAbout) { AboutView() }
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
                        .font(.caption)
                        .foregroundStyle(.green)
                } else {
                    Label("未校準", systemImage: "exclamationmark.circle")
                        .font(.caption)
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
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Text(engine.statusMessage)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
        }
    }

    private var controlButton: some View {
        Button {
            engine.isRunning ? engine.stop() : engine.start()
        } label: {
            Text(engine.isRunning ? "停止" : "開始量測")
                .font(.title3.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
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
                        .font(.subheadline.weight(.medium))
                    Text("拿掉磁吸配件與含磁鐵的手機殼、鎖好唱臂、墊一張唱片再放手機。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption)
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
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(.green)
                } else {
                    Text("未校準")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                }
            }

            if let c = store.calibration {
                DiagnosticRow("陀螺儀偏差", String(format: "%+.3f", (1.0 / c.factor - 1.0) * 100), "%")
                DiagnosticRow("依據", "\(c.revolutions) 圈 / " + String(format: "%.2f", c.seconds), "s")
                DiagnosticRow("校準精度", String(format: "±%.3f", c.precision() * 100), "%")
                DiagnosticRow("校準時間", c.recordedAt.formatted(date: .abbreviated, time: .shortened))
                Text("所有轉速讀數都已套用這個倍率。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if store.mismatched != nil {
                Label(mismatchText, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
            } else {
                Text("還沒校準。目前的「偏差 %」是唱盤誤差與陀螺儀誤差相乘的結果，"
                     + "兩者分不開，還不能拿來調唱盤。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack {
                Button(store.calibration == nil ? "開始碼錶校準" : "重新校準") {
                    showingCalibrationSheet = true
                }
                .buttonStyle(.bordered)
                .disabled(engine.isRunning || !hasMeasurement)

                if store.calibration != nil {
                    Spacer()
                    Button("清除", role: .destructive) { store.clear() }
                        .buttonStyle(.bordered)
                }
            }
            .padding(.top, 4)

            if !hasMeasurement {
                Text("要先完成一次量測，才有可以拿來比對的轉速。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .measurementCard()
    }

    /// k 綁定在特定一支陀螺儀上，換機或從備份還原之後不能沿用。
    private var mismatchText: String {
        let was = store.mismatched?.deviceModel ?? "另一台裝置"
        return "先前的校準是在 \(was) 上做的，這台是 \(CalibrationStore.deviceModel)"
             + " —— 校準倍率綁定在特定一支陀螺儀上，不能沿用。請重新校準。"
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
                .font(.subheadline)
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
                    .font(.subheadline)
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
                            .font(.subheadline.weight(.medium))
                        Text(String(format: "抖晃率 %.3f%% WRMS  ·  每圈一次 %.3f%%",
                                    analysis.wowFlutter.wrmsPercent,
                                    analysis.onePerRevolutionPercent))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
                .measurementCard()
            }
            .buttonStyle(.plain)
        } else if hasMeasurement && !engine.isRunning {
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("分析中…").font(.subheadline).foregroundStyle(.secondary)
                Spacer()
            }
            .measurementCard()
        }
    }

    private var advancedLink: some View {
        NavigationLink {
            AdvancedDiagnosticsView(engine: engine)
        } label: {
            HStack {
                Label("進階診斷", systemImage: "wrench.and.screwdriver")
                    .font(.subheadline)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
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
