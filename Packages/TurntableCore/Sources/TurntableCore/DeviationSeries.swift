import Foundation

/// 規格 §3.3：瞬時偏差 d(t) = (ω(t) − ω̄) / ω̄ × 100%。
public enum DeviationSeries {
    public static func make(from omega: [Double]) -> (mean: Double, deviationPercent: [Double])? {
        guard !omega.isEmpty else { return nil }
        var sum = 0.0
        for v in omega { sum += v }
        let mean = sum / Double(omega.count)
        guard mean > 0 else { return nil }
        return (mean, omega.map { ($0 - mean) / mean * 100.0 })
    }

    /// 最大偏差。必須連同 windowSeconds 一起回報 —— 不標頻寬的最大偏差是無法比較的數字。
    public static func maxDeviation(_ deviationPercent: [Double],
                                    sampleRate: Double,
                                    smoothingWindow: Int) -> (value: Double, windowSeconds: Double) {
        let smoothed = MovingAverage.apply(deviationPercent, window: smoothingWindow)
        var peak = 0.0
        for v in smoothed where abs(v) > peak { peak = abs(v) }
        return (peak, Double(smoothingWindow) / sampleRate)
    }
}
