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

    enum Availability {
        /// 實機且動作感測器可用。`magneticNorth` 為 false 代表拿不到磁北參考，校準功能會受限。
        case ready(magneticNorth: Bool)
        /// 模擬器，或裝置沒有陀螺儀。
        case unavailable
    }

    @Published private(set) var snapshot = Snapshot()
    @Published private(set) var isRunning = false
    @Published private(set) var availability: Availability = .unavailable
    @Published private(set) var statusMessage = ""
    /// 停止之後產生的匯出檔。給 ShareLink 用。
    @Published private(set) var exportURL: URL?

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
        statusMessage = "量測中"
    }

    func stop() {
        guard isRunning else { return }
        manager.stopDeviceMotionUpdates()
        manager.stopMagnetometerUpdates()
        refreshTimer?.invalidate()
        refreshTimer = nil
        UIApplication.shared.isIdleTimerDisabled = false
        isRunning = false
        cachedRefined = accumulator.refined()
        pullSnapshot()
        statusMessage = "已停止"
        writeExport()
    }

    /// 交出目前累積的樣本，供之後的分析路徑（M4）使用。
    func collectedSamples() -> [SpinSample] {
        accumulator.snapshotSamples()
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
            "errorPercent": s.errorPercent ?? 0,
            "nominalRPM": s.nominal?.rpm ?? 0,
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
        return d
    }

    private func pullSnapshot() {
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

        if let meanOmega = reading.meanOmega, meanOmega > 0 {
            let rawRPM = meanOmega / 6.0
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
