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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.history.MeasurementRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Orange = Color(0xFFCC6600)

/**
 * 單一量測記錄的詳情。
 *
 * 列表只放得下最關鍵的幾個數字，但「這一次到底量到什麼」需要全部攤開 ——
 * 尤其是**備註**：調整前後的比較要有意義，得記得那一次做了什麼
 * （「換皮帶之後」「冷機」「45 轉」）。沒有備註，三個月後看到一排數字
 * 只知道有變，不知道為什麼變。
 */
@Composable
fun HistoryDetailScreen(
    record: MeasurementRecord,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stamp = remember(record.epochMillis) {
        SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.getDefault()).format(Date(record.epochMillis))
    }
    // 打字時不要每個字都寫回硬碟，離開欄位或按返回時才存。
    var note by remember(record.epochMillis) { mutableStateOf(record.note) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stamp, style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.detail_section_speed)) {
            StatRow(stringResource(R.string.detail_mean_rpm), "%.4f RPM".format(record.meanRPM))
            StatRow(stringResource(R.string.detail_raw_reading), "%.4f RPM".format(record.rawMeanRPM))
            record.nominalLabel?.let {
                StatRow(
                    stringResource(R.string.detail_nominal),
                    stringResource(R.string.nominal_rpm_label, it),
                )
            }
            record.errorPercent?.let {
                StatRow(stringResource(R.string.detail_error), "%+.3f %%".format(it))
            }
            val k = record.calibrationFactor
            if (k != null) {
                StatRow(stringResource(R.string.detail_applied_factor), "%.5f".format(k))
            } else {
                Text(
                    stringResource(R.string.detail_uncalibrated_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }
        }

        Section(stringResource(R.string.detail_section_wow)) {
            StatRow(stringResource(R.string.hist_weighted_wrms), "%.4f %%".format(record.wrmsPercent))
            StatRow(stringResource(R.string.detail_din_peak), "%.4f %%".format(record.peak2SigmaPercent))
            StatRow(stringResource(R.string.detail_one_per_rev), "%.4f %%".format(record.onePerRevPercent))
            StatRow(
                stringResource(R.string.hist_dominant_share),
                "%.0f %%".format(record.dominantPeakShare * 100),
            )
            record.peakAngleDegrees?.let {
                StatRow(stringResource(R.string.detail_peak_angle), "%.0f °".format(it))
            }
        }

        if (record.peaks.isNotEmpty()) {
            Section(stringResource(R.string.detail_section_peaks)) {
                for (p in record.peaks) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "%.3f Hz".format(p.frequencyHz),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            "%.4f %%".format(p.amplitudePercent),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        storedInterpretation(p),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.isHarmonic) Orange else Color.Unspecified,
                    )
                }
            }
        }

        Section(stringResource(R.string.detail_section_conditions)) {
            StatRow(stringResource(R.string.detail_duration), "%.0f s".format(record.durationSeconds))
            StatRow(stringResource(R.string.detail_revolutions), "${record.revolutions}")
            StatRow(stringResource(R.string.detail_sample_rate), "%.2f Hz".format(record.sampleRate))
            StatRow(stringResource(R.string.detail_rotation_hz), "%.4f Hz".format(record.rotationHz))
            if (record.trimmedSeconds > 0.05) {
                StatRow(stringResource(R.string.detail_trimmed), "%.1f s".format(record.trimmedSeconds))
            }
        }

        Section(stringResource(R.string.detail_section_note)) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; onNoteChange(it) },
                placeholder = { Text(stringResource(R.string.detail_note_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.detail_note_why),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detail_back_to_history))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
