import Foundation

/// 規格 §3.2：平均轉速、穩定閘門、標稱轉速自動辨識。
public enum SpeedStatistics {

    /// 梯形積分平均。用真實 dt 加權，容忍 CoreMotion 的派送抖動。
    public static func meanOmega(_ samples: [SpinSample]) -> Double? {
        guard samples.count >= 2 else { return nil }
        let span = samples[samples.count - 1].t - samples[0].t
        guard span > 0 else { return nil }
        var area = 0.0
        for i in 1 ..< samples.count {
            let dt = samples[i].t - samples[i - 1].t
            area += dt * (samples[i].omega + samples[i - 1].omega) / 2.0
        }
        return area / span
    }

    public static func meanRPM(_ samples: [SpinSample]) -> Double? {
        guard let omega = meanOmega(samples) else { return nil }
        return omega / 6.0
    }

    /// 穩定閘門：相對標準差夠小才開始累積統計，避免把啟動加速段算進去。
    public static func isStable(_ samples: [SpinSample], relativeStdDevLimit: Double = 0.02) -> Bool {
        guard samples.count >= 2, let mean = meanOmega(samples), mean > 0 else { return false }
        var sumSquares = 0.0
        for sample in samples {
            let d = sample.omega - mean
            sumSquares += d * d
        }
        let sd = (sumSquares / Double(samples.count)).squareRoot()
        return sd / mean <= relativeStdDevLimit
    }

    /// 最近鄰標稱轉速。相鄰標稱值最近的一對是 33⅓ 與 45（差 35%），所以 ±8% 的窗很安全。
    public static func classify(rpm: Double,
                                tolerance: Double = 0.08,
                                candidates: [TurntableSpeed] = TurntableSpeed.standard) -> TurntableSpeed? {
        var best: TurntableSpeed?
        var bestDistance = Double.greatestFiniteMagnitude
        for candidate in candidates {
            let d = abs(rpm - candidate.rpm) / candidate.rpm
            if d < bestDistance {
                bestDistance = d
                best = candidate
            }
        }
        return bestDistance <= tolerance ? best : nil
    }

    public static func errorPercent(rpm: Double, nominal: TurntableSpeed) -> Double {
        (rpm - nominal.rpm) / nominal.rpm * 100.0
    }
}
