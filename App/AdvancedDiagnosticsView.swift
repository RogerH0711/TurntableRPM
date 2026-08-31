import SwiftUI
import TurntableCore

/// 進階診斷 —— 開發與疑難排解用，不是給一般使用者看的。
///
/// 這裡有兩條**已經證實失敗**的自動校準路徑。它們留著是因為對診斷仍然有用
/// （例如判斷盤面附近有沒有強磁場），但畫面上必須明確標示不可採信 ——
/// 曾經有一版對著一個「無法區分對錯」的倍率說「可以參考了」。
struct AdvancedDiagnosticsView: View {
    @ObservedObject var engine: MotionEngine

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                explainer
                sensorPanel
                fusedCalibrationPanel
                refinedPanel
                rawMagneticPanel
            }
            .padding()
        }
        .navigationTitle("進階診斷")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var explainer: some View {
        // 一定要是單一字串字面值 —— Text 的 markdown 只在字面值上生效，
        // 用 + 串接的話 ** 會原樣印出來。
        Text("下面兩區是**已經證實失敗**的自動校準嘗試，留著只為診斷用途。唯一可信的校準是主畫面的碼錶校準。")
            .font(.footnote)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - 感測器原始讀數

    private var sensorPanel: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("感測器")
                .font(.headline)
                .padding(.bottom, 8)

            DiagnosticRow("瞬時角速度", formatted(engine.snapshot.latestOmega, "%.3f"), "°/s")
            DiagnosticRow("平均轉速（已校準）",
                          engine.snapshot.meanRPM.map { String(format: "%.4f", $0) } ?? "—", "RPM")
            DiagnosticRow("平均轉速（未修正）",
                          engine.snapshot.rawMeanRPM.map { String(format: "%.4f", $0) } ?? "—", "RPM")
            DiagnosticRow("實際取樣率", formatted(engine.snapshot.effectiveSampleRate, "%.1f"),
                          String(localized: "Hz  (目標 \(Int(engine.targetSampleRate)))"))
            DiagnosticRow("圈內相位", formatted(engine.snapshot.phaseDegrees, "%.1f"), "°")
            DiagnosticRow("陀螺儀總轉角", formatted(engine.snapshot.gyroTotalDegrees, "%.0f"), "°")
            DiagnosticRow("磁北總轉角", formatted(engine.snapshot.magneticTotalDegrees, "%.0f"), "°")
            DiagnosticRow("磁北 yaw",
                          engine.snapshot.latestYawDegrees.map { String(format: "%.1f", $0) } ?? String(localized: "不可用"), "°")

            if let g = engine.snapshot.latestGravity {
                DiagnosticRow("重力向量", String(format: "%.3f, %.3f, %.3f", g.x, g.y, g.z), "g")
            }
            if let r = engine.snapshot.latestRotationRate {
                DiagnosticRow("原始角速度", String(format: "%.3f, %.3f, %.3f", r.x, r.y, r.z), "rad/s")
            }
            if let f = engine.snapshot.latestField {
                DiagnosticRow("原始磁場", String(format: "%.1f, %.1f, %.1f", f.x, f.y, f.z), "µT")
            }
            DiagnosticRow("磁力計樣本數", "\(engine.snapshot.rawMagneticSampleCount)")
        }
        .measurementCard()
    }

    // MARK: - 融合路徑（attitude.yaw）—— 已證實是同義反覆

    private var fusedCalibrationPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("融合路徑校準（attitude.yaw）")
                    .font(.headline)
                Spacer()
                Text("\(engine.snapshot.revolutions) 圈")
                    .font(.body)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            if let k = engine.snapshot.calibrationEstimate {
                DiagnosticRow("倍率 k", String(format: "%.5f", k))
                DiagnosticRow("陀螺儀偏差", String(format: "%+.3f", (1.0 / k - 1.0) * 100), "%")
                if let corrected = engine.snapshot.correctedMeanRPM,
                   let error = engine.snapshot.correctedErrorPercent {
                    DiagnosticRow("校準後轉速", String(format: "%.4f", corrected), "RPM")
                    DiagnosticRow("校準後誤差", String(format: "%+.3f", error), "%")
                }
            } else {
                Text("還沒滿一圈")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }

            confidenceFooter
        }
        .measurementCard()
    }

    @ViewBuilder
    private var confidenceFooter: some View {
        switch engine.snapshot.confidence {
        case .insufficient:
            Text("還不滿一圈，算不出倍率。")
                .font(.footnote)
                .foregroundStyle(.secondary)

        case .indistinguishable(let divergence, let floor):
            Label(indistinguishableText(divergence, floor),
                  systemImage: "exclamationmark.triangle.fill")
                .font(.footnote)
                .foregroundStyle(.orange)

        case .usable(let precision):
            Label(usableText(precision), systemImage: "checkmark.circle.fill")
                .font(.footnote)
                .foregroundStyle(.green)
        }
    }

    private func usableText(_ precision: Double) -> String {
        let value = String(format: "%.3f", precision * 100)
        return String(localized: "兩條路徑確實分歧了，這個倍率可以採信。精度 ±\(value)%")
    }

    private func indistinguishableText(_ divergence: Double, _ floor: Double) -> String {
        let d = String(format: "%.0f", divergence)
        let f = String(format: "%.0f", floor)
        return String(localized: "這個倍率不可採信。磁北與陀螺儀兩條路徑只差 \(d)°（雜訊底線 \(f)°）——「陀螺儀很準」和「yaw 根本就是陀螺儀積分」這兩件事無法區分。")
    }

    // MARK: - 扣掉圓心偏移後

    private var refinedPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("扣掉圓心偏移後")
                    .font(.headline)
                Spacer()
                Text("\(engine.snapshot.refined?.revolutions ?? 0) 圈")
                    .font(.body)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            if let refined = engine.snapshot.refined {
                DiagnosticRow("地磁總轉角", String(format: "%.0f", refined.totalDegrees), "°")
                DiagnosticRow("擬合半徑（地磁）", String(format: "%.1f", refined.radius), "µT")
                DiagnosticRow("擬合圓心偏移", String(format: "%.1f", refined.centerOffset), "µT")
                DiagnosticRow("擬合殘差", String(format: "%.2f", refined.residual), "µT")

                if let k = engine.snapshot.refinedCalibration {
                    Divider().padding(.vertical, 2)
                    DiagnosticRow("倍率 k", String(format: "%.5f", k))
                    DiagnosticRow("陀螺儀偏差", String(format: "%+.3f", (1.0 / k - 1.0) * 100), "%")
                    if let corrected = engine.snapshot.refinedCorrectedMeanRPM,
                       let error = engine.snapshot.refinedCorrectedErrorPercent {
                        DiagnosticRow("校準後轉速", String(format: "%.4f", corrected), "RPM")
                        DiagnosticRow("校準後誤差", String(format: "%+.3f", error), "%")
                    }
                }

                Text(refined.isTrustworthy ? refinedOKText : refinedBadText)
                    .font(.footnote)
                    .foregroundStyle(refined.isTrustworthy ? Color.secondary : Color.orange)
            } else {
                Text("樣本還不夠擬合圓心。")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
        }
        .measurementCard()
    }

    private var refinedOKText: String {
        String(localized: "擬合殘差遠小於半徑，這個圓是可信的。")
    }

    private var refinedBadText: String {
        String(localized: "擬合殘差偏大 —— 軌跡不是一個穩定的圓。實測上這通常是每圈一次的空間磁場失真（房間裡的靜止磁源 + 磁力計離轉軸有距離），失真振幅可以大過訊號本身。這次的倍率不要用。")
    }

    // MARK: - 原始磁力計

    private var rawMagneticPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("獨立校準（原始磁力計）")
                    .font(.headline)
                Spacer()
                Text("\(engine.snapshot.rawMagneticRevolutions) 圈")
                    .font(.body)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }

            DiagnosticRow("地磁總轉角", formatted(engine.snapshot.rawMagneticTotalDegrees, "%.0f"), "°")
            DiagnosticRow("水平分量", formatted(engine.snapshot.rawMagneticHorizontal, "%.1f"), "µT")
            DiagnosticRow("水平分量範圍",
                          formatted(engine.snapshot.rawMagneticMinHorizontal, "%.1f") + " – "
                          + formatted(engine.snapshot.rawMagneticMaxHorizontal, "%.1f"), "µT")
            if let range = engine.snapshot.rawMagneticRange {
                // 圓包住原點才繞得起來。有繞圈 → 大的是半徑；沒繞 → 大的是圓心偏移。
                let winds = engine.snapshot.rawMagneticRevolutions >= 1
                DiagnosticRow("地磁水平分量",
                              String(format: "%.1f", winds ? range.larger : range.smaller), "µT")
                DiagnosticRow("本地磁場（跟著轉）",
                              String(format: "%.1f", winds ? range.smaller : range.larger), "µT")
            }
            DiagnosticRow("磁力計校準", engine.snapshot.fieldAccuracy)

            // 地磁繞不起來的時候 k 是垃圾（實測看過 0.04452 / −95.7%）。
            // 垃圾不要長得像結論。
            if rawPathIsTracking, let k = engine.snapshot.rawCalibrationEstimate {
                DiagnosticRow("倍率 k", String(format: "%.5f", k))
                DiagnosticRow("陀螺儀偏差", String(format: "%+.3f", (1.0 / k - 1.0) * 100), "%")
            } else {
                Text(engine.snapshot.revolutions >= 3
                     ? "地磁沒有跟著盤面繞圈，這條路徑目前算不出有意義的倍率。"
                     : "還沒滿一圈")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }

            if let warning = magnetWarning {
                Label(warning, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
        }
        .measurementCard()
    }

    /// 地磁圈數有沒有跟上盤面圈數。差太多就代表這條路徑沒在追蹤，
    /// 算出來的 k 沒有意義，不能顯示。
    private var rawPathIsTracking: Bool {
        let spins = engine.snapshot.revolutions
        guard spins >= 1 else { return engine.snapshot.rawMagneticRevolutions >= 1 }
        return Double(engine.snapshot.rawMagneticRevolutions) / Double(spins) > 0.8
    }

    /// 盤面明明在轉、地磁卻繞不起來 —— 代表有塊磁鐵跟著手機一起轉，
    /// 把地磁圓的圓心推到半徑之外。把兩個量值直接報出來，不用回去自己推。
    private var magnetWarning: String? {
        guard let range = engine.snapshot.rawMagneticRange,
              engine.snapshot.rawMagneticRevolutions < 1,
              engine.snapshot.revolutions >= 3 else { return nil }
        let local = String(format: "%.0f", range.larger)
        let earth = String(format: "%.0f", range.smaller)
        let turns = engine.snapshot.revolutions
        return String(localized: "盤面轉了 \(turns) 圈，地磁卻繞不起來：本地磁場 \(local) µT 蓋過地磁的 \(earth) µT，圓心被推出半徑之外。最常見的來源是手機殼裡的 MagSafe 磁鐵環。")
    }
}
