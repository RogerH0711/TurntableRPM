package com.roger.turntablerpm.ui

/**
 * 把使用者打的數字轉成 Double，**逗號與句點都當成小數點**。
 *
 * 德文（以及大部分歐陸語系）的小數點是逗號，鍵盤上跳出來的也是逗號 ——
 * 而 `String.toDoubleOrNull()` 只認句點。多語系化之前這個問題不存在，
 * 因為畫面只有中文；一旦支援德文，使用者打「8,5」就會被判成無效輸入，
 * 而且畫面上不會有任何提示。
 *
 * 顯示端是相反的方向：`"%.2f".format(v)` 用的是 `Locale.getDefault()`，
 * 所以德文會顯示「0,31」。**讀進來與印出去要用同一套規則**，否則使用者
 * 會看到 app 自己印出「8,5」卻拒絕接受「8,5」。
 */
fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()

/** 數字輸入欄位允許的字元。逗號與句點都放行，由 [parseDecimal] 統一處理。 */
fun filterDecimalInput(text: String): String =
    text.filter { it.isDigit() || it == '.' || it == ',' }

/**
 * 把數值印回輸入欄位。8.5 顯示成「8.5」、8.0 顯示成「8」，不要一律補到小數三位。
 *
 * **小數點符號跟著語系走** —— 用 `DecimalFormat` 而不是 `toString()`：
 * 後者永遠印句點，德文使用者就會看到 app 自己印出「8.5」卻只接受「8,5」。
 */
fun formatDecimal(v: Double): String =
    java.text.DecimalFormat("0.####").format(v)
