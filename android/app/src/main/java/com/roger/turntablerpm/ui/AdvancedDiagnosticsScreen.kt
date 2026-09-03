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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.core.CalibrationConfidence
import com.roger.turntablerpm.core.ScaleCalibrator
import com.roger.turntablerpm.core.SpeedStatistics
import com.roger.turntablerpm.core.Vector3
import com.roger.turntablerpm.sensor.EngineState

private val Orange = Color(0xFFCC6600)

/**
 * 進階診斷。
 *
 * **這一頁的每一區都是「已經證實失敗」的自動校準嘗試。** 留著不是因為將來會成功，
 * 而是因為它們解釋了「為什麼唯一可信的校準是碼錶」——
 * 而那個結論如果只寫在文件裡，下一個人（包括三個月後的自己）會再走一次。
 *
 * 兩條路徑各自的失敗方式不同：
 *
 * - **融合路徑**（`TYPE_ROTATION_VECTOR` 的方位角）是同義反覆。盤面高速轉動時
 *   系統把磁修正降權到幾乎沒有貢獻，方位角退化成陀螺儀積分本身，
 *   於是不管陀螺儀準不準都會吐出 k ≈ 1（CLAUDE.md 坑 11）。
 * - **磁力計路徑**理論上獨立，但對擺放位置極度敏感：本地磁場只要蓋過地磁水平分量，
 *   圓就包不住原點、角度只能來回擺盪（坑 13）；就算繞得起來，房間裡靜止磁源的
 *   空間梯度還會造成每圈一次的失真（坑 15）。
 */
