package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.core.MeasurementAnalysis
import com.roger.turntablerpm.sensor.EngineState
import com.roger.turntablerpm.profile.TurntableProfile
import com.roger.turntablerpm.sensor.Mode
import kotlin.math.abs

private val Orange = Color(0xFFCC6600)
private val Green = Color(0xFF2E7D32)
private val Blue = Color(0xFF1565C0)

/**
 * 量測主畫面。
 *
 * 量測一開始就切到 SpinningDialScreen（反向旋轉的盤面），所以這一頁在量測中
 * 其實看不到 —— 它是「開始之前」與「分析之後」的畫面。
 */
@Composable
fun MeasurementScreen(
    state: EngineState,
    available: Boolean,
    unavailableReason: String? = null,
    onStart: (Mode) -> Unit,
    onStop: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onShareExport: (String) -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    /** 使用中的唱盤。有規格就拿來比對，有傳動鏈尺寸就用來認出馬達那根峰。 */
    profile: TurntableProfile? = null,
    mode: Mode = Mode.AUTOMATIC,
    onModeChange: (Mode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("轉速量測", style = MaterialTheme.typography.headlineSmall)

        if (!available) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    unavailableReason ?: "這台裝置缺少陀螺儀或重力感測器，無法量測。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        SpeedReadout(state)

        // **自動是預設。** 手動模式要在盤面轉動時去點按鈕，那很難按。
        Text("模式", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == Mode.AUTOMATIC,
                onClick = { if (!state.running) onModeChange(Mode.AUTOMATIC) },
                label = { Text("自動") },
                enabled = !state.running,
            )
            FilterChip(
                selected = mode == Mode.MANUAL,
                onClick = { if (!state.running) onModeChange(Mode.MANUAL) },
                label = { Text("手動") },
                enabled = !state.running,
            )
        }
        Text(
            if (mode == Mode.AUTOMATIC) {
                "按下按鈕後把手機放上轉盤，程式會等轉速穩定才開始記錄，盤面停下時自動結束。"
            } else {
                "自己按開始與停止。記得先讓轉盤轉起來、手機放好，再按開始。"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = { if (state.running) onStop() else onStart(mode) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.running -> "停止"
                    mode == Mode.AUTOMATIC -> "準備好，開始偵測"
                    else -> "開始量測"
                },
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("放上唱盤之前", style = MaterialTheme.typography.titleSmall)
                Text(
                    "拿掉磁吸配件與含磁鐵的手機殼，鎖好唱臂。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "手機橫跨轉軸放在唱片鎮上，讓質心落在轉軸正上方 —— 這是最省事的擺法。" +
                        "沒有唱片鎮的話，手機右側長邊中點貼轉軸、機身放左半邊，" +
                        "對面放一個等重的東西配平。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "手機偏在一邊會拖慢轉速 0.3%、放大每圈一次的抖動三成。" +
                        "實測顯示「每圈一次」裡有一大半是手機造成的，不是唱盤的 —— " +
                        "想知道唱盤自己有多少，把手機轉 180° 再量一次，兩次的差就是手機的貢獻。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.running) RunningCard(state)
        if (state.analyzing) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("分析中…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        state.analysisFailureReason?.let { reason ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("分析不出來", style = MaterialTheme.typography.titleSmall, color = Orange)
                    Text(reason, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        state.analysis?.let {
            AnalysisCard(it, profile)
            AnalysisCharts(it)
        }
        state.exportPath?.let { ExportCard(it, onShareExport) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("碼錶校準", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.appliedFactor != null) "已校準" else "未校準",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.appliedFactor != null) Green else Orange,
                    )
                }
                Text(
                    state.appliedFactor?.let {
                        "倍率 k = %.5f，所有轉速讀數都已套用。".format(it)
                    } ?: "還沒校準。目前的偏差 % 是唱盤誤差與陀螺儀誤差相乘的結果，" +
                        "兩者分不開，還不能拿來調唱盤。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.appliedFactor != null) "重新校準" else "開始碼錶校準")
                }
            }
        }

        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text("量測歷史")
        }

        OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.fillMaxWidth()) {
            Text(profile?.let { "唱盤：${it.displayName}" } ?: "唱盤設定檔")
        }

        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text("說明")
        }

        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("取樣特性診斷")
        }
    }
}

/**
 * 原始資料匯出。
 *
 * **摘要數字診斷不出問題。** 這個 app 每一次真正查出原因的經驗都是靠逐樣本資料：
 * 取樣率為什麼是 107.9 而不是要求的 100、時間戳誠不誠實、譜峰是不是分析參數造成的。
 * 畫面上的數字看不出這些。
 *
 * 分析失敗時也會有檔案 —— 那正是最需要它的時候。
 */
@Composable
private fun ExportCard(path: String, onShare: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("原始資料", style = MaterialTheme.typography.titleSmall)
            Text(
                "這次量測的逐樣本資料已經存成 JSON（時間戳、角速度、重力向量）。" +
                    "傳到電腦上可以用 tools/analyze_export.py 重新分析，" +
                    "或是拿去跟別的量測工具對照。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            OutlinedButton(onClick = { onShare(path) }, modifier = Modifier.fillMaxWidth()) {
                Text("分享原始資料")
            }
        }
    }
}

