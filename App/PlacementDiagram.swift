import SwiftUI

/// 手機該擺在轉盤哪裡的示意圖。
///
/// **這張圖解決兩個問題。**
///
/// 一是**方向**：反旋轉把內容鎖在「按下開始那一刻手機的物理方向」。規定一個固定
/// 擺法，那個方向就是已知的 —— 手機下緣朝著使用者，內容就會正著顯示。
///
/// 二是**配平**，這個是實測發現的：手機偏心放在盤上，會在軸承產生側向負載，
/// 拖慢轉速也放大每圈一次的抖動。對面放一個等重的東西之後，實測轉速 +0.313%、
/// 偏心 −37%、加權 W&F −21%，而且馬達變輕鬆（+0.195%）、皮帶少打滑（傳動比 −0.118%）
/// —— 兩個獨立的量都指向阻力變小。
///
/// **修正量測方法優於事後補償。** 這也是為什麼載重補償不該拿來處理這件事：
/// 兩點外插假設「只有質量在變」，而在這裡不平衡才是主導。
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
            let screen = phone.insetBy(dx: pw * 0.1, dy: ph * 0.05)
            ctx.fill(Path(roundedRect: screen, cornerRadius: pw * 0.1),
                     with: .color(Color(white: 0.16)))
            // 聽筒缺口畫在上緣，指出「上」在哪一邊
            let notch = CGRect(x: phone.midX - pw * 0.16, y: phone.minY + ph * 0.03,
                               width: pw * 0.32, height: ph * 0.018)
            ctx.fill(Path(roundedRect: notch, cornerRadius: 2), with: .color(Color(white: 0.55)))

            // 配重：放在對面，質心離轉軸的距離跟手機差不多
            let cwR = pw * 0.52
            let cwCenter = CGPoint(x: c.x + pw * 0.55, y: c.y)
            ctx.fill(Path(ellipseIn: CGRect(x: cwCenter.x - cwR, y: cwCenter.y - cwR,
                                            width: cwR * 2, height: cwR * 2)),
                     with: .color(Color(white: 0.72)))
            ctx.stroke(Path(ellipseIn: CGRect(x: cwCenter.x - cwR * 0.62,
                                              y: cwCenter.y - cwR * 0.62,
                                              width: cwR * 1.24, height: cwR * 1.24)),
                       with: .color(Color(white: 0.45)), lineWidth: 1.5)
            // 杯耳
            var handle = Path()
            handle.addArc(center: CGPoint(x: cwCenter.x + cwR, y: cwCenter.y),
                          radius: cwR * 0.34,
                          startAngle: .degrees(-95), endAngle: .degrees(95), clockwise: false)
            ctx.stroke(handle, with: .color(Color(white: 0.72)), lineWidth: 3)

            // 主軸畫在最後，壓在手機與配重之上
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)),
                     with: .color(.orange))

            // 使用者的方向
            var arrow = Path()
            arrow.move(to: CGPoint(x: c.x, y: c.y + r + 10))
            arrow.addLine(to: CGPoint(x: c.x, y: c.y + r + 30))
            ctx.stroke(arrow, with: .color(.orange),
                       style: StrokeStyle(lineWidth: 2, lineCap: .round))
            ctx.draw(Text("你").font(.footnote.weight(.semibold)).foregroundStyle(.orange),
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
                line("**對面放一個跟手機等重的東西**配平，距離轉軸差不多遠")
                line("螢幕朝上，放在轉盤原本的**絨布墊**上")
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text("配平很重要：手機偏在一邊會在軸承產生側向負載，實測會讓轉速慢 0.3%、每圈一次的抖動大三成。小馬克杯加水到跟手機等重就很好用。")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text("照這個擺法，量測畫面的文字也會正對著你。")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func line(_ text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("・").foregroundStyle(.secondary)
            Text(text)
        }
        .font(.body)
    }
}

#Preview { PlacementGuide().padding() }
