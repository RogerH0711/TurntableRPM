import Foundation

public struct LoadCompensationResult: Sendable {
    public let slopeRPMPerGram: Double
    public let zeroLoadRPM: Double
    /// 手機造成的轉速變化量（負值代表拖慢）。
    public let phoneEffectRPM: Double
    /// 斜率是否超出量測雜訊。false 就是「你的唱盤對載重不敏感，不需要補償」。
    public let isSignificant: Bool
}

/// 規格 §3.8：兩點外插。
///
/// 查表得知手機是 171 克本身沒有告訴我們任何事 —— 影響量完全取決於馬達型式
/// （同步交流馬達幾乎為零，無調速直流馬達最大）。所以實測斜率再外插回零負載。
public enum LoadCompensator {
    public static func extrapolate(rpmWithPhone: Double,
                                   rpmWithAddedMass: Double,
                                   addedMassGrams: Double,
                                   phoneMassGrams: Double,
                                   noiseRPM: Double = 0.005) -> LoadCompensationResult? {
        guard addedMassGrams > 0, phoneMassGrams > 0 else { return nil }
        let slope = (rpmWithAddedMass - rpmWithPhone) / addedMassGrams
        let effect = slope * phoneMassGrams
        return LoadCompensationResult(slopeRPMPerGram: slope,
                                      zeroLoadRPM: rpmWithPhone - effect,
                                      phoneEffectRPM: effect,
                                      isSignificant: abs(effect) > 2.0 * noiseRPM)
    }
}