@Composable
private fun SpeedReadout(state: EngineState) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.meanRPM?.let { "%.3f".format(it) } ?: "—",
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RPM", style = MaterialTheme.typography.titleMedium)
            // **未校準的偏差不能拿來調唱盤。** 那是唱盤誤差與陀螺儀誤差相乘的結果，
            // 兩者分不開，所以未校準時要明確標出來。
            if (state.appliedFactor != null) {
                Text("已校準", style = MaterialTheme.typography.titleMedium, color = Green)
            } else {
                Text("未校準", style = MaterialTheme.typography.titleMedium, color = Orange)
            }
        }
        if (state.nominal != null && state.errorPercent != null) {
            val e = state.errorPercent
            Text(
                "%s 轉  %+.3f%%".format(state.nominal.label, e),
                style = MaterialTheme.typography.headlineSmall,
                color = if (abs(e) <= 0.3) Green else Orange,
            )
        } else if (state.running) {
            Text("轉速尚未穩定或不在標稱範圍內", style = MaterialTheme.typography.bodyMedium, color = Orange)
        }
    }
}

@Composable
private fun RunningCard(state: EngineState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("經過時間 %.0f s".format(state.elapsedSeconds))
                Text("${state.revolutions} 圈")
                Text("${state.sampleCount} 筆")
            }
            state.stats?.let { s ->
                Text(
                    "取樣 %.2f Hz，抖動比 %.3f%%，長空隙 %d 次".format(
                        s.effectiveRateHz, s.jitterRatio * 100, s.longGaps,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "至少量 90 秒；想要準確的譜峰振幅就量 3 分鐘 —— " +
                    "1 分鐘的量測會低估約 8%，因為解析度不夠細，峰值落在兩個頻率格之間。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnalysisCard(a: MeasurementAnalysis, profile: TurntableProfile?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("抖晃率", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.3f".format(a.wowFlutter.wrmsPercent),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.fillMaxWidth(0.02f))
                Text("% WRMS", style = MaterialTheme.typography.bodyMedium)
            }
            StatRow("DIN 2σ 峰值", "%.3f %%".format(a.wowFlutter.peak2SigmaPercent))
            StatRow("每圈一次成分", "%.3f %%".format(a.onePerRevolutionPercent))
            StatRow("最強成分佔比", "%.0f %%".format(a.dominantPeakShare * 100))
            StatRow("平均轉速（切過）", "%.4f RPM".format(a.meanRPM))
            StatRow("分析時長", "%.1f s".format(a.durationSeconds))
            StatRow("重取樣頻率", "%.2f Hz".format(a.sampleRate))

            // 原廠規格的比對。0.09% 是好是壞，要看手冊寫幾 —— 沒有這個數字，
            // 抖晃率就只是一個無從判斷的浮點數。
            profile?.specWowFlutterPercent?.takeIf { it > 0 }?.let { spec ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                StatRow("原廠規格（${profile.displayName}）", "%.3f %%".format(spec))
                val ratio = a.wowFlutter.wrmsPercent / spec
                Text(
                    if (ratio <= 1) "在規格內（規格的 %.0f%%）。".format(ratio * 100)
                    else "超出規格 %.2f 倍。".format(ratio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ratio <= 1) Green else Orange,
                )
            }

            if (a.trimmedStartSeconds > 0.05 || a.trimmedEndSeconds > 0.05) {
                Text(
                    "已自動略過轉速不穩的區間：開頭 %.1f s、尾端 %.1f s。".format(
                        a.trimmedStartSeconds, a.trimmedEndSeconds,
                    ) + "下面所有數字都是剩下那一段算出來的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("問題出在哪", style = MaterialTheme.typography.titleMedium)
            if (a.peaks.isEmpty()) {
                Text(
                    "沒有找到顯著的週期性成分 —— 這是好事，代表沒有單一零件在主導誤差。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                for (peak in a.peaks.take(6)) {
                    Row(Modifier.height(4.dp)) {}
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "%.3f Hz".format(peak.frequencyHz),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                        Text("%.3f %%".format(peak.amplitudePercent), fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        interpretation(peak),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHarmonic(peak)) Orange else Color.Unspecified,
                    )
                    // 傳動比的比對**永遠顯示，不做二分判定**。
                    //
                    // 皮帶輪很小的時候，有效傳動比對皮帶厚度極度敏感：
                    // d=8.5 mm 配 t=0.5 mm，光是厚度就讓比值差 5.6%。用「符合／
                    // 不符合」的門檻會因為使用者量厚度差一點就整個消失，而
                    // 「預期 33.4×、量到 35.3×」這個資訊本身就有用 —— 它同時可能是
                    // 「尺寸量錯了」或「這根不是馬達」。
                    val ratio = profile?.expectedDriveRatio
                    if (ratio != null && !isHarmonic(peak) && peak.orderOfRotation > 3) {
                        val diff = (peak.orderOfRotation / ratio - 1) * 100
                        Text(
                            if (abs(diff) < 8) {
                                "符合這台盤的傳動比（%.1f×）—— 這是馬達。".format(ratio)
                            } else {
                                ("這台盤的傳動比是 %.1f×，量到 %.1f×（差 %+.0f%%）—— " +
                                    "可能是傳動鏈尺寸填得不夠準，也可能這根不是馬達。")
                                    .format(ratio, peak.orderOfRotation, diff)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (abs(diff) < 8) Blue else Color.Unspecified,
                        )
                    }
                }
                Row(Modifier.height(6.dp)) {}
                Text(
                    "整數倍 = 跟著盤面轉的東西（偏心、變形）。" +
                        "非整數倍 = 傳動鏈上轉速不同的零件（馬達、皮帶輪）。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
