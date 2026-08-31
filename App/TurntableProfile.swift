import Foundation
import SwiftData

/// 一台唱盤的設定檔。
///
/// 存的是**唱盤自己的性質**，跟量測無關的那些：原廠規格、傳動鏈尺寸、
/// 對載重的敏感度。有了它，量測結果才能自動跟規格比對，頻譜也才知道
/// 「35.32 倍」對不對得上這台盤的傳動比。
@Model
final class TurntableProfile {
    var name: String = ""
    var maker: String = ""
    /// 原廠的抖晃率規格，%。用來在分析頁直接標出超規格多少。
    var specWowFlutterPercent: Double?
    var note: String = ""
    /// 目前選用的那一台。同時只會有一個為 true。
    var isActive: Bool = false
    var createdAt: Date = Date()

    // MARK: - 傳動鏈尺寸（選填）
    //
    // 知道這兩個就能預測馬達的轉速頻率，把頻譜上「非諧波的某某倍」對上實體零件。
    // 皮帶厚度會加在兩邊的有效直徑上，所以比值是 (d+t)/(D+t) 而不是 d/D。

    /// 馬達皮帶輪直徑，mm。
    var pulleyDiameterMM: Double?
    /// 皮帶接觸的盤面直徑，mm（外盤緣或內盤，看皮帶跑在哪）。
    var platterDiameterMM: Double?
    /// 皮帶厚度，mm。
    var beltThicknessMM: Double?

    /// 預期的傳動比 = 馬達轉速 ÷ 轉盤轉速。尺寸不齊時為 nil。
    var expectedDriveRatio: Double? {
        guard let d = pulleyDiameterMM, let D = platterDiameterMM, d > 0, D > 0 else { return nil }
        let t = beltThicknessMM ?? 0
        return (D + t) / (d + t)
    }

    // MARK: - 載重

    /// 兩點外插量到的斜率，RPM/g。nil 代表還沒測。
    var loadSlopeRPMPerGram: Double?
    /// 量測時手機造成的轉速變化，RPM。負值代表被拖慢。
    var loadPhoneEffectRPM: Double?
    /// 斜率有沒有超出量測雜訊。false 代表這台盤對載重不敏感。
    var loadIsSignificant: Bool = false
    var loadMeasuredAt: Date?

    init(name: String = "") {
        self.name = name
        self.createdAt = Date()
    }

    var displayName: String {
        let full = [maker, name].filter { !$0.isEmpty }.joined(separator: " ")
        return full.isEmpty ? "未命名唱盤" : full
    }

    var hasLoadTest: Bool { loadSlopeRPMPerGram != nil }
}
