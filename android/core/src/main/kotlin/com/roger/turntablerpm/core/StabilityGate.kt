package com.roger.turntablerpm.core

import kotlin.math.abs

/** 一段乾淨的量測區間。`range` 是保留下來的索引範圍（半開區間）。 */
data class StableWindow(
    val startIndex: Int,
    val endIndex: Int,               // 不含
    val droppedAtStart: Int,
    val droppedAtEnd: Int,
    /**
     * 中間被丟掉的樣本數。> 0 代表量測途中被干擾過（碰到桌子、有人經過），
     * 這時保留的是最長的那一段，其餘資料被放棄。
     */
    val droppedInMiddle: Int,
    /** 判定用的中位數角速度，°/s。 */
    val medianOmega: Double,
) {
    val count: Int get() = endIndex - startIndex
    val droppedTotal: Int get() = droppedAtStart + droppedAtEnd + droppedInMiddle
    val isPristine: Boolean get() = droppedTotal == 0
}

/**
 * 找出量測中轉速穩定的那一段。
 *
 * **為什麼一定要有這個。** 一般人的操作是「先按開始，再把手機放上去」。那樣的
 * 資料開頭會有放置的撞擊、盤面被壓到的減速，更糟的是從靜止開始的整段加速。
 *
 * 後果不只是平均轉速偏低。開頭一段 −100% 的偏差會在頻譜低頻端灌進巨大能量，
 * 把每圈一次的偏心峰整個淹掉 —— **「問題出在哪」那一區會給出錯誤的診斷**，
 * 而那是這個 app 最有價值的功能。
 *
 * 判準刻意用**中位數**而不是平均值：平均值本身就會被加速段拉低，用它當基準
 * 等於讓污染的資料定義什麼叫「正常」。
 */
object StabilityGate {

    /**
     * @param tolerance        相對中位數的容許偏差。預設 2% —— 要抓的是加速、撞擊這種
     *                         整個量級的偏離，不是正常的抖晃。
     * @param minimumSeconds   保留區間至少要這麼長，否則視為整段量測失敗。
     * @param gapSeconds       短於這個長度的離群段會被視為雜訊而忽略，不切斷區間。
     * @param maximumTolerance 門檻的上限。**這個參數是必要的，不是保險絲。**
     *
     * **限制：穩定段必須佔資料的一半以上。** 超過一半是污染時中位數會落在加速段上，
     * 判準失效。那種情況這裡回 null —— 承認量測失敗，比給一個錯的區間好。
     */
    fun find(
        samples: List<SpinSample>,
        tolerance: Double = 0.02,
        minimumSeconds: Double = 5.0,
        gapSeconds: Double = 0.3,
        maximumTolerance: Double = 0.10,
    ): StableWindow? {
        if (samples.size < 8) return null

        val omega = DoubleArray(samples.size) { abs(samples[it].omega) }
        val median = medianOf(omega)
        if (median <= 0) return null

        // 門檻取「固定比例」與「6 倍 MAD」的較大者，**再加上上限**。
        //
        // MAD 的用意是讓抖晃比較大的唱盤有相稱的寬容度。但沒有上限的話，
        // MAD 會把整件事反過來：一段純加速的資料 MAD 本來就很大（中位數 150、
        // MAD 75 → 門檻 450），於是加速段自己把自己判成正常。
        //
        // 這正是「讓污染的資料定義什麼叫正常」—— 中心點用中位數已經避開一次，
        // 不能從離散度這邊放回來。
        val mad = medianOf(DoubleArray(omega.size) { abs(omega[it] - median) })
        val threshold = minOf(maxOf(tolerance * median, 6.0 * mad), maximumTolerance * median)

        val stable = BooleanArray(omega.size) { abs(omega[it] - median) <= threshold }
        closeShortGaps(stable, samples, gapSeconds)

        val best = longestRun(stable) ?: return null
        val span = samples[best.second - 1].t - samples[best.first].t
        if (span < minimumSeconds) return null

        val kept = best.second - best.first
        val middle = samples.size - kept - best.first - (samples.size - best.second)
        return StableWindow(
            startIndex = best.first,
            endIndex = best.second,
            droppedAtStart = best.first,
            droppedAtEnd = samples.size - best.second,
            droppedInMiddle = maxOf(0, middle),
            medianOmega = median,
        )
    }

    internal fun medianOf(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sortedArray()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /**
     * 把短於 `gapSeconds` 的 false 區段填回 true。
     *
     * 單一取樣的毛刺不該把一段乾淨的量測切成兩半 —— 沒有這一步，取最長區間
     * 的策略會因為中間一根雜訊而丟掉一半資料。
     */
    internal fun closeShortGaps(stable: BooleanArray, samples: List<SpinSample>, gapSeconds: Double) {
        var i = 0
        while (i < stable.size) {
            if (stable[i]) { i++; continue }
            var j = i
            while (j < stable.size && !stable[j]) j++
            // 只填「兩側都有穩定資料」的洞。頭尾的離群段是要丟掉的東西，不能填。
            if (i > 0 && j < stable.size) {
                val gap = samples[j - 1].t - samples[i].t
                if (gap <= gapSeconds) for (k in i until j) stable[k] = true
            }
            i = j
        }
    }

    /** 回傳最長的 true 區段 [起, 迄)，沒有就回 null。 */
    internal fun longestRun(stable: BooleanArray): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var i = 0
        while (i < stable.size) {
            if (!stable[i]) { i++; continue }
            var j = i
            while (j < stable.size && stable[j]) j++
            if (best == null || (j - i) > (best.second - best.first)) best = i to j
            i = j
        }
        return best
    }
}
