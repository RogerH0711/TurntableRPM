package com.roger.turntablerpm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roger.turntablerpm.sensor.EngineState
import com.roger.turntablerpm.sensor.SensorEngine
import com.roger.turntablerpm.ui.theme.TurntableRPMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TurntableRPMTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    SamplingDiagnostics(Modifier.padding(padding))
                }
            }
        }
    }
}

/**
 * 取樣特性診斷。
 *
 * **這是 Android 版的第一個畫面，不是暫時的。** 交接文件 §5 指出這一版真正的工程難點是
 * Android 的取樣率只是建議值、實際頻率由廠商實作決定 —— 所以第一件要做的事是把
 * 各裝置的實際取樣率與 jitter 量出來並記錄，那是 README 裡「真的碰過硬體」的證據。
 *
 * 手機**靜止不動也能量**：陀螺儀照樣以設定的速率送事件，取樣特性跟有沒有轉無關。
 */
@Composable
fun SamplingDiagnostics(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { SensorEngine(context) }
    val state by engine.state.collectAsStateWithLifecycle()
    var periodUs by remember { mutableIntStateOf(10_000) }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    // 量測中不讓螢幕睡著。3 分鐘的量測遠長於預設的螢幕逾時，睡著就等於量測中斷 ——
    // iOS 版用 isIdleTimerDisabled 做同一件事。
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("取樣特性診斷", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Android 的取樣率設定只是建議值，實際頻率由廠商實作決定。" +
                "手機靜止不動就能量 —— 取樣特性跟盤面有沒有轉無關。",
            style = MaterialTheme.typography.bodySmall,
        )

        if (!engine.isAvailable) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "這台裝置缺少陀螺儀或重力感測器，無法量測。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        // 要求的取樣率。刻意不提供 >200 Hz —— Android 12 起那需要
        // HIGH_SAMPLING_RATE_SENSORS 權限，而這個 app 不需要那麼快
        // （白頻譜假設下 50 Hz 以上只佔加權能量 0.72%）。
        Text("要求的取樣率", style = MaterialTheme.typography.titleSmall)
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
            onClick = { if (state.running) engine.stop() else engine.start(periodUs) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.running) "停止" else "開始量測")
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        StatsCard(state)
        SensorCard(state)
    }
}

@Composable
private fun StatsCard(state: EngineState) {
    val s = state.stats
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("取樣", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (s != null) "%.2f".format(s.effectiveRateHz) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.fillMaxWidth(0.02f))
                Text("Hz 實際", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.height(6.dp)) {}
            StatRow("樣本數", "${state.sampleCount}")
            StatRow("時長", if (s != null) "%.1f s".format(s.durationSeconds) else "—")
            StatRow("間隔中位數", if (s != null) "%.3f ms".format(s.medianIntervalMs) else "—")
            StatRow("間隔標準差", if (s != null) "%.3f ms".format(s.stdDevIntervalMs) else "—")
            StatRow("抖動比", if (s != null) "%.3f %%".format(s.jitterRatio * 100) else "—")
            StatRow("最小／最大", if (s != null) "%.2f / %.2f ms".format(s.minIntervalMs, s.maxIntervalMs) else "—")
            StatRow("長空隙", if (s != null) "${s.longGaps} 次" else "—")
            StatRow("最糟空隙", if (s != null) "%.2f×".format(s.worstGapRatio) else "—")

            Row(Modifier.height(6.dp)) {}
            // **最關鍵的一項。** 實際取樣率比要求值高 7.9% 有兩種可能：感測器真的比較快
            // （時間戳誠實，無害），或時間戳跑在快 7.9% 的時鐘上（頻域結果全錯）。
            // 平均轉速兩種情況都看不出差別，只有拿牆鐘對照才分得開。
            StatRow("牆鐘時長", "%.2f s".format(state.wallElapsedSeconds))
            StatRow("時間戳 ÷ 牆鐘", state.clockRatio?.let { "%.5f".format(it) } ?: "—")
            // **比值需要夠長的時間才有意義。** 第一筆事件的時間戳與牆鐘讀數之間有幾毫秒的
            // 派送延遲，那個固定偏移除以短時距會被放大成假的漂移 ——
            // 實測 0.69 秒的量測會顯示 1.00547 並誤報「時基有問題」。
            val ratioTrustworthy = state.wallElapsedSeconds >= 10.0
            state.clockRatio?.takeIf { ratioTrustworthy }?.let { r ->
                Text(
                    if (kotlin.math.abs(r - 1.0) < 0.002) {
                        "時間戳誠實（與牆鐘差 %.3f%%）—— 取樣率比要求值高只是感測器本來就跑比較快，".format((r - 1) * 100) +
                            "對這個 app 無害，因為積分一律用真實時間戳。"
                    } else {
                        "⚠ 時間戳與牆鐘差 %.2f%% —— 頻域結果會整體偏移同樣的量，".format((r - 1) * 100) +
                            "譜峰的倍數判讀不可信，必須先校正時基。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.clockRatio != null && !ratioTrustworthy) {
                Text(
                    "時間戳比值要量滿 10 秒才有意義（第一筆事件的派送延遲會被短時距放大）。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(Modifier.height(6.dp)) {}
            Text(
                "iOS 基準（iPhone 15 Pro Max）：中位數 9.990 ms、標準差 0.005 ms、" +
                    "抖動比 0.05%、最糟空隙 1.00×",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SensorCard(state: EngineState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("感測器與轉速", style = MaterialTheme.typography.titleMedium)
            StatRow("瞬時轉速", "%.3f RPM".format(state.instantRPM))
            StatRow("平均轉速", state.meanRPM?.let { "%.4f RPM".format(it) } ?: "—")
            StatRow("時間戳基準", state.timestampBase ?: "—")
            StatRow("重力來源", if (state.gravityIsFused) "TYPE_GRAVITY（融合）" else "加速度計（未融合）")
            state.gyroName?.let { Text("陀螺儀：$it", style = MaterialTheme.typography.bodySmall) }
            state.gravityName?.let { Text("重力：$it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
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
