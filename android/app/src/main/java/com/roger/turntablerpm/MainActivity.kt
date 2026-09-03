package com.roger.turntablerpm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.roger.turntablerpm.R
import com.roger.turntablerpm.calibration.CalibrationStore
import com.roger.turntablerpm.core.SpeedStatistics
import com.roger.turntablerpm.history.HistoryStore
import com.roger.turntablerpm.history.MeasurementRecord
import com.roger.turntablerpm.sensor.Mode
import com.roger.turntablerpm.sensor.SensorEngine
import com.roger.turntablerpm.ui.AdvancedDiagnosticsScreen
import com.roger.turntablerpm.ui.AnalysisScreen
import com.roger.turntablerpm.ui.CalibrationScreen
import com.roger.turntablerpm.ui.AboutScreen
import com.roger.turntablerpm.ui.HistoryDetailScreen
import com.roger.turntablerpm.ui.HistoryScreen
import com.roger.turntablerpm.profile.ProfileStore
import com.roger.turntablerpm.ui.OnboardingScreen
import com.roger.turntablerpm.ui.LoadTestScreen
import com.roger.turntablerpm.ui.ProfileScreen
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

private enum class Screen {
    Measure, Analysis, Diagnostics, Advanced, Calibration, History, HistoryDetail,
    About, Profiles, LoadTest,
}

/**
 * 把匯出的 JSON 交給別的 app（郵件、雲端硬碟、傳訊）。
 *
 * **一定要走 FileProvider。** Android 7 之後把 `file://` 的 URI 傳出 app 會直接
 * 丟 FileUriExposedException；`content://` 加上 FLAG_GRANT_READ_URI_PERMISSION
 * 才是合法的路徑，而且權限只給這一次、只給這一個檔案。
 */
