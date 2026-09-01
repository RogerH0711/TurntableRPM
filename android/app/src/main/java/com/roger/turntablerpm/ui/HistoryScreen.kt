package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.history.MeasurementRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Orange = Color(0xFFCC6600)

/**
 * 量測歷史。
 *
 * 這個畫面存在的理由是**比較**：調整前後有沒有變好，換一個擺法之後 1× 有沒有變。
 * 所以列表直接把關鍵數字攤開（轉速、W&F、每圈一次），不必點進去才看得到。
 *
 * **每圈一次特別標出來** —— 它是唯一會隨手機擺法改變的成分，
 * 「手機轉 180° 再量一次」這種實驗全靠比較它。
 */
@Composable
fun HistoryScreen(
    records: List<MeasurementRecord>,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = remember { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("量測歷史", style = MaterialTheme.typography.headlineSmall)

        if (records.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "還沒有量測記錄。完成一次分析之後會自動存進來 —— 不需要手動按儲存。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                "共 ${records.size} 筆，由新到舊。所有百分比都不受校準影響" +
                    "（那些是比值，陀螺儀的比例因子誤差會抵消）；只有 RPM 需要校準。",
                style = MaterialTheme.typography.bodySmall,
            )
            for (r in records) {
                RecordCard(r, format.format(Date(r.epochMillis))) { onDelete(r.epochMillis) }
            }
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("清除全部記錄")
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("回到量測")
        }
    }
}

@Composable
private fun RecordCard(r: MeasurementRecord, timestamp: String, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "%.3f RPM".format(r.meanRPM),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    r.errorPercent?.let { "%+.3f%%".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$timestamp · %.0f s".format(r.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!r.isCalibrated) {
                    Text("未校準", style = MaterialTheme.typography.bodySmall, color = Orange)
                }
            }
            StatRow("加權 WRMS", "%.4f %%".format(r.wrmsPercent))
            StatRow("每圈一次（會隨擺法變）", "%.4f %%".format(r.onePerRevPercent))
            StatRow("最強成分佔比", "%.0f %%".format(r.dominantPeakShare * 100))
            if (r.trimmedSeconds > 0.05) {
                Text(
                    "切掉了 %.1f s 轉速不穩的區間".format(r.trimmedSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }
            if (r.peaks.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                for (p in r.peaks.take(4)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "%.3f Hz  (%.2f×)".format(p.frequencyHz, p.orderOfRotation),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (p.isHarmonic) Orange else Color.Unspecified,
                        )
                        Text(
                            "%.4f %%".format(p.amplitudePercent),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            TextButton(onClick = onDelete) { Text("刪除這一筆") }
        }
    }
}
