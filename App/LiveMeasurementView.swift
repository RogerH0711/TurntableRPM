import SwiftUI
import TurntableCore

/// M1 的最小可驗證畫面：把感測器讀到的東西原原本本攤開來。
///
/// 這一版刻意樸素 —— 目的是把手機放上唱盤，確認演算法在真實感測器上成立。
/// 反旋轉盤面、精度圓環、停止凍結那些是 M2 的事。
struct LiveMeasurementView: View {
    @StateObject private var engine = MotionEngine()
    @StateObject private var store = CalibrationStore()
    @State private var showingCalibrationSheet = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    if case .unavailable = engine.availability {
                        unavailableBanner
                    }
                    speedReadout
                    controlButton
                    exportButton
                    stopwatchPanel
                    calibrationPanel
                    refinedPanel
                    rawMagneticPanel
                    diagnostics
                }
                .padding()
            }
            .navigationTitle("轉速量測")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { engine.calibrationFactor = store.calibration?.factor }
            .onChange(of: store.calibration) { _, new in
                engine.calibrationFactor = new?.factor
            }
            .sheet(isPresented: $showingCalibrationSheet) {
                StopwatchCalibrationSheet(
                    measuredRPM: engine.snapshot.rawMeanRPM ?? 0
                ) { store.save($0) }
            }
        }
    }

    /// 碼錶校準 —— 目前唯一可信的校準來源。
    ///
    /// 指南針自動校準兩條路都失敗了：`attitude.yaw` 跟陀螺儀是同義反覆，
    /// 原始磁力計被每圈一次的空間磁場失真蓋掉（失真 29.9 µT > 訊號 26.0 µT）。
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
                row("陀螺儀偏差", String(format: "%+.3f", (1.0 / c.factor - 1.0) * 100), "%")
                row("依據", "\(c.revolutions) 圈 / " + String(format: "%.2f", c.seconds), "s")
                row("校準精度", String(format: "±%.3f", c.precision() * 100), "%")
                row("校準時間", c.recordedAt.formatted(date: .abbreviated, time: .shortened), "")
                Text("所有轉速讀數都已套用這個倍率。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let stale = store.mismatched {
                Label("先前的校準是在 \(stale.deviceModel) 上做的，這台是 "
                      + "\(CalibrationStore.deviceModel) —— k 綁定在特定一支陀螺儀上，"
                      + "不能沿用。請重新校準。",
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
            } else {
                Text("還沒校準。陀螺儀的比例因子誤差是乘性的、平均再久也消不掉（實測 1.8%），"
                     + "所以現在畫面上的「偏差 %」還不能拿來調唱盤。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack {
                Button(store.calibration == nil ? "開始碼錶校準" : "重新校準") {
                    showingCalibrationSheet = true
                }
                .buttonStyle(.bordered)
                .disabled(engine.isRunning || (engine.snapshot.rawMeanRPM ?? 0) <= 0)

                if store.calibration != nil {
                    Spacer()
                    Button("清除", role: .destructive) { store.clear() }
                        .buttonStyle(.bordered)
                }
            }
            .padding(.top, 4)

            if engine.snapshot.rawMeanRPM ?? 0 <= 0 {
                Text("要先完成一次量測，才有可以拿來比對的轉速。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

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

    private var speedReadout: some View {
        VStack(spacing: 8) {
            Text(engine.snapshot.instantRPM > 0
                 ? String(format: "%.3f", engine.snapshot.instantRPM)
                 : "—")
                .font(.system(size: 72, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)

            HStack(spacing: 6) {
                Text("RPM")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                if engine.snapshot.appliedFactor != nil {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.caption)
                        .foregroundStyle(.green)
                } else {
                    Text("未校準")
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

    /// 匯出整包原始資料。摘要數字診斷不了磁場問題，要看逐樣本的向量。
    @ViewBuilder
    private var exportButton: some View {
        if let url = engine.exportURL, !engine.isRunning {
            ShareLink(item: url) {
                Label("匯出原始資料（\(engine.snapshot.sampleCount) 筆）",
                      systemImage: "square.and.arrow.up")
                    .font(.subheadline.weight(.medium))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.bordered)
        }
    }

    /// 融合路徑（`attitude.yaw`）的倍率估計。
    ///
    /// 這一區的文案是修過的。舊版用 `revolutions >= 30` 當判準說「可以參考了」，
    /// 結果在 36 圈時對著一個已知錯 1.8% 的數字這樣講。現在改由
    /// `ScaleCalibrator.confidence` 判斷 —— 判準是兩條路徑有沒有真的分歧。
    private var calibrationPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("融合路徑校準（attitude.yaw）")
                    .font(.headline)
                Spacer()
                Text("\(engine.snapshot.revolutions) 圈")
                    .font(.subheadline)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            if let k = engine.snapshot.calibrationEstimate {
                row("倍率 k", String(format: "%.5f", k), "")
                row("陀螺儀偏差", String(format: "%+.3f", (1.0 / k - 1.0) * 100), "%")
                if let corrected = engine.snapshot.correctedMeanRPM,
                   let correctedError = engine.snapshot.correctedErrorPercent {
                    row("校準後轉速", String(format: "%.4f", corrected), "RPM")
                    row("校準後誤差", String(format: "%+.3f", correctedError), "%")
                }
            } else {
                Text("還沒滿一圈")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            confidenceFooter
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var confidenceFooter: some View {
        switch engine.snapshot.confidence {
        case .insufficient:
            Text("還不滿一圈，算不出倍率。")
                .font(.caption)
                .foregroundStyle(.secondary)

        case .indistinguishable(let divergence, let floor):
            Label {
                Text("這個倍率不可採信。磁北與陀螺儀兩條路徑只差 "
                     + String(format: "%.0f", divergence)
                     + "°（雜訊底線 " + String(format: "%.0f", floor)
                     + "°）——「陀螺儀很準」和「yaw 根本就是陀螺儀積分」這兩件事無法區分。"
                     + "改看下面那一區。")
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
            }
            .font(.caption)
            .foregroundStyle(.orange)

        case .usable(let precision):
            Label("兩條路徑確實分歧了，這個倍率可以採信。精度 ±"
                  + String(format: "%.3f", precision * 100) + "%",
                  systemImage: "checkmark.circle.fill")
                .font(.caption)
                .foregroundStyle(.green)
        }
    }

    /// 扣掉圓心偏移之後的結果 —— 這是獨立校準真正的輸出。
    ///
    /// 手機自帶的磁鐵（MagSafe 環）或跟著轉的磁化物會把地磁圓的圓心推離原點，
    /// 推得夠遠就繞不起來。但圓心是裝置座標系裡的固定向量，盤面轉過幾圈之後
    /// 資料已經把整個圓掃了很多遍，直接擬合出來減掉即可。見 `refined()`。
    private var refinedPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("扣掉圓心偏移後")
                    .font(.headline)
                Spacer()
                Text("\(engine.snapshot.refined?.revolutions ?? 0) 圈")
                    .font(.subheadline)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            if let refined = engine.snapshot.refined {
                row("地磁總轉角", String(format: "%.0f", refined.totalDegrees), "°")
                row("擬合半徑（地磁）", String(format: "%.1f", refined.radius), "µT")
                row("擬合圓心偏移", String(format: "%.1f", refined.centerOffset), "µT")
                row("擬合殘差", String(format: "%.2f", refined.residual), "µT")

                if let k = engine.snapshot.refinedCalibration {
                    Divider().padding(.vertical, 2)
                    row("倍率 k", String(format: "%.5f", k), "")
                    row("陀螺儀偏差", String(format: "%+.3f", (1.0 / k - 1.0) * 100), "%")
                    if let corrected = engine.snapshot.refinedCorrectedMeanRPM,
                       let error = engine.snapshot.refinedCorrectedErrorPercent {
                        row("校準後轉速", String(format: "%.4f", corrected), "RPM")
                        row("校準後誤差", String(format: "%+.3f", error), "%")
                    }
                }

                Text(refined.isTrustworthy
                     ? "擬合殘差遠小於半徑，這個圓是可信的。碼錶量到的真值是 0.99915。"
                     : "擬合殘差偏大 —— 圓心在量測過程中變動了（有人走過、附近馬達啟停），"
                       + "或者地磁半徑小到跟雜訊同級。這次的倍率不要用。")
                    .font(.caption)
                    .foregroundStyle(refined.isTrustworthy ? Color.secondary : Color.orange)
            } else {
                Text("樣本還不夠擬合圓心。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    /// 繞過融合器的獨立路徑。這一區才是判定 M3 救不救得回來的地方。
    private var rawMagneticPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("獨立校準（原始磁力計）")
                    .font(.headline)
                Spacer()
                Text("\(engine.snapshot.rawMagneticRevolutions) 圈")
                    .font(.subheadline)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            row("地磁總轉角", format(engine.snapshot.rawMagneticTotalDegrees, "%.0f"), "°")
            row("水平分量", format(engine.snapshot.rawMagneticHorizontal, "%.1f"), "µT")
            row("水平分量範圍",
                format(engine.snapshot.rawMagneticMinHorizontal, "%.1f") + " – "
                + format(engine.snapshot.rawMagneticMaxHorizontal, "%.1f"), "µT")
            if let range = engine.snapshot.rawMagneticRange {
                // 圓包住原點才繞得起來。有繞圈 → 大的是半徑；沒繞 → 大的是圓心偏移。
                let winds = engine.snapshot.rawMagneticRevolutions >= 1
                row("地磁水平分量",
                    String(format: "%.1f", winds ? range.larger : range.smaller), "µT")
                row("本地磁場（跟著轉）",
                    String(format: "%.1f", winds ? range.smaller : range.larger), "µT")
            }
            row("磁力計校準", engine.snapshot.fieldAccuracy, "")

            // 地磁繞不起來的時候 k 是垃圾（實測看過 0.04452 / −95.7%）。
            // 垃圾不要長得像結論 —— 跟融合路徑那個誤導文案是同一種病。
            if rawPathIsTracking, let k = engine.snapshot.rawCalibrationEstimate {
                row("倍率 k", String(format: "%.5f", k), "")
                row("陀螺儀偏差", String(format: "%+.3f", (1.0 / k - 1.0) * 100), "%")
                if let corrected = engine.snapshot.rawCorrectedMeanRPM,
                   let correctedError = engine.snapshot.rawCorrectedErrorPercent {
                    row("校準後轉速", String(format: "%.4f", corrected), "RPM")
                    row("校準後誤差", String(format: "%+.3f", correctedError), "%")
                }
            } else {
                Text(engine.snapshot.revolutions >= 3
                     ? "地磁沒有跟著盤面繞圈，這條路徑目前算不出有意義的倍率。"
                     : "還沒滿一圈")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            if let verdict {
                Label(verdict.text, systemImage: "flask.fill")
                    .font(.caption)
                    .foregroundStyle(verdict.color)
            } else if let warning = magnetWarning {
                Label(warning, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
            } else {
                Text("這條路徑不碰 attitude.yaw，直接數地磁向量在裝置座標系裡轉了幾圈，"
                     + "所以跟陀螺儀的比例因子完全無關。碼錶量到的真值是 0.99915。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    /// 地磁圈數有沒有跟上盤面圈數。差太多就代表這條路徑沒在追蹤，
    /// 算出來的 k 沒有意義，不能顯示。
    private var rawPathIsTracking: Bool {
        let spins = engine.snapshot.revolutions
        guard spins >= 1 else { return engine.snapshot.rawMagneticRevolutions >= 1 }
        let ratio = Double(engine.snapshot.rawMagneticRevolutions) / Double(spins)
        return ratio > 0.8
    }

    /// 盤面明明在轉、地磁卻繞不起來 —— 這代表有塊磁鐵跟著手機一起轉，
    /// 把地磁圓的圓心推到半徑之外。把兩個量值直接報出來，不用回去自己推。
    private var magnetWarning: String? {
        guard let range = engine.snapshot.rawMagneticRange,
              engine.snapshot.rawMagneticRevolutions < 1,
              engine.snapshot.revolutions >= 3 else { return nil }
        let local = String(format: "%.0f", range.larger)
        let earth = String(format: "%.0f", range.smaller)
        let spins = engine.snapshot.revolutions
        return "盤面轉了 \(spins) 圈，直接解捲繞不起來：本地磁場 \(local) µT 蓋過地磁的 "
             + "\(earth) µT，圓心被推出半徑之外。這是預期內的 —— 看上面「扣掉圓心偏移後」"
             + "那一區，圓心擬合掉之後就數得出來了。"
    }

    /// 實驗的結論直接寫在畫面上，不要讓人回去翻數字自己推。
    private var verdict: (text: String, color: Color)? {
        guard let fused = engine.snapshot.calibrationEstimate,
              let raw = engine.snapshot.rawCalibrationEstimate,
              engine.snapshot.rawMagneticRevolutions >= 10 else { return nil }
        let fusedOffset = abs(fused - 1.0) * 100
        let rawOffset = abs(raw - 1.0) * 100

        if rawOffset > 0.3 && fusedOffset < 0.1 {
            return ("原始磁力計測到 " + String(format: "%.2f", rawOffset)
                    + "% 的比例因子誤差，融合路徑完全沒測到 —— 融合器確實是元兇，這條路救得回來。",
                    .green)
        }
        if rawOffset < 0.1 && fusedOffset < 0.1 {
            return ("兩條路徑都貼在 1.0。原始磁場也沒測到誤差 —— 要嘛陀螺儀這次真的準，"
                    + "要嘛連原始磁場都被吃掉了。得對照碼錶的 0.99915 才能判。", .orange)
        }
        return nil
    }

    private var diagnostics: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("感測器診斷")
                .font(.headline)
                .padding(.bottom, 8)

            row("瞬時角速度", format(engine.snapshot.latestOmega, "%.3f"), "°/s")
            row("平均轉速（已校準）", engine.snapshot.meanRPM.map { String(format: "%.4f", $0) } ?? "—", "RPM")
            row("平均轉速（未修正）", engine.snapshot.rawMeanRPM.map { String(format: "%.4f", $0) } ?? "—", "RPM")
            row("樣本數", "\(engine.snapshot.sampleCount)", "")
            row("量測時間", format(engine.snapshot.elapsedSeconds, "%.1f"), "s")
            row("實際取樣率", format(engine.snapshot.effectiveSampleRate, "%.1f"),
                "Hz  (目標 \(Int(engine.targetSampleRate)))")
            row("累積圈數", "\(engine.snapshot.revolutions)", "")
            row("圈內相位", format(engine.snapshot.phaseDegrees, "%.1f"), "°")
            row("陀螺儀總轉角", format(engine.snapshot.gyroTotalDegrees, "%.0f"), "°")
            row("磁北總轉角", format(engine.snapshot.magneticTotalDegrees, "%.0f"), "°")
            row("磁北 yaw", engine.snapshot.latestYawDegrees.map { String(format: "%.1f", $0) } ?? "不可用", "°")

            if let g = engine.snapshot.latestGravity {
                row("重力向量", String(format: "%.3f, %.3f, %.3f", g.x, g.y, g.z), "g")
            }
            if let r = engine.snapshot.latestRotationRate {
                row("原始角速度", String(format: "%.3f, %.3f, %.3f", r.x, r.y, r.z), "rad/s")
            }
            if let f = engine.snapshot.latestField {
                row("原始磁場", String(format: "%.1f, %.1f, %.1f", f.x, f.y, f.z), "µT")
            }
            row("磁力計樣本數", "\(engine.snapshot.rawMagneticSampleCount)", "")
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private func row(_ label: String, _ value: String, _ unit: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value)
                .monospacedDigit()
            if !unit.isEmpty {
                Text(unit)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .font(.subheadline)
        .padding(.vertical, 4)
    }

    private func format(_ value: Double, _ spec: String) -> String {
        value.isFinite ? String(format: spec, value) : "—"
    }
}

#Preview {
    LiveMeasurementView()
}
