import Foundation

public struct WowFlutterResult: Sendable {
    /// 加權均方根，單位 %。
    public let wrmsPercent: Double
    /// DIN 2σ 峰值：加權後 |d(t)| 的第 95 百分位（有 5% 的時間超過它）。
    public let peak2SigmaPercent: Double
    /// 加權後的時域序列，供繪圖用。
    public let weightedSeries: [Double]

    /// 比值本身有診斷價值：約 1.96 是高斯型隨機抖動，約 1.41 是單頻正弦 wow。
    public var peakToRMSRatio: Double {
        wrmsPercent > 0 ? peak2SigmaPercent / wrmsPercent : 0
    }
}

/// 規格 §3.4：加權抖晃率。
///
/// 輸入必須是**未平滑**的偏差序列。把 WRMS 算在移動平均過的訊號上，
/// 等於把 4 Hz 加權峰值挖掉，得到的數字會漂亮得毫無意義。
public enum WowFlutterAnalyzer {

    public static func analyze(deviationPercent: [Double],
                               sampleRate: Double,
                               guardSeconds: Double = 2.0) -> WowFlutterResult? {
        guard deviationPercent.count > 16, sampleRate > 0 else { return nil }
        let weighted = applyWeighting(deviationPercent, sampleRate: sampleRate)

        let maxGuard = max(0, (weighted.count - 1) / 2)
        let guardCount = min(Int(guardSeconds * sampleRate), maxGuard)
        let core = Array(weighted[guardCount ..< (weighted.count - guardCount)])
        guard !core.isEmpty else { return nil }

        var sumSquares = 0.0
        for v in core { sumSquares += v * v }
        let rms = (sumSquares / Double(core.count)).squareRoot()

        var magnitudes = core.map { abs($0) }
        magnitudes.sort()
        let index = min(magnitudes.count - 1, Int((Double(magnitudes.count) - 1.0) * 0.95))

        return WowFlutterResult(wrmsPercent: rms,
                                peak2SigmaPercent: magnitudes[index],
                                weightedSeries: weighted)
    }

    /// 整段 FFT → 乘上 W(f) → IFFT。補零到 2 倍長度的 2 次冪，避免循環卷積把尾端繞回開頭。
    public static func applyWeighting(_ deviationPercent: [Double], sampleRate: Double) -> [Double] {
        let n = deviationPercent.count
        let size = FFT.nextPowerOfTwo(atLeast: n * 2)
        var real = [Double](repeating: 0, count: size)
        var imag = [Double](repeating: 0, count: size)
        for i in 0 ..< n { real[i] = deviationPercent[i] }

        FFT.transform(real: &real, imag: &imag, inverse: false)

        let df = sampleRate / Double(size)
        for k in 0 ... (size / 2) {
            let w = WowFlutterWeighting.weight(Double(k) * df)
            real[k] *= w
            imag[k] *= w
            let mirror = (size - k) % size
            if mirror != k {
                real[mirror] *= w
                imag[mirror] *= w
            }
        }

        FFT.transform(real: &real, imag: &imag, inverse: true)
        return Array(real[0 ..< n])
    }
}