@Composable
fun AdvancedDiagnosticsScreen(
    state: EngineState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.meas_advanced_diagnostics),
            style = MaterialTheme.typography.headlineSmall,
        )
        Card(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.adv_disclaimer),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Orange,
            )
        }

        Section(stringResource(R.string.adv_sec_sensors)) {
            StatRow(stringResource(R.string.adv_instant_omega), "%.3f °/s".format(state.latestOmega))
            StatRow(
                stringResource(R.string.adv_mean_calibrated),
                state.meanRPM?.let { "%.4f RPM".format(it) } ?: "—",
            )
            StatRow(
                stringResource(R.string.adv_mean_raw),
                state.rawMeanRPM?.let { "%.4f RPM".format(it) } ?: "—",
            )
            StatRow(
                stringResource(R.string.adv_actual_rate),
                state.stats?.let { "%.1f Hz".format(it.effectiveRateHz) } ?: "—",
            )
            StatRow(stringResource(R.string.adv_phase), "%.1f °".format(state.phaseDegrees))
            StatRow(stringResource(R.string.adv_gyro_total), "%.0f °".format(state.gyroTotalDegrees))
            StatRow(stringResource(R.string.adv_mag_total), "%.0f °".format(state.magneticTotalDegrees))
            StatRow(
                stringResource(R.string.adv_mag_yaw),
                state.magneticYawDegrees?.let { "%.1f °".format(it) } ?: "—",
            )
            VectorRow(stringResource(R.string.adv_gravity_vector), state.gravityVector, "m/s²", 3)
            VectorRow(stringResource(R.string.adv_rotation_rate), state.rotationRate, "rad/s", 3)
            VectorRow(stringResource(R.string.adv_field_calibrated), state.calibratedField, "µT", 1)
            VectorRow(stringResource(R.string.adv_field_raw), state.rawField, "µT", 1)
            StatRow(stringResource(R.string.adv_mag_samples), "${state.rawMagneticSampleCount}")
        }

        // ── 融合路徑 ──────────────────────────────────────────
        Section(stringResource(R.string.adv_sec_fused)) {
            Text(
                stringResource(R.string.meas_revs, state.revolutions),
                style = MaterialTheme.typography.bodySmall,
            )
            val k = state.fusedCalibration
            if (k == null) {
                Text(
                    stringResource(R.string.adv_no_factor_yet),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                StatRow(stringResource(R.string.cal_factor_k), "%.5f".format(k))
                StatRow(
                    stringResource(R.string.adv_gyro_error),
                    "%+.3f %%".format((1.0 / k - 1.0) * 100),
                )
                state.rawMeanRPM?.let { raw ->
                    val corrected = raw * k
                    StatRow(stringResource(R.string.adv_corrected_rpm), "%.4f RPM".format(corrected))
                    SpeedStatistics.classify(corrected)?.let { nominal ->
                        StatRow(
                            stringResource(R.string.adv_corrected_error),
                            "%+.3f %%".format(SpeedStatistics.errorPercent(corrected, nominal)),
                        )
                    }
                }
                // **可信度說明只在「算得出倍率」時才貼。** 算不出來的時候
                // 上面那句「還沒滿一圈」已經說完了，再貼一次同樣的話只是雜訊。
                ConfidenceNote(state)
            }
        }

        // ── 磁力計路徑 ────────────────────────────────────────
        Section(stringResource(R.string.adv_sec_magnetic)) {
            Text(
                stringResource(
                    R.string.adv_revs_and_samples,
                    state.magneticRevolutions, state.magneticSampleCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            StatRow(stringResource(R.string.adv_geomag_total), "%.0f °".format(state.magneticTotal))
            StatRow(stringResource(R.string.adv_horizontal), "%.1f µT".format(state.magneticHorizontal))
            val range = state.magneticHorizontalRange
            if (range != null) {
                StatRow(
                    stringResource(R.string.adv_horizontal_range),
                    "%.1f / %.1f µT".format(range.first, range.second),
                )
                // 誰是半徑、誰是圓心偏移，由「有沒有繞圈」決定 —— 圓要包住原點才繞得起來。
                if (state.magneticRevolutions >= 1) {
                    StatRow(stringResource(R.string.adv_geomag_radius), "%.1f µT".format(range.first))
                    StatRow(stringResource(R.string.adv_local_field), "%.1f µT".format(range.second))
                } else {
                    StatRow(stringResource(R.string.adv_local_field), "%.1f µT".format(range.first))
                    StatRow(stringResource(R.string.adv_geomag_radius), "%.1f µT".format(range.second))
                    Text(
                        stringResource(R.string.adv_local_field_wins),
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange,
                    )
                }
            }
            StatRow(stringResource(R.string.adv_field_accuracy), state.fieldAccuracy)
            state.magneticTotal.takeIf { it > 0 && state.gyroTotalDegrees > 0 }?.let {
                StatRow(stringResource(R.string.cal_factor_k), "%.5f".format(it / state.gyroTotalDegrees))
            }
        }

        Section(stringResource(R.string.adv_sec_refined)) {
            val refined = state.refined
            if (refined == null) {
                Text(
                    stringResource(R.string.adv_refined_pending),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                StatRow(stringResource(R.string.adv_geomag_total), "%.0f °".format(refined.totalDegrees))
                StatRow(stringResource(R.string.adv_revolutions), "${refined.revolutions}")
                StatRow(stringResource(R.string.adv_fit_radius), "%.1f µT".format(refined.radius))
                StatRow(stringResource(R.string.adv_fit_centre), "%.1f µT".format(refined.centerOffset))
                StatRow(
                    stringResource(R.string.adv_fit_residual),
                    stringResource(
                        R.string.adv_residual_value,
                        refined.residual,
                        if (refined.radius > 0) refined.residual / refined.radius * 100 else 0.0,
                    ),
                )
                if (state.gyroTotalDegrees > 0) {
                    val k = refined.totalDegrees / state.gyroTotalDegrees
                    StatRow(stringResource(R.string.cal_factor_k), "%.5f".format(k))
                    StatRow(
                        stringResource(R.string.adv_gyro_error),
                        "%+.3f %%".format((1.0 / k - 1.0) * 100),
                    )
                }
                Text(
                    if (refined.isTrustworthy) stringResource(R.string.adv_fit_trustworthy)
                    else stringResource(R.string.adv_fit_untrustworthy),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (refined.isTrustworthy) Color.Unspecified else Orange,
                )
            }
        }

        Section(stringResource(R.string.adv_sec_raw_mag)) {
            Text(
                stringResource(
                    R.string.adv_revs_and_samples,
                    state.rawMagneticRevolutions, state.rawMagneticSampleCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            StatRow(stringResource(R.string.adv_geomag_total), "%.0f °".format(state.rawMagneticTotal))
            state.rawRefined?.let { r ->
                StatRow(stringResource(R.string.adv_refined_total), "%.0f °".format(r.totalDegrees))
                StatRow(stringResource(R.string.adv_fit_radius), "%.1f µT".format(r.radius))
                StatRow(stringResource(R.string.adv_fit_centre), "%.1f µT".format(r.centerOffset))
                if (state.gyroTotalDegrees > 0) {
                    StatRow(
                        stringResource(R.string.cal_factor_k),
                        "%.5f".format(r.totalDegrees / state.gyroTotalDegrees),
                    )
                }
            }
            Text(
                stringResource(R.string.adv_raw_mag_note),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
        }
    }
}

/**
 * 倍率能不能採信。
 *
 * **判準是兩條路徑有沒有真的分歧，不是圈數。** 舊版 iOS UI 用「圈數 ≥ 30」當判準，
 * 結果在 36 圈時對著一個「無法區分對錯」的數字說「可以參考了」。
 */
@Composable
private fun ConfidenceNote(state: EngineState) {
    val confidence = ScaleCalibrator.confidence(
        gyroTotalDegrees = state.gyroTotalDegrees,
        magneticTotalDegrees = state.magneticTotalDegrees,
        revolutions = state.revolutions,
    )
    val text = when (confidence) {
        is CalibrationConfidence.Insufficient ->
            stringResource(R.string.adv_conf_insufficient)
        is CalibrationConfidence.Indistinguishable ->
            stringResource(
                R.string.adv_conf_indistinguishable,
                confidence.divergenceDegrees, confidence.noiseFloorDegrees,
            )
        is CalibrationConfidence.Usable ->
            stringResource(R.string.adv_conf_usable, confidence.precision * 100)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (confidence.isUsable) Color.Unspecified else Orange,
    )
}

@Composable
private fun VectorRow(label: String, v: Vector3?, unit: String, digits: Int) {
    StatRow(
        label,
        if (v == null) "—"
        else "%.${digits}f, %.${digits}f, %.${digits}f $unit".format(v.x, v.y, v.z),
    )
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
