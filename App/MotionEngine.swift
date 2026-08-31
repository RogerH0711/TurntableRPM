import Foundation
import CoreMotion
import UIKit
import TurntableCore

/// 規格 §2.1 / §4.3：CoreMotion 的封裝。
///
/// 感測器回呼跑在專屬 OperationQueue，樣本進 `SampleAccumulator`；
/// UI 只以 10 Hz 拉取快照，不會每來一筆樣本就觸發 SwiftUI 重繪（那會是每秒 100 次）。
final class MotionEngine: ObservableObject {

    struct Snapshot {
        /// 未修正的原始讀數。診斷用。
        var rawInstantRPM: Double = 0
        var rawMeanRPM: Double?
        /// 套用碼錶校準之後的讀數。沒有校準時等於原始值。
        var instantRPM: Double = 0
        var latestOmega: Double = 0
        var meanRPM: Double?
        var nominal: TurntableSpeed?
        var errorPercent: Double?
        var sampleCount: Int = 0
        var elapsedSeconds: Double = 0
        var effectiveSampleRate: Double = 0
        var phaseDegrees: Double = 0
        var revolutions: Int = 0
        var gyroTotalDegrees: Double = 0
        var magneticTotalDegrees: Double = 0
        /// k = 磁北總轉角 ÷ 陀螺儀總轉角。跑滿數十圈才可信。
        var calibrationEstimate: Double?
        /// 目前生效的碼錶校準倍率。nil 代表還沒校準，讀數不能拿去調唱盤。
        var appliedFactor: Double?
        /// 套用上面那個 k 之後的平均轉速。
        var correctedMeanRPM: Double?
        var correctedErrorPercent: Double?
        var latestYawDegrees: Double?
        var latestGravity: Vector3?
        var latestRotationRate: Vector3?
        var latestField: Vector3?

        /// 融合路徑的 k 到底能不能採信。見 `CalibrationConfidence`。
        var confidence: CalibrationConfidence = .insufficient

        // --- 繞過融合器的獨立路徑（`MagneticRevolutionCounter`）---
        var rawMagneticTotalDegrees: Double = 0
        var rawMagneticRevolutions: Int = 0
        var rawMagneticSampleCount: Int = 0
        var rawMagneticHorizontal: Double = 0
        var rawMagneticMinHorizontal: Double = 0
        var rawMagneticMaxHorizontal: Double = 0
        var rawMagneticRange: (larger: Double, smaller: Double)?

        // --- 扣掉圓心偏移之後重算（硬鐵校準）。這是這條路徑真正的輸出。---
        var refined: MagneticRevolutionCounter.Refined?
        var refinedCalibration: Double?
        var refinedCorrectedMeanRPM: Double?
        var refinedCorrectedErrorPercent: Double?
        /// k = 地磁總轉角 ÷ 陀螺儀總轉角。這個才是真正獨立的估計。
        var rawCalibrationEstimate: Double?
        var rawCorrectedMeanRPM: Double?
        var rawCorrectedErrorPercent: Double?
        /// 磁力計校準狀態。`uncalibrated` 時上面整條路徑都不可信。
        var fieldAccuracy: String = "—"
    }

    /// 量測模式。
    enum Mode: String, CaseIterable, Identifiable {
        /// 使用者自己按開始與停止。
        case manual
        /// 等轉盤到達穩定轉速才真正開始記錄，盤面停下時自動結束。
        case automatic
        var id: String { rawValue }
        var label: String { self == .manual ? "手動" : "自動" }
    }

    /// 量測的階段。自動模式會多一個「等待轉速穩定」的階段。
    enum Phase: Equatable {
        case idle
        /// 感測器已開，但還在等轉速穩定 —— 這段資料不會被記錄。
        case waitingForStability
        case measuring
        case stopped
    }

    enum Availability {
        /// 實機且動作感測器可用。`magneticNorth` 為 false 代表拿不到磁北參考，校準功能會受限。
        case ready(magneticNorth: Bool)
        /// 模擬器，或裝置沒有陀螺儀。
        case unavailable
    }

