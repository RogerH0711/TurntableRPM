import SwiftUI
import TurntableCore

/// 極座標熱圖：把偏差依「圈內角度」畫成一個環。
///
/// 這張圖回答的是「誤差集中在盤面的哪一段」。均勻散開代表隨機抖動；
/// 集中在某個角度代表**偏心** —— 盤面、主軸或皮帶接觸面沒對正。
///
/// 色階刻意用手動上下限（`scale`）而不是每次自動縮放：自動縮放會讓
/// 兩次量測的顏色無法比較，一個很乾淨的盤跟一個很糟的盤看起來會一樣紅。
struct PolarHeatmapView: View {
    let bins: [PolarBin]
    let scale: Double            // 色階上下限，± 這個值
    var peakAngleDegrees: Double?

    var body: some View {
        Canvas { ctx, size in
            guard !bins.isEmpty, scale > 0 else { return }
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let outer = min(size.width, size.height) / 2 * 0.94
            let inner = outer * 0.42
            let step = 360.0 / Double(bins.count)

            for (i, bin) in bins.enumerated() {
                // 0° 畫在正上方，順時針增加 —— 跟從上方看唱盤的方向一致。
                let a0 = Angle.degrees(Double(i) * step - 90)
                let a1 = Angle.degrees(Double(i + 1) * step - 90)
                var p = Path()
                p.addArc(center: c, radius: outer, startAngle: a0, endAngle: a1, clockwise: false)
                p.addArc(center: c, radius: inner, startAngle: a1, endAngle: a0, clockwise: true)
                p.closeSubpath()
                ctx.fill(p, with: .color(color(for: bin.meanDeviation)))
            }

            // 峰值角度的指針。
            if let peak = peakAngleDegrees {
                let a = (peak - 90) * .pi / 180
                var p = Path()
                p.move(to: CGPoint(x: c.x + cos(a) * inner * 0.82,
                                   y: c.y + sin(a) * inner * 0.82))
                p.addLine(to: CGPoint(x: c.x + cos(a) * outer * 1.0,
                                      y: c.y + sin(a) * outer * 1.0))
                ctx.stroke(p, with: .color(.primary), style: StrokeStyle(lineWidth: 2.5,
                                                                        lineCap: .round))
            }

            var hole = Path()
            hole.addEllipse(in: CGRect(x: c.x - inner, y: c.y - inner,
                                       width: inner * 2, height: inner * 2))
            ctx.fill(hole, with: .color(Color(.secondarySystemBackground)))
        }
        .aspectRatio(1, contentMode: .fit)
    }

    /// 藍（偏慢）→ 灰（準）→ 紅（偏快）。
    private func color(for deviation: Double) -> Color {
        let t = max(-1, min(1, deviation / scale))
        if t >= 0 {
            return Color(hue: 0.02, saturation: 0.75 * t, brightness: 0.55 + 0.35 * (1 - t))
        }
        return Color(hue: 0.58, saturation: 0.75 * (-t), brightness: 0.55 + 0.35 * (1 + t))
    }
}

/// 色階說明條。沒有這個，圖上的顏色不知道對應多少。
struct HeatmapLegend: View {
    let scale: Double

    var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geo in
                HStack(spacing: 0) {
                    ForEach(0 ..< 40, id: \.self) { i in
                        let t = Double(i) / 39 * 2 - 1
                        Rectangle().fill(color(t))
                            .frame(width: geo.size.width / 40)
                    }
                }
            }
            .frame(height: 10)
            .clipShape(RoundedRectangle(cornerRadius: 3))

            HStack {
                Text(String(format: "−%.2f%%", scale))
                Spacer()
                Text("準")
                Spacer()
                Text(String(format: "+%.2f%%", scale))
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
    }

    private func color(_ t: Double) -> Color {
        if t >= 0 {
            return Color(hue: 0.02, saturation: 0.75 * t, brightness: 0.55 + 0.35 * (1 - t))
        }
        return Color(hue: 0.58, saturation: 0.75 * (-t), brightness: 0.55 + 0.35 * (1 + t))
    }
}
