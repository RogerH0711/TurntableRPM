package com.roger.turntablerpm.core

data class PolarBin(val meanDeviation: Double, val count: Int)

/**
 * 規格 §3.6：把偏差依轉盤角度分箱平均。
 *
 * 每格的樣本數是 fs × T ÷ 格數，與轉速無關 —— 60 秒 100 Hz、72 格時
 * 每格恆為 83 筆，16⅔ 或 78 轉都一樣。
 *
 * **角度不能跨次量測比較**：0° 是按下開始那一瞬間盤面的位置，而那是隨機的。
 * 要比較就得在盤面貼記號，每次等記號轉到同一位置才開始。
 */
class PolarAccumulator(binCount: Int = 72) {

    val binCount: Int = maxOf(1, binCount)
    private val sums = DoubleArray(this.binCount)
    private val counts = IntArray(this.binCount)

    val binWidthDegrees: Double get() = 360.0 / binCount

    fun add(angleDegrees: Double, deviationPercent: Double) {
        var a = angleDegrees % 360.0
        if (a < 0) a += 360.0
        var index = (a / binWidthDegrees).toInt()
        if (index >= binCount) index = binCount - 1
        sums[index] += deviationPercent
        counts[index] += 1
    }

    val bins: List<PolarBin>
        get() = (0 until binCount).map { i ->
            PolarBin(if (counts[i] > 0) sums[i] / counts[i] else 0.0, counts[i])
        }

    /** 最大偏差所在的箱中心角度。 */
    val peakAngleDegrees: Double?
        get() {
            val all = bins
            val peak = all.indices.filter { all[it].count > 0 }
                .maxByOrNull { all[it].meanDeviation } ?: return null
            return (peak + 0.5) * binWidthDegrees
        }

    companion object {
        /** 建議的色階上下限：±2 × 加權 WRMS。手動鎖定才能跨次量測比較顏色。 */
        fun suggestedColorScale(wrmsPercent: Double): Double = maxOf(2.0 * wrmsPercent, 0.01)
    }
}
