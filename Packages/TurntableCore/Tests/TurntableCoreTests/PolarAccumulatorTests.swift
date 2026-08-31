import Foundation
import XCTest
import TurntableCore

/// 規格 §3.6。
final class PolarAccumulatorTests: XCTestCase {

    private func run(phaseDegrees: Double) -> SyntheticRun {
        let revolutionsPerSecond = (100.0 / 3.0) / 60.0
        return SyntheticSignal.make(nominalRPM: 100.0 / 3.0,
                                    durationSeconds: 60,
                                    wow: [WowComponent(amplitudePercent: 0.4,
                                                       frequencyHz: revolutionsPerSecond,
                                                       phaseRadians: phaseDegrees * Double.pi / 180.0)])
    }

    func testPeakLandsAtTheCorrectAngle() {
        // d(θ) = A·sin(θ + φ) 的峰值在 θ = 90° − φ
        for phase in [0.0, 90.0, 217.0, 300.0] {
            let signal = run(phaseDegrees: phase)
            let deviation = DeviationSeries.make(from: signal.trueOmega)!.deviationPercent
            var accumulator = PolarAccumulator(binCount: 72)
            for i in 0 ..< deviation.count {
                accumulator.add(angleDegrees: signal.trueAngleDegrees[i], deviationPercent: deviation[i])
            }
            let expected = (90.0 - phase).truncatingRemainder(dividingBy: 360.0) + 360.0
            let peak = accumulator.peakAngleDegrees!
            var error = (peak - expected).truncatingRemainder(dividingBy: 360.0)
            if error > 180 { error -= 360 }
            if error < -180 { error += 360 }
            XCTAssertLessThan(abs(error), 6.0, "相位 \(phase)°：峰值在 \(peak)°，預期 \(expected.truncatingRemainder(dividingBy: 360.0))°")
        }
    }

    func testSamplesPerBinIsSpeedIndependent() {
        // 每格樣本數 = fs × T ÷ 格數，與轉速無關：60 s、100 Hz、72 格 -> 恆為 83
        for rpm in [50.0 / 3.0, 100.0 / 3.0, 45.0, 78.0] {
            let signal = SyntheticSignal.make(nominalRPM: rpm, durationSeconds: 60)
            var accumulator = PolarAccumulator(binCount: 72)
            for i in 0 ..< signal.trueOmega.count {
                accumulator.add(angleDegrees: signal.trueAngleDegrees[i], deviationPercent: 0)
            }
            let total = accumulator.bins.reduce(0) { $0 + $1.count }
            XCTAssertEqual(total, 6000)
            XCTAssertEqual(Double(total) / 72.0, 83.0, accuracy: 1.0, "rpm=\(rpm)")
        }
    }

    func testBinWrapping() {
        var accumulator = PolarAccumulator(binCount: 4)
        accumulator.add(angleDegrees: -10, deviationPercent: 1)     // -> 350°，第 3 格
        accumulator.add(angleDegrees: 725, deviationPercent: 2)     // -> 5°，第 0 格
        accumulator.add(angleDegrees: 360, deviationPercent: 3)     // -> 0°，第 0 格
        let bins = accumulator.bins
        XCTAssertEqual(bins[3].count, 1)
        XCTAssertEqual(bins[0].count, 2)
        XCTAssertEqual(bins[0].meanDeviation, 2.5, accuracy: 1e-12)
        XCTAssertEqual(bins[1].count, 0)
    }
}
