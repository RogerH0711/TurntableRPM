import Foundation

/// 規格 §3.4：IEC 386 / DIN 45507 加權曲線的閉式近似（三極高通 × 三極低通）。
///
/// 對 AES 公布的四個錨點做最小平方擬合，峰值落在 3.968 Hz，錨點最大誤差 3.5%：
/// 0.2 Hz→0.0292（標準 0.0296）、0.8 Hz→0.5176（0.500）、4 Hz→1.000、20 Hz→0.5081（0.508）。
public enum WowFlutterWeighting {
    public static let highPassCorner = 0.635    // Hz
    public static let lowPassCorner = 24.8      // Hz
    /// 未正規化曲線的峰值，出現在 3.9683751 Hz。
    public static let peakValue = 0.926957486998

    public static func weight(_ frequency: Double) -> Double {
        guard frequency > 0 else { return 0 }
        let a = frequency / highPassCorner
        let b = frequency / lowPassCorner
        let highPass = (a * a * a) / pow(1.0 + a * a, 1.5)
        let lowPass = 1.0 / pow(1.0 + b * b, 1.5)
        return highPass * lowPass / peakValue
    }
}
