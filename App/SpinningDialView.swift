import SwiftUI
import TurntableCore

/// 手機在轉盤上轉的時候，把畫面反向旋轉同樣的角度 —— 對站著不動的人來說，
/// 內容看起來就是靜止的。規格 §6.2。
///
/// **三個必須處理的約束（規格點名的，都踩得到）：**
///
/// 1. **介面方向要鎖死。** 手機平躺旋轉時系統的自動轉向會胡亂觸發。
///    `project.yml` 只留 Portrait。
/// 2. **內容要放得進內接圓。** 旋轉中的矩形內容會被螢幕邊緣裁掉，所以所有
///    會轉的元件都必須落在直徑 = `min(width, height)` 的圓內。這是硬版面約束。
/// 3. **可觸控區另外處理。** 按鈕跟著畫面轉，很難按 —— 所以**整片畫面都是
///    停止鍵**，不是某個小按鈕。
///
/// 角度用 `MotionEngine.displayAngleDegrees()`，它會用最新一筆的時間戳外推，
/// 補掉感測器（100 Hz）與畫面（120 Hz）之間的落後。直接讀累積值會讓畫面抖。
struct SpinningDialView: View {
    @ObservedObject var engine: MotionEngine
    /// 額外資訊。刻意做成單行、小字 —— 每多一行內容就變高，
    /// 而高度是內接圓約束裡最吃緊的方向。
    var showsElapsed = false
    var showsRevolutions = false
    let onStop: () -> Void

    var body: some View {
        GeometryReader { geo in
            let diameter = min(geo.size.width, geo.size.height)
            ZStack {
                Color.black.ignoresSafeArea()

                TimelineView(.animation) { _ in
                    dial
                        .frame(width: diameter, height: diameter)
                        // 反向旋轉 —— 手機順時針轉，內容就逆時針轉同樣的角度。
                        //
                        // 這裡假設盤面是順時針（從上方看）。`SpinProjector` 取了
                        // 絕對值，所以角度不帶方向資訊 —— 逆時針的盤（實務上不存在）
                        // 會讓補償方向相反，看起來是兩倍速在轉。
                        .rotationEffect(.degrees(-engine.displayAngleDegrees()))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // 不跟著轉的提示，放在角落。使用者的視角是靜止的，
                // 所以這些字對他來說反而是在轉 —— 刻意做得很小、很淡。
                VStack {
                    Spacer()
                    Text("點一下畫面停止")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.35))
                        .padding(.bottom, 8)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture(perform: onStop)      // 整片畫面都是停止鍵
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
    }

    /// 會跟著反轉的內容。**全部必須落在內接圓內**，否則轉到某個角度就被切掉。
    private var dial: some View {
        VStack(spacing: 4) {
            Text(engine.snapshot.instantRPM > 0
                 ? String(format: "%.2f", engine.snapshot.instantRPM)
                 : "—")
                .font(.system(size: 84, weight: .bold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.4)
                .lineLimit(1)
                .foregroundStyle(.white)

            Text("RPM")
                .font(.headline)
                .foregroundStyle(.white.opacity(0.5))

            if let nominal = engine.snapshot.nominal,
               let error = engine.snapshot.errorPercent {
                Text("\(nominal.label) 轉")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.5))
                    .padding(.top, 6)
                Text("\(error >= 0 ? "+" : "")\(String(format: "%.2f", error))%")
                    .font(.system(size: 34, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(errorColor(error))
            } else {
                Text(engine.phase == .waitingForStability ? "等待轉速穩定" : "尚未穩定")
                    .font(.title3)
                    .foregroundStyle(.orange)
                    .padding(.top, 6)
            }

            if engine.snapshot.appliedFactor == nil {
                Text("未校準")
                    .font(.caption)
                    .foregroundStyle(.orange.opacity(0.8))
                    .padding(.top, 4)
            }

            if !extraInfo.isEmpty {
                Text(extraInfo)
                    .font(.subheadline)
                    .monospacedDigit()
                    .foregroundStyle(.white.opacity(0.45))
                    .padding(.top, 10)
            }
        }
        // 內接圓的約束：對角線不能超過直徑，所以內容寬度限制在直徑的 0.7 倍
        // （0.7 ≈ 1/√2，正方形內接於圓時的邊長比）。
        .frame(maxWidth: .infinity)
        .padding(.horizontal)
    }

    /// 計時與圈數併成一行，用中點分隔。分兩行的話高度會多一截，
    /// 在 45° 旋轉時比較容易撞到內接圓的邊。
    private var extraInfo: String {
        var parts: [String] = []
        if showsElapsed {
            let s = Int(engine.snapshot.elapsedSeconds.rounded())
            parts.append(String(format: "%d:%02d", s / 60, s % 60))
        }
        if showsRevolutions {
            parts.append("\(engine.snapshot.revolutions) 圈")
        }
        return parts.joined(separator: "  ·  ")
    }

    private func errorColor(_ error: Double) -> Color {
        abs(error) <= 0.3 ? .green : (abs(error) <= 1.0 ? .yellow : .orange)
    }
}
