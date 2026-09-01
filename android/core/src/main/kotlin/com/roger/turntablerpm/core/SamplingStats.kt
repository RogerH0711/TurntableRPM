package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 取樣間隔的統計。
 *
 * **這是 Android 版特有的必要工具。** iOS 的 CoreMotion 取樣率穩定（實測中位數 9.990 ms、
 * 標準差 0.005 ms）；Android 的 `SensorManager.registerListener` 取樣率設定只是**建議值**，
 * 實際頻率由廠商實作決定，不同晶片與機型差異顯著。
 *
 * 對一般 app 無所謂，但這個 app 要量到 0.1% 轉速精度並做抖晃率的頻域分析，
 * 非均勻取樣會直接汙染結果 —— 所以要先量出來、記錄下來，再決定怎麼補償。
 */
data class SamplingStats(
    val count: Int,
    val durationSeconds: Double,
    /** 有效取樣率 = (筆數 − 1) ÷ 時長。這是**實際**拿到的，不是要求的。 */
    val effectiveRateHz: Double,
    val medianIntervalMs: Double,
    val meanIntervalMs: Double,
    /** 間隔的標準差。iOS 是 0.005 ms —— 這個數字是判斷 Android 有多糟的基準。 */
    val stdDevIntervalMs: Double,
    val minIntervalMs: Double,
    val maxIntervalMs: Double,
    /** 間隔超過中位數 1.5 倍的次數，通常代表掉了樣本。 */
    val longGaps: Int,
    /** 最大間隔是中位數的幾倍。1.0 表示完美等間隔。 */
    val worstGapRatio: Double,
) {
    /**
     * 抖動相對中位數的比例。
     *
     * iOS 實測 0.005/9.990 = 0.05%。這個值大到某個程度就必須靠重採樣補償 ——
     * 但重採樣本身也會有誤差，所以**先量出來，不要假設**。
     */
    val jitterRatio: Double get() = if (medianIntervalMs > 0) stdDevIntervalMs / medianIntervalMs else 0.0

    companion object {
        /** @param timestampsSeconds 感測器自己的時間戳，秒，必須遞增。 */
        fun from(timestampsSeconds: DoubleArray): SamplingStats? {
            if (timestampsSeconds.size < 3) return null
            val n = timestampsSeconds.size
            val duration = timestampsSeconds[n - 1] - timestampsSeconds[0]
            if (duration <= 0) return null

            val intervals = DoubleArray(n - 1) {
                (timestampsSeconds[it + 1] - timestampsSeconds[it]) * 1000.0
            }
            val sorted = intervals.sortedArray()
            val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0

            val mean = intervals.average()
            var sumSq = 0.0
            for (v in intervals) sumSq += (v - mean) * (v - mean)
            val sd = sqrt(sumSq / intervals.size)

            var longGaps = 0
            for (v in intervals) if (v > median * 1.5) longGaps++

            return SamplingStats(
                count = n,
                durationSeconds = duration,
                effectiveRateHz = (n - 1) / duration,
                medianIntervalMs = median,
                meanIntervalMs = mean,
                stdDevIntervalMs = sd,
                minIntervalMs = sorted.first(),
                maxIntervalMs = sorted.last(),
                longGaps = longGaps,
                worstGapRatio = if (median > 0) sorted.last() / median else 0.0,
            )
        }

        /**
         * Android 的 `SensorEvent.timestamp` 基準有已知的廠商差異：多數是開機以來的奈秒
         * （`elapsedRealtimeNanos`），但有些機型回報的是 `uptimeNanos`，少數甚至是 epoch。
         *
         * 這個 app 只用**差值**，所以基準不影響計算 —— 但要能認出來並記錄，
         * 那是「真的碰過硬體」的證據，也是之後對照多台裝置時的必要資訊。
         *
         * @return 距離哪一個基準最近（誤差秒數一併回傳）。
         */
        fun identifyTimestampBase(
            sensorTimestampNanos: Long,
            elapsedRealtimeNanos: Long,
            currentTimeMillis: Long,
        ): Pair<String, Double> {
            val candidates = listOf(
                "elapsedRealtime" to elapsedRealtimeNanos,
                "epoch" to currentTimeMillis * 1_000_000L,
            )
            val best = candidates.minBy { abs(sensorTimestampNanos - it.second) }
            return best.first to abs(sensorTimestampNanos - best.second) / 1e9
        }
    }
}
