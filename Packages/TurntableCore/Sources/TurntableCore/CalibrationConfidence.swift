import Foundation

/// 倍率 k 到底能不能採信。
///
/// 這個型別的存在理由是一次真機實測（TD 235 EV，68.1 秒 36 圈）：
/// app 估出 k = 0.99994。碼錶同步量測的真值是 0.99915 —— **它猜對了，但那是矇的。**
///
/// `CMDeviceMotion.attitude.yaw` 是融合結果：盤面以 190 °/s 連續轉動時
/// CoreMotion 的磁修正被降權到幾乎沒有貢獻，yaw 退化成陀螺儀積分本身。
/// 那次量測的兩條路徑是 12987° 與 12986° —— **只差 1°，遠低於磁力計 5° 的雜訊底線。**
/// 換句話說這個估計不管陀螺儀準不準，都會吐出 k ≈ 1。
///
/// 關鍵在於：兩條路徑幾乎沒有分歧、k ≈ 1 時，你無法區分
/// 「陀螺儀真的很準」與「yaw 根本就是陀螺儀積分」。**資料本身不含這個資訊。**
/// 這次剛好是前者，但那是運氣，不是量測。
/// 所以這種情況一律回報 `.indistinguishable`，UI 不准說「可以參考了」。
///
/// 真正獨立的路徑見 `MagneticRevolutionCounter`。
public enum CalibrationConfidence: Sendable, Equatable {
    /// 還不滿一圈，算不出倍率。
    case insufficient

    /// 兩條路徑的分歧量沉在磁力計雜訊底線以下 —— 倍率不可採信。
    case indistinguishable(divergenceDegrees: Double, noiseFloorDegrees: Double)

    /// 分歧量高出雜訊底線，倍率可以採信。`precision` 是相對精度（比例值，非百分比）。
    case usable(precision: Double)

    public var isUsable: Bool {
        if case .usable = self { return true }
        return false
    }
}

extension ScaleCalibrator {

    /// 判斷倍率 k 能不能採信。見 `CalibrationConfidence` 的說明。
    ///
    /// 判準不是圈數，是**兩條路徑有沒有真的分歧**。舊版 UI 用 `revolutions >= 30`
    /// 當判準，結果在 36 圈時對著一個「無法區分對錯」的數字說「可以參考了」。
    ///
    /// - Parameters:
    ///   - yawNoiseDegrees: 磁北 yaw 的角度雜訊，實測環境常見 5°。
    ///   - minimumSigma: 分歧量要超過雜訊的幾倍才算數。
    public static func confidence(gyroTotalDegrees: Double,
                                  magneticTotalDegrees: Double,
                                  revolutions: Int,
                                  yawNoiseDegrees: Double = 5.0,
                                  minimumSigma: Double = 3.0) -> CalibrationConfidence {
        guard revolutions >= 1, gyroTotalDegrees > 0, magneticTotalDegrees > 0 else {
            return .insufficient
        }
        let divergence = abs(magneticTotalDegrees - gyroTotalDegrees)
        let noiseFloor = yawNoiseDegrees * minimumSigma
        guard divergence > noiseFloor else {
            return .indistinguishable(divergenceDegrees: divergence,
                                      noiseFloorDegrees: noiseFloor)
        }
        return .usable(precision: yawNoiseDegrees / magneticTotalDegrees)
    }
}
