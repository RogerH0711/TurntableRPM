package com.roger.turntablerpm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.calibration.CalibrationStore
import com.roger.turntablerpm.core.StopwatchCalibration

private val Orange = Color(0xFFCC6600)

/**
 * 碼錶校準。
 *
 * **為什麼是碼錶而不是自動校準。** iOS 端把指南針自動校準走過兩條路都失敗了：
 * `attitude.yaw` 是融合結果，拿它校準陀螺儀是同義反覆；原始磁力計則被每圈一次的
 * 空間磁場失真蓋掉（失真振幅 29.9 µT 大過訊號振幅 26.0 µT）。碼錶反而乾淨：
 * 100 圈搭配 ±0.3 秒的人為誤差就是 0.17%，而且完全不經過手機的任何感測器。
 */
@Composable
fun CalibrationScreen(
    measuredRPM: Double?,
    current: StopwatchCalibration?,
    mismatched: StopwatchCalibration?,
    onSave: (StopwatchCalibration) -> Boolean,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revolutions by remember { mutableStateOf("100") }
    var seconds by remember { mutableStateOf("") }

    val rev = revolutions.toIntOrNull()
    val secs = seconds.toDoubleOrNull()
    val candidate = if (rev != null && secs != null && measuredRPM != null) {
        StopwatchCalibration.create(rev, secs, measuredRPM, CalibrationStore.deviceModel)
    } else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.cal_title), style = MaterialTheme.typography.headlineSmall)

        mismatched?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.cal_invalidated_title),
                        style = MaterialTheme.typography.titleSmall, color = Orange,
                    )
                    Text(
                        stringResource(
                            R.string.cal_invalidated_body,
                            it.deviceModel, CalibrationStore.deviceModel,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        current?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.cal_active_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    StatRow(stringResource(R.string.cal_factor_k), "%.5f".format(it.factor))
                    StatRow(
                        stringResource(R.string.cal_gyro_error),
                        "%+.3f %%".format((1.0 / it.factor - 1.0) * 100),
                    )
                    StatRow(
                        stringResource(R.string.cal_based_on),
                        stringResource(R.string.cal_revs_over_seconds, it.revolutions, it.seconds),
                    )
                    StatRow(
                        stringResource(R.string.cal_precision),
                        "±%.3f %%".format(it.precision() * 100),
                    )
                    OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cal_clear))
                    }
                }
            }
        }

        if (measuredRPM == null) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.cal_needs_measurement),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.cal_this_calibration),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = revolutions,
                            onValueChange = { revolutions = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.cal_revolutions)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = seconds,
                            onValueChange = { seconds = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.cal_seconds)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    StatRow(stringResource(R.string.cal_app_reading), "%.4f RPM".format(measuredRPM))
                    if (candidate == null) {
                        Text(
                            stringResource(R.string.cal_fill_in_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        StatRow(
                            stringResource(R.string.cal_stopwatch_rpm),
                            "%.4f RPM".format(candidate.trueRPM),
                        )
                        StatRow(stringResource(R.string.cal_factor_k), "%.5f".format(candidate.factor))
                        StatRow(
                            stringResource(R.string.cal_gyro_error),
                            "%+.3f %%".format((1.0 / candidate.factor - 1.0) * 100),
                        )
                        StatRow(
                            stringResource(R.string.cal_this_precision),
                            "±%.3f %%".format(candidate.precision() * 100),
                        )

                        if (!candidate.isPlausible) {
                            Text(
                                stringResource(R.string.cal_implausible, candidate.factor) +
                                    stringResource(R.string.cal_implausible_tail),
                                style = MaterialTheme.typography.bodySmall,
                                color = Orange,
                            )
                        }
                        Button(
                            onClick = { onSave(candidate) },
                            enabled = candidate.isPlausible,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.cal_save)) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.cal_how_to_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.cal_how_to), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(
                    stringResource(R.string.cal_precision_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.cal_same_run), style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
        }
    }
}
