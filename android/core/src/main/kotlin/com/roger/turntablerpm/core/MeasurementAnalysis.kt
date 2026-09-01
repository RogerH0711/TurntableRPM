package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 頻譜上的一根峰，附帶「它是什麼」的判讀。
 *
 * 光列出一堆頻率沒有用。真正有診斷價值的是**它跟轉盤基頻的關係**：
 * 整數倍代表跟著盤面轉的東西（偏心、盤面變形），非整數倍代表傳動鏈上
 * 轉速不同的零件（馬達、皮帶輪）。
 */
data class SpectralPeak(
    val frequencyHz: Double,
    /** 振幅，單位是「佔平均轉速的百分比」。 */
    val amplitudePercent: Double,
    /** 相對於轉盤基頻的倍數。1.0 = 每圈一次。 */
    val orderOfRotation: Double,
) {
    /** 是不是轉盤的整數諧波（容差 4%）。 */
    val isRotationHarmonic: Boolean
        get() {
            val n = orderOfRotation.roundToInt()
            return n >= 1 && abs(orderOfRotation - n) < 0.04
        }

    /**
     * 這根峰代表什麼零件。**回傳分類，不回傳文字。**
     *
     * 核心刻意不產生使用者可見的字串：文案屬於 UI 層，而且要能本地化 ——
     * 把中文寫死在演算法核心裡，等於逼這個純 Kotlin 模組去背本地化資源。
     */
    val kind: Kind
        get() {
            val n = orderOfRotation.roundToInt()
            if (isRotationHarmonic) {
                return when (n) {
                    1 -> Kind.Eccentricity
                    2 -> Kind.Ovality
                    else -> Kind.Harmonic(n)
                }
            }
            return if (orderOfRotation < 1) Kind.SlowerThanRotation else Kind.DriveChain
        }

    sealed interface Kind {
        /** 每圈一次 —— 盤面、主軸或皮帶接觸面沒對正。 */
        data object Eccentricity : Kind

        /** 每圈兩次 —— 盤面橢圓或主軸兩點磨損。 */
        data object Ovality : Kind

        /** 轉盤的第 n 次諧波。 */
        data class Harmonic(val order: Int) : Kind

        /** 比一圈還慢 —— 皮帶循環或長週期漂移。 */
        data object SlowerThanRotation : Kind

        /** 非諧波 —— 傳動鏈上轉速不同的零件。 */
        data object DriveChain : Kind
    }
}

/**
 * 一次量測的完整離線分析。
 *
 * **這一層刻意放在核心而不是 app**：它能在純 JVM 上跑測試，也能拿 iOS 端匯出的
 * 逐樣本資料交叉比對。app 端只負責畫圖。
 *
 * 分析路徑一律用**未平滑**的偏差序列。移動平均在 fs/N 有零點，
 * 100 Hz 下 N=25 的零點正好落在加權曲線的 4 Hz 峰值上。
 */
