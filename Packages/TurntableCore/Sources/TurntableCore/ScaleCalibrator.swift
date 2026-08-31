import Foundation

public struct CalibrationResult: Sendable {
    /// 修正倍率 k，實際使用時 ω_cal = k × ω_gyro。
    public let factor: Double
    public let revolutions: Int
    public let gyroAngleDegrees: Double
    public let magneticAngleDegrees: Double

    /// 給定磁北 yaw 的角度雜訊，回推這次校準的相對精度（比例值，非百分比）。
    public func precision(yawNoiseDegrees: Double) -> Double {
        magneticAngleDegrees > 0 ? yawNoiseDegrees / magneticAngleDegrees : .infinity
    }
}

/// 規格 §3.7：指南針自動校準。
///
/// 陀螺儀積分的總角度會被比例因子誤差放大；磁北 yaw 是絕對量測、長期不漂移。
/// 比較同一時間窗內的總轉角就得到修正倍率。時間窗切在整數圈上，
/// 好讓房間座標系裡固定磁源造成的每圈一次失真在相減時抵消。
public enum ScaleCalibrator {

    public static func calibrate(_ samples: [SpinSample]) -> CalibrationResult? {
        guard samples.count >= 2 else { return nil }

        var gyroAngle = [Double](repeating: 0, count: samples.count)
        for i in 1 ..< samples.count {
            let dt = samples[i].t - samples[i - 1].t
            gyroAngle[i] = gyroAngle[i - 1] + dt * (samples[i].omega + samples[i - 1].omega) / 2.0
        }

        var magAngle = [Double](repeating: 0, count: samples.count)
        var accumulated = 0.0
        var previous: Double?
        for i in 0 ..< samples.count {
            guard let yaw = samples[i].yaw else { return nil }
            if let p = previous {
                var delta = yaw - p
                while delta > Double.pi { delta -= 2 * Double.pi }
                while delta < -Double.pi { delta += 2 * Double.pi }
                accumulated += delta
            }
            previous = yaw
            magAngle[i] = abs(accumulated * 180.0 / Double.pi)
        }

        let total = magAngle[magAngle.count - 1]
        let revolutions = Int(total / 360.0)
        guard revolutions >= 1 else { return nil }

        let target = Double(revolutions) * 360.0
        var end = samples.count - 1
        for i in 0 ..< magAngle.count where magAngle[i] >= target {
            end = i
            break
        }
        guard gyroAngle[end] > 0 else { return nil }

        return CalibrationResult(factor: magAngle[end] / gyroAngle[end],
                                 revolutions: revolutions,
                                 gyroAngleDegrees: gyroAngle[end],
                                 magneticAngleDegrees: magAngle[end])
    }

    /// 手動備援：碼錶量 N 圈用了 T 秒。
    public static func manualFactor(revolutions: Int, seconds: Double, measuredRPM: Double) -> Double? {
        guard revolutions > 0, seconds > 0, measuredRPM > 0 else { return nil }
        let trueRPM = 60.0 * Double(revolutions) / seconds
        return trueRPM / measuredRPM
    }

    /// 碼錶法的精度，用來在 UI 上老實告訴使用者手動不一定比較準。
    /// 100 圈搭配 ±0.3 s 的人為誤差 → 0.17%；200 圈 → 0.08%。
    public static func manualPrecision(revolutions: Int, rpm: Double, timingErrorSeconds: Double) -> Double {
        let seconds = 60.0 * Double(revolutions) / rpm
        return seconds > 0 ? timingErrorSeconds / seconds : .infinity
    }

    /// 指南針校準要跑幾圈才到得了目標精度。
    public static func requiredRevolutions(yawNoiseDegrees: Double, targetPrecision: Double) -> Int {
        guard targetPrecision > 0 else { return Int.max }
        return Int(ceil(yawNoiseDegrees / (360.0 * targetPrecision)))
    }
}