    @Published private(set) var snapshot = Snapshot()
    @Published private(set) var isRunning = false
    @Published private(set) var phase: Phase = .idle
    /// 量測模式。自動模式會自己等轉速穩定、自己在盤面停下時結束。
    @Published var mode: Mode = .manual
    @Published private(set) var availability: Availability = .unavailable
    @Published private(set) var statusMessage = ""
    /// 停止之後產生的匯出檔。給 ShareLink 用。
    @Published private(set) var exportURL: URL?
    /// 停止之後的離線分析（頻譜、抖晃率、極座標）。在背景執行緒算。
    @Published private(set) var analysis: MeasurementAnalysis?
    /// 每完成一次分析就換一個新值。畫面靠它知道「有新結果該存進歷史了」——
    /// `MeasurementAnalysis` 不是 Equatable，`onChange` 沒辦法直接觀察它。
    @Published private(set) var completedMeasurementID: UUID?

    /// 規格 §2.1：iOS 對第三方 App 的取樣率上限就是 100 Hz。
    let targetSampleRate: Double = 100.0

    /// 碼錶校準倍率。設定之後所有對外的轉速讀數都會自動套用。
    ///
    /// 指南針自動校準兩條路都失敗了（見 `CalibrationConfidence` 與
    /// `MagneticRevolutionCounter`），碼錶是目前唯一可信的來源。
    var calibrationFactor: Double? {
        didSet { if oldValue != calibrationFactor { pullSnapshot() } }
    }

    private let manager = CMMotionManager()
    private let accumulator = SampleAccumulator()
    private let sensorQueue: OperationQueue = {
        let queue = OperationQueue()
        queue.name = "TurntableRPM.motion"
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        return queue
    }()
    private var refreshTimer: Timer?
    /// 自動模式：轉速從什麼時候開始持續穩定。
    private var stableSince: TimeInterval?
    /// 自動模式的判準。
    private let autoStableSeconds = 3.0
    private let autoWindowSeconds = 2.0
    /// 圓心擬合是全量掃描，每 2 秒跑一次就夠了。
    private var refineTick = 0
    private var cachedRefined: MagneticRevolutionCounter.Refined?

    init() {
        refreshAvailability()
    }

    deinit {
        manager.stopDeviceMotionUpdates()
        manager.stopMagnetometerUpdates()
        refreshTimer?.invalidate()
    }

    func refreshAvailability() {
        guard manager.isDeviceMotionAvailable else {
            availability = .unavailable
            statusMessage = "這台裝置讀不到動作感測器。模擬器沒有陀螺儀，必須用實機測試。"
            return
        }
        let hasMagneticNorth = CMMotionManager.availableAttitudeReferenceFrames()
            .contains(.xMagneticNorthZVertical)
        availability = .ready(magneticNorth: hasMagneticNorth)
        statusMessage = hasMagneticNorth
            ? "把手機放上唱盤，靠近中心即可，然後按開始。"
            : "這台裝置沒有磁北參考，之後的自動校準會需要改用碼錶手動校準。"
    }

