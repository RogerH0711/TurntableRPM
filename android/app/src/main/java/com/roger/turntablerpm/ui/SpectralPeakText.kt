package com.roger.turntablerpm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.roger.turntablerpm.R
import com.roger.turntablerpm.core.SpectralPeak

/**
 * 譜峰判讀的文案。
 *
 * **核心回傳的是分類（`SpectralPeak.Kind`），文字在這裡組。** 文案屬於 UI 層，
 * 而且要能本地化 —— 把中文寫死在演算法核心裡，等於逼那個純 Kotlin 模組去背資源檔。
 * iOS 端是同樣的分工（`SpectralPeakText.swift`）。
 *
 * 目前先寫死中文。要多語系時搬進 `strings.xml` 即可 —— Android 的資源查找是顯式的，
 * 不會像 iOS 的字串抽取那樣靜默失敗（見 CLAUDE.md 坑 29）。
 */
@Composable
fun interpretation(peak: SpectralPeak): String = when (val k = peak.kind) {
    SpectralPeak.Kind.Eccentricity ->
        stringResource(R.string.peak_eccentricity)
    // **1× 大的時候這個判讀不可信。** 非正弦的每圈一次擾動本來就會生出 2×、3×。
    // 實測把手機轉 180° 讓 1× 掉 74% 之後，2× 也跟著掉 59% —— 而純粹的 180° 旋轉
    // 不該改變 2×（相位移 360°），所以那是耦合的諧波不是獨立缺陷。
    SpectralPeak.Kind.Ovality ->
        stringResource(R.string.peak_ovality)
    is SpectralPeak.Kind.Harmonic ->
        stringResource(R.string.peak_harmonic, k.order)
    SpectralPeak.Kind.SlowerThanRotation ->
        stringResource(R.string.peak_slower_than_rotation)
    SpectralPeak.Kind.DriveChain ->
        stringResource(R.string.peak_drive_chain, peak.orderOfRotation)
}

/** 整數倍代表跟著盤面轉的東西，非整數倍代表傳動鏈 —— 用顏色區分。 */
fun isHarmonic(peak: SpectralPeak): Boolean = peak.isRotationHarmonic

/**
 * 存檔用譜峰的簡短判讀。
 *
 * **跟核心的 `SpectralPeak` 分開，因為存檔格式跟核心型別是分開演進的**
 * （見 `StoredPeak` 的註解）。歷史頁的空間也比較窄，所以句子短一些。
 */
@Composable
fun storedInterpretation(peak: com.roger.turntablerpm.history.StoredPeak): String {
    val n = Math.round(peak.orderOfRotation).toInt()
    if (peak.isHarmonic) {
        return when (n) {
            1 -> stringResource(R.string.stored_peak_eccentricity)
            2 -> stringResource(R.string.stored_peak_ovality)
            else -> stringResource(R.string.stored_peak_harmonic, n)
        }
    }
    if (peak.orderOfRotation < 1) return stringResource(R.string.stored_peak_slower)
    return stringResource(R.string.stored_peak_drive_chain, peak.orderOfRotation)
}
