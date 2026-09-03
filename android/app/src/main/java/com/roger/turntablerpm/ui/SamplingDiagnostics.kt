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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.sensor.EngineState

/**
 * 取樣特性診斷。
 *
 * 交接文件 §5 指出這一版真正的工程難點是 Android 的取樣率只是建議值、
 * 實際頻率由廠商實作決定 —— 所以要把各裝置的實際取樣率與 jitter 量出來並記錄，
 * 那是 README 裡「真的碰過硬體」的證據。
 *
 * 手機**靜止不動也能量**：陀螺儀照樣以設定的速率送事件，取樣特性跟有沒有轉無關。
 */
@Composable
fun SamplingDiagnostics(
    state: EngineState,
    available: Boolean,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var periodUs by remember { mutableIntStateOf(10_000) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.meas_sampling_diagnostics),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.samp_intro),
            style = MaterialTheme.typography.bodySmall,
        )

        if (!available) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.meas_unavailable),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        // 刻意不提供 >200 Hz —— Android 12 起那需要 HIGH_SAMPLING_RATE_SENSORS 權限，
        // 而這個 app 不需要那麼快（白頻譜假設下 50 Hz 以上只佔加權能量 0.72%）。
        Text(stringResource(R.string.samp_requested_rate), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20_000 to "50 Hz", 10_000 to "100 Hz", 5_000 to "200 Hz").forEach { (us, label) ->
                FilterChip(
                    selected = periodUs == us,
                    onClick = { if (!state.running) periodUs = us },
                    label = { Text(label) },
                    enabled = !state.running,
                )
            }
        }

        Button(
            onClick = { if (state.running) onStop() else onStart(periodUs) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.running) stringResource(R.string.meas_stop)
                else stringResource(R.string.meas_start_manual),
            )
        }

        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        StatsCard(state)
        SensorCard(state)

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_measure))
        }
    }
}

@Composable
fun StatsCard(state: EngineState) {
    // **這一頁只看 samplingStats。** `stats` 是量測那條在用的，跑診斷不能碰
    // —— 碰了主畫面的「這次量測」就會被這一輪的數字蓋掉。
    val s = state.samplingStats
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.samp_section), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (s != null) "%.2f".format(s.effectiveRateHz) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.fillMaxWidth(0.02f))
                Text(stringResource(R.string.samp_hz_actual), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.height(6.dp)) {}
            StatRow(stringResource(R.string.samp_count), s?.count?.toString() ?: "—")
            StatRow(
                stringResource(R.string.samp_duration),
                if (s != null) "%.1f s".format(s.durationSeconds) else "—",
            )
            StatRow(
                stringResource(R.string.samp_median_interval),
                if (s != null) "%.3f ms".format(s.medianIntervalMs) else "—",
            )
            StatRow(
                stringResource(R.string.samp_stddev_interval),
                if (s != null) "%.3f ms".format(s.stdDevIntervalMs) else "—",
            )
            StatRow(
                stringResource(R.string.samp_jitter),
                if (s != null) "%.3f %%".format(s.jitterRatio * 100) else "—",
            )
            StatRow(
                stringResource(R.string.samp_min_max),
                if (s != null) "%.2f / %.2f ms".format(s.minIntervalMs, s.maxIntervalMs) else "—",
            )
            StatRow(
                stringResource(R.string.samp_long_gaps),
                if (s != null) stringResource(R.string.samp_gap_count, s.longGaps) else "—",
            )
            StatRow(
                stringResource(R.string.samp_worst_gap),
                if (s != null) "%.2f×".format(s.worstGapRatio) else "—",
            )

            Row(Modifier.height(6.dp)) {}
            // **最關鍵的一項。** 實際取樣率比要求值高 7.9% 有兩種可能：感測器真的比較快
            // （時間戳誠實，無害），或時間戳跑在快 7.9% 的時鐘上（頻域結果全錯）。
            // 平均轉速兩種情況都看不出差別，只有拿牆鐘對照才分得開。
            StatRow(stringResource(R.string.samp_wall_clock), "%.2f s".format(state.wallElapsedSeconds))
            StatRow(
                stringResource(R.string.samp_clock_ratio),
                state.clockRatio?.let { "%.5f".format(it) } ?: "—",
            )
            // **比值需要夠長的時間才有意義。** 第一筆事件的時間戳與牆鐘讀數之間有幾毫秒的
            // 派送延遲，那個固定偏移除以短時距會被放大成假的漂移 ——
            // 實測 0.69 秒的量測會顯示 1.00547 並誤報「時基有問題」。
            val ratioTrustworthy = state.wallElapsedSeconds >= 10.0
            state.clockRatio?.takeIf { ratioTrustworthy }?.let { r ->
                Text(
                    if (kotlin.math.abs(r - 1.0) < 0.002) {
                        stringResource(R.string.samp_clock_honest, (r - 1) * 100)
                    } else {
                        stringResource(R.string.samp_clock_dishonest, (r - 1) * 100)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.clockRatio != null && !ratioTrustworthy) {
                Text(
                    stringResource(R.string.samp_need_10s),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(Modifier.height(6.dp)) {}
            Text(
                stringResource(R.string.samp_ios_baseline),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun SensorCard(state: EngineState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.samp_sensors_section),
                style = MaterialTheme.typography.titleMedium,
            )
            StatRow(stringResource(R.string.samp_instant_rpm), "%.3f RPM".format(state.instantRPM))
            StatRow(
                stringResource(R.string.samp_mean_rpm),
                state.meanRPM?.let { "%.4f RPM".format(it) } ?: "—",
            )
            StatRow(stringResource(R.string.samp_timestamp_base), state.timestampBase ?: "—")
            StatRow(
                stringResource(R.string.samp_gravity_source),
                if (state.gravityIsFused) stringResource(R.string.samp_gravity_fused)
                else stringResource(R.string.samp_gravity_accel),
            )
            state.gyroName?.let {
                Text(
                    stringResource(R.string.samp_gyro_name, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.gravityName?.let {
                Text(
                    stringResource(R.string.samp_gravity_name, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