private fun shareExport(context: android.content.Context, path: String) {
    val file = java.io.File(path)
    if (!file.exists()) return
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent, context.getString(R.string.share_raw_data),
        ),
    )
}

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
    val profileStore = remember { ProfileStore(context) }
    val profiles by profileStore.profiles.collectAsStateWithLifecycle()
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
        engine.onAnalysisComplete = { analysis, raw, revolutions ->
            val nominal = SpeedStatistics.classify(analysis.meanRPM)
            history.add(
                MeasurementRecord.from(
                    analysis = analysis,
                    rawMeanRPM = raw,
                    revolutions = revolutions,
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
    var openRecordId by remember { mutableStateOf<Long?>(null) }
    var rotationOffset by remember { mutableStateOf(0.0) }
    var showDial by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.AUTOMATIC) }

    // 首次開啟顯示導覽。看完就記下來，之後可以從說明頁再看一次。
    val onboarding = remember { context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!onboarding.getBoolean("seenOnboarding", false)) }
    // 手機重量：換手機才會變，不該每次做載重測試都重打一次。
    var phoneMass by remember { mutableStateOf(onboarding.getFloat("phoneMassGrams", 200f).toDouble()) }

    DisposableEffect(Unit) { onDispose { engine.stop() } }

    // ── 系統返回鍵 ──────────────────────────────────────────────
    //
    // **Android 的返回鍵必須回上一頁，不是離開 app。** 這個 app 的畫面是用一個
    // `screen` 狀態切換的，不是 Activity 堆疊，所以系統不會自己知道該退到哪裡 ——
    // 沒有 BackHandler 的話，從說明頁按返回會直接把 app 關掉。
    // iOS 的 NavigationStack 免費得到這個行為，Compose 要自己接。
    BackHandler(enabled = showOnboarding && onboarding.getBoolean("seenOnboarding", false)) {
        // 第一次開啟時不攔 —— 那時導覽是最外層，返回就是離開 app。
        // 從說明入口重看時才需要退回原本的畫面。
        showOnboarding = false
    }
    BackHandler(enabled = showDial) {
        // **量測中不讓返回鍵停止量測。** 手機這時在轉盤上，返回鍵是誤觸的高風險區，
        // 而中斷一次 3 分鐘的量測沒有辦法復原。停止要走畫面上半部那個大按鈕。
        // 凍結之後（量測已結束）才讓它關掉盤面。
        if (!state.running) showDial = false
    }
    BackHandler(enabled = !showOnboarding && !showDial && screen != Screen.Measure) {
        screen = when (screen) {
            Screen.HistoryDetail -> Screen.History
            Screen.LoadTest -> Screen.Profiles
            else -> Screen.Measure
        }
    }

    // 量測中不讓螢幕睡著。3 分鐘的量測遠長於預設的螢幕逾時，睡著就等於量測中斷 ——
    // iOS 版用 isIdleTimerDisabled 做同一件事。
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    if (showOnboarding) {
        OnboardingScreen(
            onFinish = {
                // 第一次看完就記下來。從說明入口重看時這一行是無害的重複寫入。
                onboarding.edit().putBoolean("seenOnboarding", true).apply()
                showOnboarding = false
            },
            modifier = modifier,
        )
        return
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
            onOpenAbout = { screen = Screen.About },
            onOpenAdvanced = { screen = Screen.Advanced },
            onOpenAnalysis = { screen = Screen.Analysis },
            onReplayOnboarding = { showOnboarding = true },
            onShareExport = { shareExport(context, it) },
            onOpenProfiles = { screen = Screen.Profiles },
            profile = profiles.firstOrNull { it.isActive },
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
        Screen.Profiles -> ProfileScreen(
            profiles = profiles,
            onAdd = { profileStore.add() },
            onUpdate = { profileStore.update(it) },
            onDelete = { profileStore.delete(it) },
            onOpenLoadTest = { screen = Screen.LoadTest },
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
        Screen.LoadTest -> {
            val active = profiles.firstOrNull { it.isActive }
            LoadTestScreen(
                profile = active,
                records = records,
                phoneMassGrams = phoneMass,
                onPhoneMassChange = {
                    phoneMass = it
                    onboarding.edit().putFloat("phoneMassGrams", it.toFloat()).apply()
                },
                onSave = { r ->
                    active?.let {
                        profileStore.update(
                            it.copy(
                                loadSlopeRPMPerGram = r.slopeRPMPerGram,
                                loadPhoneEffectRPM = r.phoneEffectRPM,
                                loadIsSignificant = r.isSignificant,
                                loadMeasuredAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                    screen = Screen.Profiles
                },
                onBack = { screen = Screen.Profiles },
                modifier = modifier,
            )
        }
        Screen.About -> AboutScreen(
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
        Screen.History -> HistoryScreen(
            records = records,
            onDelete = { history.delete(it) },
            onOpen = { openRecordId = it; screen = Screen.HistoryDetail },
            onClear = { history.clear() },
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
        Screen.HistoryDetail -> {
            val record = records.firstOrNull { it.epochMillis == openRecordId }
            // 記錄被刪掉時退回列表，不要停在一個空白畫面上。
            if (record == null) {
                screen = Screen.History
            } else {
                HistoryDetailScreen(
                    record = record,
                    onNoteChange = { history.setNote(record.epochMillis, it) },
                    onBack = { screen = Screen.History },
                    modifier = modifier,
                )
            }
        }
        Screen.Analysis -> {
            val analysis = state.analysis
            // 記錄被清掉時退回主畫面，不要停在空白頁。
            if (analysis == null) {
                screen = Screen.Measure
            } else {
                AnalysisScreen(
                    analysis = analysis,
                    profile = profiles.firstOrNull { it.isActive },
                    onBack = { screen = Screen.Measure },
                    modifier = modifier,
                )
            }
        }
        Screen.Advanced -> AdvancedDiagnosticsScreen(
            state = state,
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
        Screen.Diagnostics -> SamplingDiagnostics(
            state = state,
            available = engine.isAvailable,
            // **手動模式 + samplingOnly。** 這一頁量的是取樣特性，不是一次量測：
            // 自動模式會在轉穩時清掉樣本、停下時自己結束，兩者都會毀掉統計。
            onStart = { engine.start(it, mode = Mode.MANUAL, samplingOnly = true) },
            onStop = { engine.stop() },
            onBack = { screen = Screen.Measure },
            modifier = modifier,
        )
    }
}
