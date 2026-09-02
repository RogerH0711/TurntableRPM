package com.roger.turntablerpm.ui

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
fun interpretation(peak: SpectralPeak): String = when (val k = peak.kind) {
    SpectralPeak.Kind.Eccentricity ->
        "每圈一次 —— 偏心（盤面、主軸或皮帶接觸面沒對正）"
    // **1× 大的時候這個判讀不可信。** 非正弦的每圈一次擾動本來就會生出 2×、3×。
    // 實測把手機轉 180° 讓 1× 掉 74% 之後，2× 也跟著掉 59% —— 而純粹的 180° 旋轉
    // 不該改變 2×（相位移 360°），所以那是耦合的諧波不是獨立缺陷。
    SpectralPeak.Kind.Ovality ->
        "每圈兩次 —— 盤面橢圓或主軸兩點磨損。但如果上面的每圈一次很大，" +
            "這根多半只是它的諧波，先把偏心降下來再看。"
    is SpectralPeak.Kind.Harmonic ->
        "轉盤 ${k.order}× 諧波"
    SpectralPeak.Kind.SlowerThanRotation ->
        "比一圈還慢 —— 皮帶循環或長週期漂移"
    SpectralPeak.Kind.DriveChain ->
        "非諧波（轉盤的 %.1f 倍）—— 傳動鏈上的零件，馬達或皮帶輪的候選"
            .format(peak.orderOfRotation)
}

/** 整數倍代表跟著盤面轉的東西，非整數倍代表傳動鏈 —— 用顏色區分。 */
fun isHarmonic(peak: SpectralPeak): Boolean = peak.isRotationHarmonic