    func start() {
        guard case .ready(let hasMagneticNorth) = availability else {
            refreshAvailability()
            return
        }
        guard !isRunning else { return }

        accumulator.reset()
        exportURL = nil
        analysis = nil
        phase = (mode == .automatic) ? .waitingForStability : .measuring
        stableSince = nil
        refineTick = 0
        cachedRefined = nil
        manager.deviceMotionUpdateInterval = 1.0 / targetSampleRate

        // 選磁北而非真北：我們只需要相對角度的累積量，磁偏角沒有意義，
        // 而真北需要 CoreLocation 授權才能算磁偏角。
        let frame: CMAttitudeReferenceFrame = hasMagneticNorth
            ? .xMagneticNorthZVertical
            : .xArbitraryZVertical

        // 不捕捉 self，避免感測器佇列與主執行緒之間的隔離問題。
        let sink = accumulator
        manager.startDeviceMotionUpdates(using: frame, to: sensorQueue) { motion, _ in
            guard let motion else { return }
            let rotationRate = Vector3(motion.rotationRate.x, motion.rotationRate.y, motion.rotationRate.z)
            let gravity = Vector3(motion.gravity.x, motion.gravity.y, motion.gravity.z)
            let omega = SpinProjector.project(rotationRate: rotationRate, gravity: gravity)
            // 時間戳一定要用 CMLogItem.timestamp，不要假設每筆間隔剛好 1/100 秒。
            let sample = SpinSample(t: motion.timestamp,
                                    omega: omega,
                                    yaw: hasMagneticNorth ? motion.attitude.yaw : nil)

            // 原始磁場向量。刻意不走 attitude.yaw —— 那條路被融合器降權吃掉了。
            let raw = motion.magneticField
            let field = Vector3(raw.field.x, raw.field.y, raw.field.z)
            sink.append(sample,
                        gravity: gravity,
                        rotationRate: rotationRate,
                        field: field.magnitude > 0 ? field : nil,
                        fieldAccuracy: raw.accuracy)
        }

        // 未校準磁力計。CoreMotion 的偏置估計器會在量測過程中改動 magneticField，
        // 所以另外收一份完全沒動過的讀數，兩邊並排才判斷得出來是誰的問題。
        if manager.isMagnetometerAvailable {
            manager.magnetometerUpdateInterval = 1.0 / targetSampleRate
            manager.startMagnetometerUpdates(to: sensorQueue) { data, _ in
                guard let data else { return }
                sink.appendRawField(Vector3(data.magneticField.x,
                                            data.magneticField.y,
                                            data.magneticField.z))
            }
        }

        refreshTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.pullSnapshot()
        }
        UIApplication.shared.isIdleTimerDisabled = true
        isRunning = true
        statusMessage = mode == .automatic ? "等待轉速穩定…" : "量測中"
    }

    func stop() {
        guard isRunning else { return }
        manager.stopDeviceMotionUpdates()
        manager.stopMagnetometerUpdates()
        refreshTimer?.invalidate()
        refreshTimer = nil
        UIApplication.shared.isIdleTimerDisabled = false
        isRunning = false
        phase = .stopped
        cachedRefined = accumulator.refined()
        pullSnapshot()
        statusMessage = "已停止"
        // 分析先跑，寫檔在它完成之後 —— 匯出的摘要要帶上分析結果，
        // 反過來的話寫檔時 analysis 還是 nil，那些欄位永遠不會出現。
        runAnalysis()
    }

    /// 交出目前累積的樣本，供之後的分析路徑（M4）使用。
    func collectedSamples() -> [SpinSample] {
        accumulator.snapshotSamples()
    }

    /// 離線分析。FFT 加加權捲積在六萬筆樣本上要跑一下，不能擋主執行緒。
    private func runAnalysis() {
        let samples = accumulator.snapshotSamples()
        // 樣本太少分析不了，但匯出還是要做 —— 短量測的原始資料一樣有診斷價值。
        guard samples.count > 64 else { writeExport(); return }
        let rate = targetSampleRate
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let result = MeasurementAnalysis.analyze(samples: samples, sampleRate: rate)
            DispatchQueue.main.async {
                self?.analysis = result
                // 重拉快照，主畫面的平均轉速才會換成切過的值。
                self?.pullSnapshot()
                if result != nil { self?.completedMeasurementID = UUID() }
                self?.writeExport()
            }
        }
    }

    /// 把整包原始資料寫成 JSON。放背景執行緒 —— 10 分鐘的量測是六萬筆樣本，
    /// 在主執行緒編碼會讓畫面頓一下。
    private func writeExport() {
        let frames = accumulator.snapshotFrames()
        guard !frames.isEmpty else { return }
        let summary = summaryDictionary()
        let directory = FileManager.default.urls(for: .documentDirectory,
                                                 in: .userDomainMask)[0]
        DispatchQueue.global(qos: .utility).async { [weak self] in
            let url = try? MeasurementExport.write(frames: frames,
                                                   summary: summary,
                                                   to: directory)
            DispatchQueue.main.async { self?.exportURL = url }
        }
    }

    private func summaryDictionary() -> [String: Any] {
        let s = snapshot
        var d: [String: Any] = [
            "meanRPM": s.meanRPM ?? 0,
            "rawMeanRPM": s.rawMeanRPM ?? 0,
            "appliedFactor": s.appliedFactor ?? 0,
            "instantRPM": s.instantRPM,

            "sampleCount": s.sampleCount,
            "elapsedSeconds": s.elapsedSeconds,
            "effectiveSampleRate": s.effectiveSampleRate,
            "revolutions": s.revolutions,
            "phaseDegrees": s.phaseDegrees,
            "gyroTotalDegrees": s.gyroTotalDegrees,
            "magneticTotalDegrees": s.magneticTotalDegrees,
            "fieldAccuracy": s.fieldAccuracy,
            "rawMagneticTotalDegrees": s.rawMagneticTotalDegrees,
            "rawMagneticRevolutions": s.rawMagneticRevolutions,
            "rawMagneticMinHorizontal": s.rawMagneticMinHorizontal,
            "rawMagneticMaxHorizontal": s.rawMagneticMaxHorizontal,
        ]
        d["fusedCalibration"] = s.calibrationEstimate ?? 0
        // 標稱轉速辨識失敗時不要寫 0 —— 那會被讀成「偏差正好是 0%」。
        // 沒有值就不寫這個鍵。（NaN 不是合法 JSON，JSONSerialization 會拋錯。）
        if let e = s.errorPercent { d["errorPercent"] = e }
        if let n = s.nominal { d["nominalRPM"] = n.rpm }
        if let r = s.refined {
            d["refinedTotalDegrees"] = r.totalDegrees
            d["refinedRevolutions"] = r.revolutions
            d["refinedRadius"] = r.radius
            d["refinedCenterOffset"] = r.centerOffset
            d["refinedResidual"] = r.residual
            d["refinedTrustworthy"] = r.isTrustworthy
        }
        // 碼錶同步量測的真值（三次，範圍 0.08%），寫進檔案好讓分析時直接對照。
        d["stopwatchReferenceK"] = 0.99915

        // 分析結果也要寫進去。沒有這一段就只能靠截圖跟外部工具比對，
        // 而 tools/analyze_export.py 用頻譜平方和估 WRMS 會被補零高估約 1.3 倍 ——
        // App 走的是「加權後回到時域算 RMS」，兩邊要能並排才看得出誰對。
        if let a = analysis {
            d["analysisWrmsPercent"] = a.wowFlutter.wrmsPercent
            d["analysisPeak2SigmaPercent"] = a.wowFlutter.peak2SigmaPercent
            d["analysisPeakToRMSRatio"] = a.wowFlutter.peakToRMSRatio
            d["analysisOnePerRevPercent"] = a.onePerRevolutionPercent
            d["analysisRotationHz"] = a.rotationHz
            d["analysisPeakAngleDegrees"] = a.peakAngleDegrees ?? -1
            d["analysisPeaks"] = a.peaks.prefix(8).map {
                ["hz": $0.frequencyHz,
                 "percent": $0.amplitudePercent,
                 "order": $0.orderOfRotation,
                 "harmonic": $0.isRotationHarmonic]
            }
        }
        return d
    }

    /// 給反旋轉顯示用。每個畫面更新都會呼叫，所以刻意不是 @Published ——
    /// 每秒 120 次的 objectWillChange 會把 SwiftUI 淹掉。
    func displayAngleDegrees() -> Double {
        accumulator.displayAngleDegrees(now: ProcessInfo.processInfo.systemUptime)
    }

    /// 自動模式的狀態機。在 10 Hz 的快照迴圈裡跑。
    private func advanceAutomaticPhase() {
        guard mode == .automatic, isRunning else { return }
        let recent = accumulator.recentSamples(seconds: autoWindowSeconds)
        guard recent.count > 32, let mean = SpeedStatistics.meanOmega(recent) else { return }

        switch phase {
        case .waitingForStability:
            // 要同時「夠快」與「夠穩」。只看穩定度的話，靜止不動也是穩定的。
            let fastEnough = mean / 6.0 > 10.0
            let steady = SpeedStatistics.isStable(recent, relativeStdDevLimit: 0.01)
            guard fastEnough && steady else { stableSince = nil; return }

            let now = ProcessInfo.processInfo.systemUptime
            if let since = stableSince {
                if now - since >= autoStableSeconds {
                    // 丟掉等待期間的資料，從乾淨的狀態開始記錄。
                    accumulator.reset()
                    phase = .measuring
                    statusMessage = "量測中"
                }
            } else {
                stableSince = now
                statusMessage = "轉速穩定中…"
            }

        case .measuring:
            // 盤面停下就自動結束。門檻取標稱的 20%（規格 §6.2）。
            if mean / 6.0 < 8.0 { stop() }

        case .idle, .stopped:
            break
        }
    }

    private func pullSnapshot() {
        advanceAutomaticPhase()
        refineTick += 1
        if isRunning && refineTick % 20 == 0 { cachedRefined = accumulator.refined() }
        let reading = accumulator.read()
        var next = Snapshot()
        let k = calibrationFactor
        next.appliedFactor = k
        next.rawInstantRPM = reading.smoothedOmega / 6.0
        next.instantRPM = next.rawInstantRPM * (k ?? 1.0)
        next.latestOmega = reading.latestOmega
        next.sampleCount = reading.sampleCount
        next.elapsedSeconds = reading.elapsedSeconds
        next.effectiveSampleRate = reading.effectiveSampleRate
        next.phaseDegrees = reading.phaseDegrees
        next.revolutions = reading.revolutions
        next.gyroTotalDegrees = reading.gyroTotalDegrees
        next.magneticTotalDegrees = reading.magneticTotalDegrees
        next.calibrationEstimate = reading.calibrationEstimate
        next.latestYawDegrees = reading.latestYawDegrees
        next.latestGravity = reading.latestGravity
        next.latestRotationRate = reading.latestRotationRate
        next.latestField = reading.latestField
        next.rawMagneticTotalDegrees = reading.rawMagneticTotalDegrees
        next.rawMagneticRevolutions = reading.rawMagneticRevolutions
        next.rawMagneticSampleCount = reading.rawMagneticSampleCount
        next.rawMagneticHorizontal = reading.rawMagneticHorizontal
        next.rawMagneticMinHorizontal = reading.rawMagneticMinHorizontal
        next.rawMagneticMaxHorizontal = reading.rawMagneticMaxHorizontal
        next.rawMagneticRange = reading.rawMagneticRange
        next.refined = cachedRefined
        if let refined = cachedRefined, refined.isTrustworthy, reading.gyroTotalDegrees > 0 {
            next.refinedCalibration = refined.totalDegrees / reading.gyroTotalDegrees
        }
        next.rawCalibrationEstimate = reading.rawCalibrationEstimate
        next.fieldAccuracy = reading.fieldAccuracyLabel
        next.confidence = ScaleCalibrator.confidence(
            gyroTotalDegrees: reading.gyroTotalDegrees,
            magneticTotalDegrees: reading.magneticTotalDegrees,
            revolutions: reading.revolutions)

        // 停止之後，平均轉速改用分析算出來的 —— 那是切掉加速段之後的值。
        //
        // 即時累積器算的是「全部樣本」，含加速段。實測「先按開始再放手機」的
        // 情境：即時平均 30.02 RPM，真值 31.95，而且因為離 33⅓ 超過 8% 的辨識
        // 容差，連標稱轉速都判不出來，偏差顯示成 0。分析頁是對的，主畫面卻不是
        // —— 而主畫面才是使用者第一眼看到的數字。
        let gatedRawRPM = (!isRunning ? analysis?.meanRPM : nil)
        if let meanOmega = reading.meanOmega, meanOmega > 0 {
            let rawRPM = gatedRawRPM ?? (meanOmega / 6.0)
            next.rawMeanRPM = rawRPM
            // 對外的一切都用校準後的值，包含標稱辨識 —— 未修正的讀數不該拿來下判斷。
            let rpm = rawRPM * (k ?? 1.0)
            next.meanRPM = rpm
            if let nominal = SpeedStatistics.classify(rpm: rpm) {
                next.nominal = nominal
                next.errorPercent = SpeedStatistics.errorPercent(rpm: rpm, nominal: nominal)
                if let kEstimate = reading.calibrationEstimate {
                    let corrected = rawRPM * kEstimate
                    next.correctedMeanRPM = corrected
                    next.correctedErrorPercent = SpeedStatistics.errorPercent(rpm: corrected,
                                                                              nominal: nominal)
                }
                if let kRefined = next.refinedCalibration {
                    let corrected = rawRPM * kRefined
                    next.refinedCorrectedMeanRPM = corrected
                    next.refinedCorrectedErrorPercent =
                        SpeedStatistics.errorPercent(rpm: corrected, nominal: nominal)
                }
                if let kRaw = reading.rawCalibrationEstimate {
                    let corrected = rawRPM * kRaw
                    next.rawCorrectedMeanRPM = corrected
                    next.rawCorrectedErrorPercent = SpeedStatistics.errorPercent(rpm: corrected,
                                                                                 nominal: nominal)
                }
            }
        }
        snapshot = next
    }
}
