import SwiftUI

/// 手機該擺在轉盤哪裡的示意圖。
///
/// **這張圖不只是說明，它是方向問題的解法。** 反旋轉把內容鎖在「按下開始那一刻
/// 手機的物理方向」。只要規定一個固定擺法，那個方向就是已知的 —— 手機下緣朝著
/// 使用者，內容就會正著顯示，不需要手動轉。
///
/// 擺法的兩個理由：
/// - **長邊中點貼轉軸**：手機質心離軸約 38 mm。改用短邊貼會變成約 80 mm，
///   不平衡力矩加倍。
/// - **下緣朝使用者**：跟把手機平放在面前的桌上一樣，這樣字才是正的。
struct PlacementDiagram: View {
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let r = min(w, h * 0.78) / 2 * 0.92
            let c = CGPoint(x: w / 2, y: h * 0.42)

            // 轉盤
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r,
                                            width: r * 2, height: r * 2)),
                     with: .color(Color(white: 0.22)))
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r,
                                              width: r * 2, height: r * 2)),
                       with: .color(Color(white: 0.45)), lineWidth: 1.5)

            // 手機：長邊中點貼著轉軸，機身往左邊延伸
            let pw = r * 0.62, ph = r * 1.28
            let phone = CGRect(x: c.x - pw, y: c.y - ph / 2, width: pw, height: ph)
            ctx.fill(Path(roundedRect: phone, cornerRadius: pw * 0.16),
                     with: .color(Color(white: 0.92)))
            // 螢幕（示意內容朝下緣 = 朝使用者）
            let screen = phone.insetBy(dx: pw * 0.1, dy: ph * 0.05)
            ctx.fill(Path(roundedRect: screen, cornerRadius: pw * 0.1),
                     with: .color(Color(white: 0.16)))
            // 聽筒缺口畫在上緣，指出「上」在哪一邊
            let notch = CGRect(x: phone.midX - pw * 0.16, y: phone.minY + ph * 0.03,
                               width: pw * 0.32, height: ph * 0.018)
            ctx.fill(Path(roundedRect: notch, cornerRadius: 2), with: .color(Color(white: 0.55)))

            // 主軸
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)),
                     with: .color(.orange))

            // 使用者的方向
            var arrow = Path()
            arrow.move(to: CGPoint(x: c.x, y: c.y + r + 10))
            arrow.addLine(to: CGPoint(x: c.x, y: c.y + r + 30))
            ctx.stroke(arrow, with: .color(.orange),
                       style: StrokeStyle(lineWidth: 2, lineCap: .round))
            ctx.draw(Text("你").font(.caption.weight(.semibold)).foregroundStyle(.orange),
                     at: CGPoint(x: c.x, y: c.y + r + 44))
        }
    }
}

/// 圖 + 說明。導覽與說明頁共用。
struct PlacementGuide: View {
    var body: some View {
        VStack(spacing: 12) {
            PlacementDiagram()
                .frame(height: 230)

            VStack(alignment: .leading, spacing: 6) {
                line("手機**右側長邊的中點**貼著轉軸，機身放在左半邊")
                line("手機**下緣朝著你**（跟平放在桌上看一樣）")
                line("螢幕朝上，放在轉盤原本的**絨布墊**上")
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text("照這個擺法，量測畫面的文字就會正對著你。")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func line(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("・").foregroundStyle(.secondary)
            Text(.init(text))
        }
        .font(.subheadline)
    }
}

#Preview { PlacementGuide().padding() }
