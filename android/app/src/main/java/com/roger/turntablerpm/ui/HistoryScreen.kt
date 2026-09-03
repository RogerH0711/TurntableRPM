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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
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
    onOpen: (Long) -> Unit,
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
        Text(stringResource(R.string.hist_title), style = MaterialTheme.typography.headlineSmall)

        if (records.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.hist_empty),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                stringResource(R.string.hist_count_and_note, records.size),
                style = MaterialTheme.typography.bodySmall,
            )
            // 趨勢圖放在列表之前：使用者打開歷史是為了看「有沒有變好」，
            // 那個答案該第一眼就看到，不是往下捲十筆記錄之後才拼出來。
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.hist_trend), style = MaterialTheme.typography.titleMedium)
                    TrendChart(records)
                }
            }
            for (r in records) {
                RecordCard(
                    r, format.format(Date(r.epochMillis)),
                    onOpen = { onOpen(r.epochMillis) },
                    onDelete = { onDelete(r.epochMillis) },
                )
            }
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hist_clear_all))
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
        }
    }
}

@Composable
private fun RecordCard(
    r: MeasurementRecord,
    timestamp: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
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
                    Text(
                        stringResource(R.string.hist_uncalibrated),
                        style = MaterialTheme.typography.bodySmall, color = Orange,
                    )
                }
            }
            StatRow(stringResource(R.string.hist_weighted_wrms), "%.4f %%".format(r.wrmsPercent))
            StatRow(stringResource(R.string.hist_one_per_rev), "%.4f %%".format(r.onePerRevPercent))
            StatRow(
                stringResource(R.string.hist_dominant_share),
                "%.0f %%".format(r.dominantPeakShare * 100),
            )
            if (r.trimmedSeconds > 0.05) {
                Text(
                    stringResource(R.string.hist_trimmed, r.trimmedSeconds),
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
            if (r.note.isNotBlank()) {
                Text(
                    r.note,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.hist_detail_and_note)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.hist_delete_one)) }
            }
        }
    }
}
