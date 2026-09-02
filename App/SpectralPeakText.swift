import Foundation
import TurntableCore

/// 譜峰判讀的文案。
///
/// **文字放在 App 層，不在核心。** 核心回傳的是分類（`SpectralPeak.Kind`），
/// 由這裡翻成給人看的句子 —— 這樣核心保持純演算法、不必背本地化資源，
/// 而文案也才能跟著系統語言走。
extension SpectralPeak {
    var interpretation: String {
        Self.text(for: kind, order: orderOfRotation)
    }

    static func text(for kind: Kind, order: Double) -> String {
        switch kind {
        case .eccentricity:
            return String(localized: "每圈一次 —— 偏心（盤面、主軸或皮帶接觸面沒對正）")
        case .ovality:
            // **1× 大的時候這個判讀不可信。** 非正弦的每圈一次擾動本來就會生出 2×、3×。
            // 實測把手機轉 180° 讓 1× 掉 74% 之後，2× 也跟著掉 59% —— 而純粹的
            // 180° 旋轉不該改變 2×（相位移 360°），所以那是耦合的諧波不是獨立缺陷。
            return String(localized: "每圈兩次 —— 盤面橢圓或主軸兩點磨損。但如果上面的每圈一次很大，這根多半只是它的諧波，先把偏心降下來再看。")
        case .harmonic(let n):
            return String(localized: "轉盤 \(n)× 諧波")
        case .slowerThanRotation:
            return String(localized: "比一圈還慢 —— 皮帶循環或長週期漂移")
        case .driveChain:
            let times = String(format: "%.1f", order)
            return String(localized: "非諧波（轉盤的 \(times) 倍）—— 傳動鏈上的零件，馬達或皮帶輪的候選")
        }
    }
}

extension StoredPeak {
    /// 存檔用的簡短版本。歷史列表空間比較窄。
    var interpretation: String {
        let n = Int(orderOfRotation.rounded())
        if isHarmonic {
            switch n {
            case 1:  return String(localized: "每圈一次 —— 偏心")
            case 2:  return String(localized: "每圈兩次 —— 盤面橢圓")
            default: return String(localized: "轉盤 \(n)× 諧波")
            }
        }
        if orderOfRotation < 1 { return String(localized: "比一圈還慢 —— 皮帶循環") }
        let times = String(format: "%.1f", orderOfRotation)
        return String(localized: "非諧波（\(times) 倍）—— 傳動鏈")
    }
}
