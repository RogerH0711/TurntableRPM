import Foundation

/// 規格 §3.3：顯示路徑用的移動平均。
///
/// 這是低通濾波器，N 個樣本在 fs/N 有第一個零點。N=25（0.25 s）的零點正好落在
/// 加權曲線的 4 Hz 峰值上，所以抖晃率**絕對不能**算在平滑後的訊號上。
public enum MovingAverage {

    /// 中心對齊移動平均，邊界以可用樣本數平均。
    public static func apply(_ x: [Double], window: Int) -> [Double] {
        guard window > 1, !x.isEmpty else { return x }
        var prefix = [Double](repeating: 0, count: x.count + 1)
        for i in 0 ..< x.count { prefix[i + 1] = prefix[i] + x[i] }
        let half = window / 2
        var out = [Double](repeating: 0, count: x.count)
        for i in 0 ..< x.count {
            let lo = max(0, i - half)
            let hi = min(x.count, i - half + window)
            out[i] = (prefix[hi] - prefix[lo]) / Double(hi - lo)
        }
        return out
    }

    /// 振幅響應 |H(f)|。
    public static func magnitudeResponse(frequency: Double, window: Int, sampleRate: Double) -> Double {
        guard window > 1 else { return 1 }
        if frequency == 0 { return 1 }
        let denominator = Double(window) * sin(Double.pi * frequency / sampleRate)
        if abs(denominator) < 1e-15 { return 1 }
        return abs(sin(Double.pi * frequency * Double(window) / sampleRate) / denominator)
    }

    /// 第一個零點 —— 這個視窗完全砍掉的頻率。
    public static func firstNullFrequency(window: Int, sampleRate: Double) -> Double {
        sampleRate / Double(window)
    }

    /// −3 dB 截止頻率（近似係數 0.4429）。
    public static func cutoffFrequency(window: Int, sampleRate: Double) -> Double {
        0.4429 * sampleRate / Double(window)
    }
}