class MeasurementAnalysis private constructor(
    val meanRPM: Double,
    val sampleRate: Double,
    val durationSeconds: Double,
    /** 轉盤基頻（Hz）。1 / 每圈秒數。 */
    val rotationHz: Double,
    /** 未平滑的瞬時偏差序列，%。滾動圖用。 */
    val deviationPercent: DoubleArray,
    val wowFlutter: WowFlutterResult,
    val spectrumFrequencies: DoubleArray,
    val spectrumAmplitudes: DoubleArray,
    /** 依振幅排序的顯著譜峰。 */
    val peaks: List<SpectralPeak>,
    /** 依圈內角度分箱的平均偏差。極座標熱圖用。 */
    val polarBins: List<PolarBin>,
    val peakAngleDegrees: Double?,
    /**
     * 實際拿來分析的區間。開頭的加速、尾端的減速、中途的干擾都在這裡被切掉。
     * **所有數字都是這個區間算出來的**，不是整段量測。
     */
    val stableWindow: StableWindow,
    /** 被切掉的秒數。要在畫面上如實告訴使用者。 */
    val trimmedStartSeconds: Double,
    val trimmedEndSeconds: Double,
) {

    /**
     * 最強的那根譜峰佔全部譜峰功率的比例（0–1）。
     *
     * **判斷「單頻主導」還是「隨機抖動」要用這個，不要用峰值/RMS 比。**
     * 峰值/RMS 的理論值是單頻 1.41、高斯隨機 1.96，但實測兩次同一台唱盤
     * 得到 1.67 與 1.95 —— 譜峰內容其實一模一樣，只是比值本身的隨機起伏
     * 就跨過了中點。拿它當二分判準會給出前後矛盾的結論。
     */
    val dominantPeakShare: Double
        get() {
            val power = peaks.map { it.amplitudePercent * it.amplitudePercent }
            val total = power.sum()
            val top = power.maxOrNull() ?: return 0.0
            return if (total > 0) top / total else 0.0
        }

    /** 每圈一次成分的振幅（%）。偏心的直接指標。 */
    val onePerRevolutionPercent: Double
        get() = peaks.firstOrNull {
            it.isRotationHarmonic && it.orderOfRotation.roundToInt() == 1
        }?.amplitudePercent ?: 0.0

    companion object {

        /**
         * @param samples    原始樣本。時間戳可以有抖動，內部會重取樣到等間隔。
         * @param sampleRate 重取樣的目標頻率。**Android 上不要寫死** ——
         *   實測 XZ Premium 要求 100 Hz 會拿到 107.92 Hz，應該傳入實測的有效速率，
         *   或選一個低於原生速率的固定值（往下重取樣安全，往上會生出假的細節）。
         */
        fun analyze(
            samples: List<SpinSample>,
            sampleRate: Double = 100.0,
            binCount: Int = 72,
        ): MeasurementAnalysis? {
            // 先切出穩定區間再分析。少了這一步，一段從靜止開始的加速會在頻譜低頻端
            // 灌進巨大能量，把每圈一次的偏心峰淹掉 —— 診斷會整個錯掉。
            if (samples.size <= 64) return null
            val window = StabilityGate.find(samples) ?: return null
            val trimmedStart =
                if (window.droppedAtStart > 0) samples[window.startIndex].t - samples.first().t else 0.0
            val trimmedEnd =
                if (window.droppedAtEnd > 0) samples.last().t - samples[window.endIndex - 1].t else 0.0
            val stable = samples.subList(window.startIndex, window.endIndex)

            val resampled = UniformResampler.resample(stable, sampleRate) ?: return null
            val series = DeviationSeries.make(resampled.values) ?: return null
            val meanOmega = series.mean
            if (meanOmega <= 0) return null
            val deviation = series.deviationPercent
            val wf = WowFlutterAnalyzer.analyze(deviation, sampleRate) ?: return null

            val duration = (resampled.values.size - 1) / sampleRate
            val rotationHz = meanOmega / 360.0

            val spectrum = FFT.amplitudeSpectrum(deviation, sampleRate)
            val peaks = findPeaks(spectrum.frequencies, spectrum.amplitudes, rotationHz)

            // 極座標分箱。角度用等間隔網格上的累積轉角推算 —— 重取樣之後每一步
            // 的時間都是 1/fs，所以角度就是 ω 的累加。
            val polar = PolarAccumulator(binCount)
            var angle = 0.0
            for (i in resampled.values.indices) {
                polar.add(angle, deviation[i])
                angle += resampled.values[i] / sampleRate
            }

            return MeasurementAnalysis(
                meanRPM = meanOmega / 6.0,
                sampleRate = sampleRate,
                durationSeconds = duration,
                rotationHz = rotationHz,
                deviationPercent = deviation,
                wowFlutter = wf,
                spectrumFrequencies = spectrum.frequencies,
                spectrumAmplitudes = spectrum.amplitudes,
                peaks = peaks,
                polarBins = polar.bins,
                peakAngleDegrees = polar.peakAngleDegrees,
                stableWindow = window,
                trimmedStartSeconds = trimmedStart,
                trimmedEndSeconds = trimmedEnd,
            )
        }

        /**
         * 找局部極大值。
         *
         * 門檻用「整段頻譜的中位數振幅」的倍數，而不是固定值 —— 不同量測的雜訊
         * 底線差很多，固定門檻在安靜的量測裡會漏掉真峰、在吵的量測裡會塞滿雜訊。
         *
         * 只看 0.05–50 Hz：更低的是量測時長不夠解析的漂移，更高的超出可用範圍。
         */
        internal fun findPeaks(
            frequencies: DoubleArray,
            amplitudes: DoubleArray,
            rotationHz: Double,
            maxCount: Int = 12,
        ): List<SpectralPeak> {
            if (frequencies.size != amplitudes.size || frequencies.size <= 8 || rotationHz <= 0) {
                return emptyList()
            }
            val band = amplitudes.indices.filter { frequencies[it] > 0.05 && frequencies[it] < 50 }
            if (band.size <= 8) return emptyList()

            val sorted = band.map { amplitudes[it] }.sorted()
            val median = sorted[sorted.size / 2]
            val threshold = maxOf(median * 6.0, 1e-9)

            val found = ArrayList<SpectralPeak>()
            for (i in band) {
                if (i <= 0 || i >= amplitudes.size - 1) continue
                val a = amplitudes[i]
                if (a <= threshold || a <= amplitudes[i - 1] || a < amplitudes[i + 1]) continue
                found += SpectralPeak(
                    frequencyHz = frequencies[i],
                    amplitudePercent = a,
                    orderOfRotation = frequencies[i] / rotationHz,
                )
            }
            found.sortByDescending { it.amplitudePercent }
            return found.take(maxCount)
        }
    }
}
