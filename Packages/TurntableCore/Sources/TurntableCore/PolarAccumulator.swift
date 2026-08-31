import Foundation

public struct PolarBin: Sendable {
    public var meanDeviation: Double
    public var count: Int
}

/// 規格 §3.6：把偏差依轉盤角度分箱平均。
///
/// 每格的樣本數是 fs × T ÷ 格數，與轉速無關 —— 60 秒 100 Hz、72 格時
/// 每格恆為 83 筆，16⅔ 或 78 轉都一樣。
public struct PolarAccumulator: Sendable {
    public let binCount: Int
    private var sums: [Double]
    private var counts: [Int]

    public init(binCount: Int = 72) {
        let n = max(1, binCount)
        self.binCount = n
        self.sums = [Double](repeating: 0, count: n)
        self.counts = [Int](repeating: 0, count: n)
    }

    public var binWidthDegrees: Double { 360.0 / Double(binCount) }

    public mutating func add(angleDegrees: Double, deviationPercent: Double) {
        var a = angleDegrees.truncatingRemainder(dividingBy: 360.0)
        if a < 0 { a += 360.0 }
        var index = Int(a / binWidthDegrees)
        if index >= binCount { index = binCount - 1 }
        sums[index] += deviationPercent
        counts[index] += 1
    }

    public var bins: [PolarBin] {
        (0 ..< binCount).map { i in
            PolarBin(meanDeviation: counts[i] > 0 ? sums[i] / Double(counts[i]) : 0,
                     count: counts[i])
        }
    }

    /// 最大偏差所在的箱中心角度。
    public var peakAngleDegrees: Double? {
        let all = bins
        let populated = all.indices.filter { all[$0].count > 0 }
        guard let peak = populated.max(by: { all[$0].meanDeviation < all[$1].meanDeviation }) else { return nil }
        return (Double(peak) + 0.5) * binWidthDegrees
    }

    /// 建議的色階上下限：±2 × 加權 WRMS。手動鎖定才能跨次量測比較顏色。
    public static func suggestedColorScale(wrmsPercent: Double) -> Double {
        max(2.0 * wrmsPercent, 0.01)
    }
}
