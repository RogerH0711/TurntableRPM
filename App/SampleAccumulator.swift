import Foundation
import CoreMotion
import TurntableCore

/// 感測器樣本的執行緒安全累積器。
///
/// CoreMotion 的回呼跑在專屬 OperationQueue，UI 在主執行緒，兩邊透過這個類別交棒。
/// 平均值用增量梯形積分，不必為了算平均而複製整包樣本。
final class SampleAccumulator {

    struct Reading {
        var smoothedOmega: Double = 0
        var latestOmega: Double = 0
        var meanOmega: Double?
        var sampleCount: Int = 0
        var elapsedSeconds: Double = 0
        var effectiveSampleRate: Double = 0
        var phaseDegrees: Double = 0
        var revolutions: Int = 0
        var gyroTotalDegrees: Double = 0
        var magneticTotalDegrees: Double = 0
        var calibrationEstimate: Double?
        var latestYawDegrees: Double?
        var latestGravity: Vector3?
        var latestRotationRate: Vector3?
        var latestField: Vector3?

        // 繞過融合器的獨立路徑。跟上面 magneticTotalDegrees 並排比對用。
        var rawMagneticTotalDegrees: Double = 0
        var rawMagneticRevolutions: Int = 0
        var rawMagneticSampleCount: Int = 0
        var rawMagneticHorizontal: Double = 0
        var rawMagneticMinHorizontal: Double = 0
        var rawMagneticMaxHorizontal: Double = 0
        /// (較大者, 較小者)。誰是半徑誰是圓心偏移由「有沒有繞圈」決定。
        var rawMagneticRange: (larger: Double, smaller: Double)?
        var rawCalibrationEstimate: Double?
        var fieldAccuracyLabel: String = "—"
    }

    /// 顯示路徑的平滑視窗，規格 §3.3 的預設值（0.5 秒）。
    /// 只影響畫面上的數字，分析路徑一律用未平滑的原始序列。
    private let displayWindow = 50
    private let maxStoredSamples = 100 * 600   // 10 分鐘

    private let lock = NSLock()
    private var samples: [SpinSample] = []
    private var recentOmega: [Double] = []
    private var phase = PhaseIntegrator()
    private var previous: SpinSample?
    private var startTime: TimeInterval?
    private var trapezoidArea: Double = 0
    private var latestGravity: Vector3?
    private var latestRotationRate: Vector3?
    private var latestField: Vector3?
    /// 完全不經過陀螺儀的圈數計。見 `MagneticRevolutionCounter` 的說明。
    private var magneticCounter = MagneticRevolutionCounter()
    private var fieldAccuracy: CMMagneticFieldCalibrationAccuracy = .uncalibrated
    /// 逐樣本原始記錄，給匯出用。摘要數字不足以診斷磁場問題，必須留原始資料。
    private var frames: [RawFrame] = []
    /// 未校準磁力計最新讀數。它走自己的回呼，跟 deviceMotion 不同步，
    /// 所以取「最近一筆」貼到當下的 frame 上。
    private var latestRawField: Vector3?

    func reset() {
        lock.lock()
        defer { lock.unlock() }
        samples.removeAll(keepingCapacity: true)
        recentOmega.removeAll(keepingCapacity: true)
        phase = PhaseIntegrator()
        previous = nil
        startTime = nil
        trapezoidArea = 0
        latestGravity = nil
        latestRotationRate = nil
        latestField = nil
        magneticCounter = MagneticRevolutionCounter()
        fieldAccuracy = .uncalibrated
        frames.removeAll(keepingCapacity: true)
        latestRawField = nil
    }

