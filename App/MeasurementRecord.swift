import Foundation
import SwiftData
import TurntableCore

/// 一次量測的存檔。
///
/// **只存分析後的結論，不存逐樣本原始資料。** 一次 3 分鐘的量測是兩萬筆樣本、
/// 約 700 KB，存個幾十次資料庫就肥了。原始資料要留的話用「匯出」，那是刻意的
/// 一次性動作。歷史記錄要回答的是「上次調整之後有沒有變好」，摘要就夠。
@Model
final class MeasurementRecord {
    var date: Date = Date()
    var note: String = ""

    // 轉速
    var meanRPM: Double = 0
    var nominalLabel: String?
    var errorPercent: Double?
    /// 當下套用的校準倍率。沒校準是 nil —— 跟「倍率剛好是 1」要分得開。
    var calibrationFactor: Double?

    // 量測條件
    var durationSeconds: Double = 0
    var revolutions: Int = 0
    /// 被穩定閘門切掉的秒數。大於零代表操作或盤面有狀況。
    var trimmedSeconds: Double = 0

    // 分析
    var wrmsPercent: Double = 0
    var peak2SigmaPercent: Double = 0
    var onePerRevPercent: Double = 0
    var rotationHz: Double = 0
    var dominantPeakShare: Double = 0
    /// 譜峰用 JSON 存。SwiftData 對巢狀型別的支援綁手綁腳，而這是唯讀的展示資料，
    /// 不需要查詢或關聯。
    var peaksJSON: Data?

    init() {}

    /// 從一次分析建立記錄。
    convenience init(analysis: MeasurementAnalysis,
                     snapshot: MotionEngine.Snapshot,
                     calibration: Double?) {
        self.init()
        date = Date()
        meanRPM = analysis.meanRPM * (calibration ?? 1.0)
        nominalLabel = snapshot.nominal?.label
        errorPercent = snapshot.errorPercent
        calibrationFactor = calibration
        durationSeconds = analysis.durationSeconds
        revolutions = snapshot.revolutions
        trimmedSeconds = analysis.trimmedStartSeconds + analysis.trimmedEndSeconds
        wrmsPercent = analysis.wowFlutter.wrmsPercent
        peak2SigmaPercent = analysis.wowFlutter.peak2SigmaPercent
        onePerRevPercent = analysis.onePerRevolutionPercent
        rotationHz = analysis.rotationHz
        dominantPeakShare = analysis.dominantPeakShare
        peaksJSON = try? JSONEncoder().encode(
            analysis.peaks.prefix(6).map(StoredPeak.init))
    }

    var peaks: [StoredPeak] {
        guard let peaksJSON else { return [] }
        return (try? JSONDecoder().decode([StoredPeak].self, from: peaksJSON)) ?? []
    }

    var isCalibrated: Bool { calibrationFactor != nil }
}

/// 存檔用的譜峰。刻意跟 `SpectralPeak` 分開 —— 核心的型別可能會演進，
/// 存檔格式不該跟著動，否則舊記錄會讀不出來。
struct StoredPeak: Codable, Identifiable {
    var id: Double { frequencyHz }
    let frequencyHz: Double
    let amplitudePercent: Double
    let orderOfRotation: Double
    let isHarmonic: Bool

    init(_ p: SpectralPeak) {
        frequencyHz = p.frequencyHz
        amplitudePercent = p.amplitudePercent
        orderOfRotation = p.orderOfRotation
        isHarmonic = p.isRotationHarmonic
    }

    /// 判讀文字用存下來的欄位重建，不依賴核心的型別。
    var interpretation: String {
        let n = Int(orderOfRotation.rounded())
        if isHarmonic {
            switch n {
            case 1:  return "每圈一次 —— 偏心"
            case 2:  return "每圈兩次 —— 盤面橢圓"
            default: return "轉盤 \(n)× 諧波"
            }
        }
        if orderOfRotation < 1 { return "比一圈還慢 —— 皮帶循環" }
        return String(format: "非諧波（%.1f 倍）—— 傳動鏈", orderOfRotation)
    }
}
