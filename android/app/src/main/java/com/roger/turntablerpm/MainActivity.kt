package com.roger.turntablerpm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roger.turntablerpm.calibration.CalibrationStore
import com.roger.turntablerpm.core.SpeedStatistics
import com.roger.turntablerpm.history.HistoryStore
import com.roger.turntablerpm.history.MeasurementRecord
import com.roger.turntablerpm.sensor.Mode
import com.roger.turntablerpm.sensor.SensorEngine
import com.roger.turntablerpm.ui.CalibrationScreen
import com.roger.turntablerpm.ui.HistoryScreen
import com.roger.turntablerpm.ui.MeasurementScreen
import com.roger.turntablerpm.ui.SpinningDialScreen
import com.roger.turntablerpm.ui.SamplingDiagnostics
import com.roger.turntablerpm.ui.theme.TurntableRPMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TurntableRPMTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppRoot(Modifier.padding(padding))
                }
            }
        }
    }
}

private enum class Screen { Measure, Diagnostics, Calibration, History }

/**
 * 兩個畫面共用**同一個** SensorEngine —— 兩份引擎會各自註冊監聽器，
 * 事件流互相干擾，取樣統計就沒有意義了。
 */
@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { SensorEngine(context) }
    val store = remember { CalibrationStore(context) }
    val history = remember { HistoryStore(context) }
    val records by history.records.collectAsStateWithLifecycle()
    val state by engine.state.collectAsStateWithLifecycle()
    val calibration by store.calibration.collectAsStateWithLifecycle()
    val mismatched by store.mismatched.collectAsStateWithLifecycle()

    // 校準倍率一改就要立刻反映在讀數上。
    androidx.compose.runtime.LaunchedEffect(calibration) {
        engine.calibrationFactor = calibration?.factor
    }

    // 分析成功就自動存檔。不做手動按鈕 —— 使用者不會記得按。
    androidx.compose.runtime.DisposableEffect(engine) {
        engine.onAnalysisComplete = { analysis, raw ->
            val nominal = SpeedStatistics.classify(analysis.meanRPM)
            history.add(
                MeasurementRecord.from(
                    analysis = analysis,
                    rawMeanRPM = raw,
                    calibrationFactor = engine.calibrationFactor,
                    nominalLabel = nominal?.label,
                    errorPercent = nominal?.let {
                        SpeedStatistics.errorPercent(analysis.meanRPM, it)
                    },
                ),
            )
        }
        onDispose { engine.onAnalysisComplete = null }
    }
    var screen by remember { mutableStateOf(Screen.Measure) }
    var rotationOffset by remember { mutableStateOf(0.0) }
    var showDial by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.AUTOMATIC) }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    // 量測中不讓螢幕睡著。3 分鐘的量測遠長於預設的螢幕逾時，睡著就等於量測中斷 ——
    // iOS 版用 isIdleTimerDisabled 做同一件事。
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    // 量測中（含自動模式的等待）就整片切到反旋轉盤面 —— 手機這時在轉盤上，
    // 一般版面的文字是歪的、按鈕也按不到。
    if (showDial) {
        SpinningDialScreen(
            state = state,
            angleProvider = { engine.displayAngleDegrees() },
            rotationOffset = rotationOffset,
            onRotate = { rotationOffset += it },
            onStop = { engine.stop() },
            onDismiss = { showDial = false },
            modifier = modifier,
        )
        return
    }

    when (screen) {
        Screen.Measure -> MeasurementScreen(
            state = state,
            available = engine.isAvailable,
            unavailableReason = engine.unavailableReason,
            onStart = { rotationOffset = 0.0; showDial = true; engine.start(mode = it) },
            onStop = { engine.stop() },
            onOpenDiagnostics = { screen = Screen.Diagnostics },
            onOpenCalibration = { screen = Screen.Calibration },
            onOpenHistory = { screen = Screen.History },
            mode = mode,
            onModeChange = { mode = it },
            modifier = modifier,
        )
        Screen.Calibration -> CalibrationScreen(
            // 校準要拿**未修正**的讀數去比，否則會把已經套過的 k 再算一次。
            measuredRPM = state.rawMeanRPM,
            current = calibration,
            mismatched = mismatched,
            onSave = { store.save(it) },
            onClear = { store.clear() },
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
        Screen.History -> HistoryScreen(
            records = records,
            onDelete = { history.delete(it) },
            onClear = { history.clear() },
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
        Screen.Diagnostics -> SamplingDiagnostics(
            state = state,
            available = engine.isAvailable,
            onStart = { engine.start(it) },
            onStop = { engine.stop() },
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
    }
}
