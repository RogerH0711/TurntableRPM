import Foundation

/// 一段乾淨的量測區間。
public struct StableWindow: Sendable, Equatable {
    /// 保留下來的索引範圍。
    public let range: Range<Int>
    public let droppedAtStart: Int
    public let droppedAtEnd: Int
    /// 中間被丟掉的樣本數。> 0 代表量測途中被干擾過（碰到桌子、有人經過），
    /// 這時保留的是最長的那一段，其餘資料被放棄。
    public let droppedInMiddle: Int
    /// 判定用的中位數角速度，°/s。
    public let medianOmega: Double

    public var droppedTotal: Int { droppedAtStart + droppedAtEnd + droppedInMiddle }
    public var isPristine: Bool { droppedTotal == 0 }
}

/// 找出量測中轉速穩定的那一段。
///
/// **為什麼一定要有這個。** 一般人的操作是「先按開始，再把手機放上去」。那樣的
/// 資料開頭會有放置的撞擊、盤面被壓到的減速，更糟的是從靜止開始的整段加速。
///
/// 後果不只是平均轉速偏低。開頭一段 −100% 的偏差會在頻譜低頻端灌進巨大能量，
/// 把每圈一次的偏心峰整個淹掉 —— **「問題出在哪」那一區會給出錯誤的診斷**，
/// 而那是這個 app 最有價值的功能。
///
/// 判準刻意用**中位數**而不是平均值：平均值本身就會被加速段拉低，用它當基準
/// 等於讓污染的資料定義什麼叫「正常」。中位數在污染低於一半時不受影響。
public enum StabilityGate {

    /// - Parameters:
    ///   - tolerance: 相對中位數的容許偏差。預設 2% —— 要抓的是加速、撞擊這種
    ///     整個量級的偏離，不是正常的抖晃（實測唱盤的瞬時偏差在 ±1.5% 以內）。
    ///   - minimumSeconds: 保留區間至少要這麼長，否則視為整段量測失敗。
    ///   - gapSeconds: 短於這個長度的離群段會被視為雜訊而忽略，不切斷區間。
    ///     少了這一步，單一根毛刺就會把資料切成兩半，然後丟掉其中一半。
    ///   - maximumTolerance: 門檻的上限。**這個參數是必要的，不是保險絲。**
    ///     見下面關於 MAD 的說明。
    ///
    /// **限制：穩定段必須佔資料的一半以上。** 中位數在污染低於一半時才可靠；
    /// 超過一半（例如量了 10 秒加速只錄到 2 秒穩定）中位數會落在加速段上，
    /// 整個判準失效。那種情況這裡回 nil —— 承認量測失敗，比給一個錯的區間好。
    public static func find(_ samples: [SpinSample],
                            tolerance: Double = 0.02,
                            minimumSeconds: Double = 5.0,
                            gapSeconds: Double = 0.3,
                            maximumTolerance: Double = 0.10) -> StableWindow? {
        guard samples.count >= 8 else { return nil }

        let omega = samples.map { abs($0.omega) }
        let median = medianOf(omega)
        guard median > 0 else { return nil }

        // 門檻取「固定比例」與「6 倍 MAD」的較大者，**再加上上限**。
        //
        // MAD 的用意是讓抖晃比較大的唱盤有相稱的寬容度。但沒有上限的話，
        // MAD 會把整件事反過來：一段純加速的資料 MAD 本來就很大（中位數 150、
        // MAD 75 → 門檻 450），於是加速段自己把自己判成正常。
        //
        // 這正是「讓污染的資料定義什麼叫正常」—— 中心點用中位數已經避開一次，
        // 不能從離散度這邊放回來。真實唱盤的瞬時偏差不會超過 10%，超過就是污染。
        let mad = medianOf(omega.map { abs($0 - median) })
        let threshold = min(max(tolerance * median, 6.0 * mad), maximumTolerance * median)

        var stable = omega.map { abs($0 - median) <= threshold }
        closeShortGaps(&stable, samples: samples, gapSeconds: gapSeconds)

        guard let best = longestRun(stable) else { return nil }
        let span = samples[best.upperBound - 1].t - samples[best.lowerBound].t
        guard span >= minimumSeconds else { return nil }

        let middle = samples.count - best.count - best.lowerBound
            - (samples.count - best.upperBound)
        return StableWindow(range: best,
                            droppedAtStart: best.lowerBound,
                            droppedAtEnd: samples.count - best.upperBound,
                            droppedInMiddle: max(0, middle),
                            medianOmega: median)
    }

    // MARK: - 內部

    static func medianOf(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        let s = values.sorted()
        let m = s.count / 2
        return s.count % 2 == 1 ? s[m] : (s[m - 1] + s[m]) / 2
    }

    /// 把短於 `gapSeconds` 的 false 區段填回 true。
    ///
    /// 單一取樣的毛刺不該把一段乾淨的量測切成兩半 —— 沒有這一步，取最長區間
    /// 的策略會因為中間一根雜訊而丟掉一半資料。
    static func closeShortGaps(_ stable: inout [Bool],
                               samples: [SpinSample],
                               gapSeconds: Double) {
        var i = 0
        while i < stable.count {
            guard !stable[i] else { i += 1; continue }
            var j = i
            while j < stable.count && !stable[j] { j += 1 }
            // 只填「兩側都有穩定資料」的洞。頭尾的離群段是要丟掉的東西，不能填。
            if i > 0 && j < stable.count {
                let gap = samples[j - 1].t - samples[i].t
                if gap <= gapSeconds {
                    for k in i ..< j { stable[k] = true }
                }
            }
            i = j
        }
    }

    static func longestRun(_ stable: [Bool]) -> Range<Int>? {
        var best: Range<Int>?
        var i = 0
        while i < stable.count {
            guard stable[i] else { i += 1; continue }
            var j = i
            while j < stable.count && stable[j] { j += 1 }
            if best == nil || (j - i) > best!.count { best = i ..< j }
            i = j
        }
        return best
    }
}
