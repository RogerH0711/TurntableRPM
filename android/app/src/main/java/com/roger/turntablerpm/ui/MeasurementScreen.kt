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
import kotlin.math.abs

private val Orange = Color(0xFFCC6600)
private val Green = Color(0xFF2E7D32)

/**
 * 量測主畫面。
 *
 * iOS 版在量測中會把整個畫面**反向旋轉**，讓內容在轉動中看起來靜止 ——
 * 那需要處理內接圓約束與文字方向（規格 §6.2）。Android 版還沒做，
 * 目前的作法是量完拿起手機再看。
 */
@Composable
fun MeasurementScreen(
    state: EngineState,
    available: Boolean,
    unavailableReason: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenCalibration: () -> Unit,
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

        Button(
            onClick = { if (state.running) onStop() else onStart() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.running) "停止" else "開始量測")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("放上唱盤之前", style = MaterialTheme.typography.titleSmall)
                Text(
                    "拿掉磁吸配件與含磁鐵的手機殼、鎖好唱臂、墊一張唱片或用唱片鎮再放手機。" +
                        "手機偏在一邊會拖慢轉速並放大抖動 —— 放在轉軸正中央，或對面放等重的東西配平。",
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
        state.analysis?.let { AnalysisCard(it) }

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

        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("取樣特性診斷")
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
private fun AnalysisCard(a: MeasurementAnalysis) {
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