    func append(_ sample: SpinSample, gravity: Vector3, rotationRate: Vector3, field: Vector3?,
                fieldAccuracy: CMMagneticFieldCalibrationAccuracy) {
        lock.lock()
        defer { lock.unlock() }

        if startTime == nil { startTime = sample.t }
        if let last = previous {
            let dt = sample.t - last.t
            if dt > 0 { trapezoidArea += dt * (sample.omega + last.omega) / 2.0 }
        }
        previous = sample

        if samples.count < maxStoredSamples { samples.append(sample) }
        recentOmega.append(sample.omega)
        if recentOmega.count > displayWindow {
            recentOmega.removeFirst(recentOmega.count - displayWindow)
        }
        phase.add(sample)
        if let field {
            magneticCounter.add(field: field, gravity: gravity)
            latestField = field
        }
        latestGravity = gravity
        latestRotationRate = rotationRate
        self.fieldAccuracy = fieldAccuracy

        if frames.count < maxStoredSamples {
            frames.append(RawFrame(t: sample.t,
                                   omega: sample.omega,
                                   yaw: sample.yaw,
                                   gravity: gravity,
                                   field: field,
                                   rawField: latestRawField))
        }
    }

    /// 未校準磁力計的回呼。跟 `append` 是不同的感測器串流。
    func appendRawField(_ field: Vector3) {
        lock.lock()
        defer { lock.unlock() }
        latestRawField = field
    }

    func snapshotFrames() -> [RawFrame] {
        lock.lock()
        defer { lock.unlock() }
        return frames
    }

    func read() -> Reading {
        lock.lock()
        defer { lock.unlock() }

        var reading = Reading()
        guard let last = previous, let start = startTime else { return reading }

        var sum = 0.0
        for v in recentOmega { sum += v }
        reading.smoothedOmega = recentOmega.isEmpty ? 0 : sum / Double(recentOmega.count)
        reading.latestOmega = last.omega
        reading.sampleCount = samples.count
        reading.elapsedSeconds = last.t - start
        if reading.elapsedSeconds > 0 {
            reading.meanOmega = trapezoidArea / reading.elapsedSeconds
            reading.effectiveSampleRate = Double(samples.count - 1) / reading.elapsedSeconds
        }
        reading.phaseDegrees = phase.phaseDegrees
        reading.revolutions = phase.revolutions
        reading.gyroTotalDegrees = phase.gyroTotalDegrees
        reading.magneticTotalDegrees = phase.magneticTotalDegrees
        reading.calibrationEstimate = phase.calibrationEstimate
        reading.latestYawDegrees = last.yaw.map { $0 * 180.0 / Double.pi }
        reading.latestGravity = latestGravity
        reading.latestRotationRate = latestRotationRate
        reading.latestField = latestField

        reading.rawMagneticTotalDegrees = magneticCounter.totalDegrees
        reading.rawMagneticRevolutions = magneticCounter.revolutions
        reading.rawMagneticSampleCount = magneticCounter.sampleCount
        reading.rawMagneticHorizontal = magneticCounter.horizontalMagnitude
        reading.rawMagneticMinHorizontal =
            magneticCounter.minHorizontal.isFinite ? magneticCounter.minHorizontal : 0
        reading.rawMagneticMaxHorizontal = magneticCounter.maxHorizontal
        reading.rawMagneticRange = magneticCounter.horizontalRange
        reading.rawCalibrationEstimate =
            magneticCounter.calibrationFactor(gyroTotalDegrees: phase.gyroTotalDegrees)
        reading.fieldAccuracyLabel = Self.label(for: fieldAccuracy)
        return reading
    }

    /// 扣掉圓心偏移之後重算。這是全量掃描，不要每次 `read()` 都跑 ——
    /// 呼叫端自己節流（目前是每 2 秒一次，外加停止時一次）。
    func refined() -> MagneticRevolutionCounter.Refined? {
        lock.lock()
        defer { lock.unlock() }
        return magneticCounter.refined()
    }

    /// `uncalibrated` 代表磁力計還沒校準好，這時整條原始磁場路徑都不可信，
    /// 要在畫面上講清楚，不能讓使用者拿去調唱盤。
    private static func label(for accuracy: CMMagneticFieldCalibrationAccuracy) -> String {
        switch accuracy {
        case .uncalibrated: return "未校準（不可信）"
        case .low: return "低"
        case .medium: return "中"
        case .high: return "高"
        @unknown default: return "未知"
        }
    }

    /// 交出目前累積的樣本，供之後的分析路徑（M4）使用。
    func snapshotSamples() -> [SpinSample] {
        lock.lock()
        defer { lock.unlock() }
        return samples
    }
}
