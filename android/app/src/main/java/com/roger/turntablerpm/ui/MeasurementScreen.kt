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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    onOpenAnalysis: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    /** 使用中的唱盤。有規格就拿來比對，有傳動鏈尺寸就用來認出馬達那根峰。 */
    profile: TurntableProfile? = null,
    mode: Mode = Mode.AUTOMATIC,
    onModeChange: (Mode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 有沒有可以拿來校準、匯出或分析的結果。
    val hasMeasurement = (state.rawMeanRPM ?: 0.0) > 0

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

        // **安全提醒只在「還沒量過」時出現。** 量完之後使用者要看的是結果，
        // 而那三段字每次都佔掉一整螢幕。完整的擺法與安全說明在說明頁裡，
        // 所以這張橫幅點下去就是說明頁。
        if (!state.running && !hasMeasurement) SafetyBanner(onOpenAbout)

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
        // **量測摘要放在分析之前。** 那是使用者每次量完最先想確認的東西
        // （轉速、圈數、取樣率），而分析是要花時間讀的。
        if (hasMeasurement) SummaryCard(state)

        state.analysis?.let { AnalysisLink(it, onOpenAnalysis) }
        state.exportPath?.let { ExportRow(state.sampleCount, it, onShareExport) }

        NavRow(Icons.Filled.List, stringResource(R.string.meas_history_row), onOpenHistory)
        NavRow(
            Icons.Filled.Settings,
            profile?.let {
                stringResource(
                    R.string.meas_profile_named,
                    it.displayName.ifBlank { stringResource(R.string.profile_untitled) },
                )
            } ?: stringResource(R.string.meas_profile_row),
            onOpenProfiles,
        )

        // **校準放在最下面。** 它是設定性質的，一台裝置做一次就好；
        // 上面那些是每次量測都會碰的。iOS 端也是這個順序。
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

        NavRow(
            Icons.Filled.Search,
            stringResource(R.string.meas_sampling_diagnostics),
            onOpenDiagnostics,
        )
        NavRow(
            Icons.Filled.Build,
            stringResource(R.string.meas_advanced_diagnostics),
            onOpenAdvanced,
        )
    }
}

/**
 * 放上唱盤之前的安全提醒。
 *
 * **一行，而且可以點進說明頁。** 完整的擺法、配平、磁鐵、78 轉離心力那些
 * 都在說明頁裡；主畫面只需要一句話加一個入口。三段長文字放在這裡的話，
 * 每次打開 app 都要捲過它才看得到按鈕。量完之後就收起來 ——
 * 那時使用者要看的是結果。
 */
@Composable
private fun SafetyBanner(onOpenAbout: () -> Unit) {
    Card(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Orange)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.meas_before_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.meas_safety_short),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 這次量測的原始數字。分析是要花時間讀的，這幾個是一眼確認用的。 */
@Composable
private fun SummaryCard(state: EngineState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.meas_this_run), style = MaterialTheme.typography.titleMedium)
            StatRow(
                stringResource(R.string.meas_mean_speed),
                state.meanRPM?.let { "%.4f RPM".format(it) } ?: "—",
            )
            StatRow(stringResource(R.string.meas_run_duration), "%.1f s".format(state.elapsedSeconds))
            StatRow(stringResource(R.string.meas_total_revs), "${state.revolutions}")
            StatRow(stringResource(R.string.meas_sample_count), "${state.sampleCount}")
            StatRow(
                stringResource(R.string.meas_sample_rate),
                state.stats?.let { "%.1f Hz".format(it.effectiveRateHz) } ?: "—",
            )
        }
    }
}

/**
 * 分析結果的入口。
 *
 * **只放一行摘要，內容在獨立的一頁。** 分析有三張圖加上譜峰判讀，接在主畫面
 * 下面的話每次打開 app 都要捲過去。而這一行（抖晃率＋每圈一次）已經足夠
 * 判斷「這次要不要點進去看」。
 */
@Composable
private fun AnalysisLink(a: MeasurementAnalysis, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = Blue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.meas_analysis_result),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        R.string.meas_analysis_summary,
                        a.wowFlutter.wrmsPercent, a.onePerRevolutionPercent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 原始資料匯出。
 *
 * **摘要數字診斷不出問題。** 這個 app 每一次真正查出原因都是靠逐樣本資料 ——
 * iOS 端是磁場的空間失真，Android 端是取樣率為什麼是 107.9 而不是要求的 100。
 * 分析失敗時也會有檔案，那正是最需要它的時候。
 */
@Composable
private fun ExportRow(sampleCount: Int, path: String, onShare: (String) -> Unit) {
    OutlinedButton(onClick = { onShare(path) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.meas_export_with_count, sampleCount))
    }
}

/** 導覽列：圖示 + 標籤 + 右箭頭。跟 iOS 的 NavigationLink 同一個視覺語彙。 */
@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
internal fun AnalysisCard(a: MeasurementAnalysis, profile: TurntableProfile?) {
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
                    // **不要在程式碼裡串接句子。** 分隔符本身是語言相關的 ——
                    // 中文句尾是「。」不用空格，英文需要。串接出來就是
                    // 「2.4 s at the end.Every number below…」。整句放進一條字串。
                    stringResource(
                        R.string.meas_trimmed, a.trimmedStartSeconds, a.trimmedEndSeconds,
                    ),
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
