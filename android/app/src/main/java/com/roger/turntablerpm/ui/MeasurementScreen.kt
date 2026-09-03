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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
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
    onReplayOnboarding: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
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
        Text(stringResource(R.string.meas_title), style = MaterialTheme.typography.headlineSmall)

        if (!available) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    unavailableReason ?: stringResource(R.string.meas_unavailable),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        SpeedReadout(state)

        // **自動是預設。** 手動模式要在盤面轉動時去點按鈕，那很難按。
        Text(stringResource(R.string.meas_mode), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == Mode.AUTOMATIC,
                onClick = { if (!state.running) onModeChange(Mode.AUTOMATIC) },
                label = { Text(stringResource(R.string.meas_mode_auto)) },
                enabled = !state.running,
            )
            FilterChip(
                selected = mode == Mode.MANUAL,
                onClick = { if (!state.running) onModeChange(Mode.MANUAL) },
                label = { Text(stringResource(R.string.meas_mode_manual)) },
                enabled = !state.running,
            )
        }
        Text(
            if (mode == Mode.AUTOMATIC) {
                stringResource(R.string.meas_auto_hint)
            } else {
                stringResource(R.string.meas_manual_hint)
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = { if (state.running) onStop() else onStart(mode) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.running -> stringResource(R.string.meas_stop)
                    mode == Mode.AUTOMATIC -> stringResource(R.string.meas_start_auto)
                    else -> stringResource(R.string.meas_start_manual)
                },
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.meas_before_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.meas_before_magnets),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.meas_before_placement),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.meas_before_offcentre),
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
                    Text(stringResource(R.string.meas_analyzing), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        state.analysisFailureReason?.let { reason ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.meas_analysis_failed),
                        style = MaterialTheme.typography.titleSmall, color = Orange,
                    )
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
                    Text(stringResource(R.string.cal_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.appliedFactor != null) stringResource(R.string.meas_cal_calibrated)
                        else stringResource(R.string.meas_cal_uncalibrated),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.appliedFactor != null) Green else Orange,
                    )
                }
                Text(
                    state.appliedFactor?.let {
                        stringResource(R.string.meas_cal_applied, it)
                    } ?: stringResource(R.string.meas_cal_none),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.appliedFactor != null) stringResource(R.string.meas_cal_redo)
                        else stringResource(R.string.meas_cal_start),
                    )
                }
            }
        }

        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meas_history))
        }

        OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.fillMaxWidth()) {
            Text(
                profile?.let {
                    stringResource(
                        R.string.meas_profile_named,
                        it.displayName.ifBlank { stringResource(R.string.profile_untitled) },
                    )
                } ?: stringResource(R.string.meas_profile_none),
            )
        }

        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meas_about))
        }

        // 導覽看完就再也進不去的話，那幾頁等於只存在一次 —— 而擺法那一頁
        // 是使用者最常需要回去確認的東西。
        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meas_replay_onboarding))
        }

        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meas_sampling_diagnostics))
        }

        OutlinedButton(onClick = onOpenAdvanced, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meas_advanced_diagnostics))
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
            Text(stringResource(R.string.meas_raw_data), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.meas_raw_data_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            OutlinedButton(onClick = { onShare(path) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.share_raw_data))
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
                Text(
                    stringResource(R.string.meas_cal_calibrated),
                    style = MaterialTheme.typography.titleMedium, color = Green,
                )
            } else {
                Text(
                    stringResource(R.string.meas_cal_uncalibrated),
                    style = MaterialTheme.typography.titleMedium, color = Orange,
                )
            }
        }
        if (state.nominal != null && state.errorPercent != null) {
            val e = state.errorPercent
            Text(
                stringResource(R.string.meas_nominal_and_error, state.nominal.label, e),
                style = MaterialTheme.typography.headlineSmall,
                color = if (abs(e) <= 0.3) Green else Orange,
            )
        } else if (state.running) {
            Text(
                stringResource(R.string.meas_rpm_unstable),
                style = MaterialTheme.typography.bodyMedium, color = Orange,
            )
        }
    }
}

@Composable
private fun RunningCard(state: EngineState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.meas_elapsed, state.elapsedSeconds))
                Text(stringResource(R.string.meas_revs, state.revolutions))
                Text(stringResource(R.string.meas_samples, state.sampleCount))
            }
            state.stats?.let { s ->
                Text(
                    stringResource(
                        R.string.meas_sampling_line,
                        s.effectiveRateHz, s.jitterRatio * 100, s.longGaps,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.meas_duration_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnalysisCard(a: MeasurementAnalysis, profile: TurntableProfile?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.meas_wow_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.3f".format(a.wowFlutter.wrmsPercent),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.fillMaxWidth(0.02f))
                Text(stringResource(R.string.meas_wrms_unit), style = MaterialTheme.typography.bodyMedium)
            }
            StatRow(
                stringResource(R.string.meas_din_peak),
                "%.3f %%".format(a.wowFlutter.peak2SigmaPercent),
            )
            StatRow(
                stringResource(R.string.meas_one_per_rev),
                "%.3f %%".format(a.onePerRevolutionPercent),
            )
            StatRow(
                stringResource(R.string.meas_dominant_share),
                "%.0f %%".format(a.dominantPeakShare * 100),
            )
            StatRow(stringResource(R.string.meas_mean_trimmed), "%.4f RPM".format(a.meanRPM))
            StatRow(stringResource(R.string.meas_analysis_duration), "%.1f s".format(a.durationSeconds))
            StatRow(stringResource(R.string.meas_resample_rate), "%.2f Hz".format(a.sampleRate))

            // 原廠規格的比對。0.09% 是好是壞，要看手冊寫幾 —— 沒有這個數字，
            // 抖晃率就只是一個無從判斷的浮點數。
            profile?.specWowFlutterPercent?.takeIf { it > 0 }?.let { spec ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                StatRow(
                    stringResource(
                        R.string.meas_spec_row,
                        profile.displayName.ifBlank { stringResource(R.string.profile_untitled) },
                    ),
                    "%.3f %%".format(spec),
                )
                val ratio = a.wowFlutter.wrmsPercent / spec
                Text(
                    if (ratio <= 1) stringResource(R.string.meas_within_spec, ratio * 100)
                    else stringResource(R.string.meas_over_spec, ratio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ratio <= 1) Green else Orange,
                )
            }

            if (a.trimmedStartSeconds > 0.05 || a.trimmedEndSeconds > 0.05) {
                Text(
                    stringResource(
                        R.string.meas_trimmed, a.trimmedStartSeconds, a.trimmedEndSeconds,
                    ) + stringResource(R.string.meas_trimmed_tail),
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(stringResource(R.string.meas_where_problem), style = MaterialTheme.typography.titleMedium)
            if (a.peaks.isEmpty()) {
                Text(
                    stringResource(R.string.meas_no_peaks),
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
                                stringResource(R.string.meas_is_the_motor, ratio)
                            } else {
                                stringResource(
                                    R.string.meas_ratio_mismatch,
                                    ratio, peak.orderOfRotation, diff,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (abs(diff) < 8) Blue else Color.Unspecified,
                        )
                    }
                }
                Row(Modifier.height(6.dp)) {}
                Text(
                    stringResource(R.string.meas_harmonic_legend),
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
