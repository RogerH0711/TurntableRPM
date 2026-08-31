import Foundation

/// 一個已知答案的抖晃成分。
public struct WowComponent: Sendable {
    public let amplitudePercent: Double
    public let frequencyHz: Double
    public let phaseRadians: Double

    public init(amplitudePercent: Double, frequencyHz: Double, phaseRadians: Double = 0) {
        self.amplitudePercent = amplitudePercent
        self.frequencyHz = frequencyHz
        self.phaseRadians = phaseRadians
    }
}

public struct SyntheticRun: Sendable {
    /// 陀螺儀「量到」的樣本（含比例因子誤差），也就是 app 實際會拿到的東西。
    public let samples: [SpinSample]
    /// 真值角速度，°/s。測試拿它算出應該得到的答案。
    public let trueOmega: [Double]
    /// 真值累積角度，度（恆為正，代表轉過的量）。
    public let trueAngleDegrees: [Double]
    public let rotationRates: [Vector3]
    public let gravities: [Vector3]
}

/// 規格 §8.1 的 M0 核心：可重現的合成訊號。
///
/// 在還沒有唱盤、也還沒上機之前，先用它把演算法測到對。之後上機讀到奇怪的數字時，
/// 才知道問題出在感測器而不是數學。
public enum SyntheticSignal {

    /// - Parameter reversedYaw: 產生**遞減**的 yaw，模擬從上方看順時針旋轉的唱盤。
    ///   真實唱盤就是這個方向 —— 早期版本只產生遞增的 yaw，讓一個轉向的 bug 溜過了整組測試。
    ///   凡是會累積或比較 yaw 的東西，兩個方向都要測。
    public static func make(nominalRPM: Double,
                            durationSeconds: Double,
                            sampleRate: Double = 100.0,
                            wow: [WowComponent] = [],
                            noisePercent: Double = 0,
                            scaleError: Double = 0,
                            tiltDegrees: Double = 0,
                            yawNoiseDegrees: Double = 0,
                            reversedYaw: Bool = false,
                            seed: UInt64 = 1) -> SyntheticRun {
        var rng = SplitMix64(seed: seed)
        let count = max(2, Int(durationSeconds * sampleRate))
        let omega0 = nominalRPM * 6.0
        let tilt = tiltDegrees * Double.pi / 180.0
        let gravity = Vector3(sin(tilt), 0, -cos(tilt))
        let yawSign = reversedYaw ? -1.0 : 1.0

        var samples: [SpinSample] = []
        var trueOmega = [Double](repeating: 0, count: count)
        var angle = [Double](repeating: 0, count: count)
        var rotationRates: [Vector3] = []
        var gravities: [Vector3] = []
        samples.reserveCapacity(count)
        rotationRates.reserveCapacity(count)
        gravities.reserveCapacity(count)

        for i in 0 ..< count {
            let t = Double(i) / sampleRate
            var deviation = 0.0
            for component in wow {
                deviation += (component.amplitudePercent / 100.0)
                    * sin(2.0 * Double.pi * component.frequencyHz * t + component.phaseRadians)
            }
            if noisePercent > 0 {
                deviation += rng.nextGaussian() * noisePercent / 100.0
            }
            trueOmega[i] = omega0 * (1.0 + deviation)
            if i > 0 {
                angle[i] = angle[i - 1] + (trueOmega[i] + trueOmega[i - 1]) / 2.0 / sampleRate
            }

            let measured = trueOmega[i] * (1.0 + scaleError)
            let radiansPerSecond = measured * Double.pi / 180.0
            rotationRates.append(Vector3(-gravity.x * radiansPerSecond,
                                         -gravity.y * radiansPerSecond,
                                         -gravity.z * radiansPerSecond))
            gravities.append(gravity)

            var yaw = yawSign * angle[i] * Double.pi / 180.0
            if yawNoiseDegrees > 0 {
                yaw += rng.nextGaussian() * yawNoiseDegrees * Double.pi / 180.0
            }
            samples.append(SpinSample(t: t, omega: measured, yaw: yaw))
        }

        return SyntheticRun(samples: samples,
                            trueOmega: trueOmega,
                            trueAngleDegrees: angle,
                            rotationRates: rotationRates,
                            gravities: gravities)
    }
}

/// 可重現的偽亂數，測試用，不需要密碼學強度。
public struct SplitMix64: Sendable {
    private var state: UInt64

    public init(seed: UInt64) { state = seed }

    public mutating func next() -> UInt64 {
        state = state &+ 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    public mutating func nextUniform() -> Double {
        Double(next() >> 11) * (1.0 / 9_007_199_254_740_992.0)
    }

    /// Box–Muller。
    public mutating func nextGaussian() -> Double {
        let u1 = max(nextUniform(), 1e-12)
        let u2 = nextUniform()
        return (-2.0 * log(u1)).squareRoot() * cos(2.0 * Double.pi * u2)
    }
}
