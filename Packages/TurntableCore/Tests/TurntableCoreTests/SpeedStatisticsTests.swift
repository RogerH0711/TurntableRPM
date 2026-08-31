import Foundation
import XCTest
import TurntableCore

/// 規格 §3.2。
final class SpeedStatisticsTests: XCTestCase {

    func testMeanRPMRecoveryForAllNominalSpeeds() {
        for speed in TurntableSpeed.standard {
            let run = SyntheticSignal.make(nominalRPM: speed.rpm,
                                           durationSeconds: 30,
                                           wow: [WowComponent(amplitudePercent: 0.3, frequencyHz: 0.55)],
                                           noisePercent: 0.02,
                                           seed: 42)
            let rpm = SpeedStatistics.meanRPM(run.samples)
            XCTAssertNotNil(rpm)
            XCTAssertEqual(rpm!, speed.rpm, accuracy: speed.rpm * 0.001)
            XCTAssertEqual(SpeedStatistics.classify(rpm: rpm!), speed)
        }
    }

    func testDegreesPerSecondConversion() {
        XCTAssertEqual(TurntableSpeed.rpm16.degreesPerSecond, 100.0, accuracy: 1e-12)
        XCTAssertEqual(TurntableSpeed.rpm33.degreesPerSecond, 200.0, accuracy: 1e-12)
        XCTAssertEqual(TurntableSpeed.rpm45.degreesPerSecond, 270.0, accuracy: 1e-12)
        XCTAssertEqual(TurntableSpeed.rpm78.degreesPerSecond, 468.0, accuracy: 1e-12)
    }

    func testClassificationWindow() {
        // ±8% 的辨識窗。33⅓ 的窗是 30.667–36.000。
        XCTAssertEqual(SpeedStatistics.classify(rpm: 33.20), TurntableSpeed.rpm33)
        XCTAssertEqual(SpeedStatistics.classify(rpm: 30.70), TurntableSpeed.rpm33)
        XCTAssertEqual(SpeedStatistics.classify(rpm: 35.90), TurntableSpeed.rpm33)
        XCTAssertEqual(SpeedStatistics.classify(rpm: 41.50), TurntableSpeed.rpm45)
        XCTAssertEqual(SpeedStatistics.classify(rpm: 71.90), TurntableSpeed.rpm78)
        // 落在兩個標稱值之間的空隙 -> 不判定
        XCTAssertNil(SpeedStatistics.classify(rpm: 30.60))
        XCTAssertNil(SpeedStatistics.classify(rpm: 41.00))
        XCTAssertNil(SpeedStatistics.classify(rpm: 71.50))
        XCTAssertNil(SpeedStatistics.classify(rpm: 60.00))
    }

    func testErrorPercent() {
        XCTAssertEqual(SpeedStatistics.errorPercent(rpm: 33.0, nominal: .rpm33), -1.0, accuracy: 1e-9)
        XCTAssertEqual(SpeedStatistics.errorPercent(rpm: 45.045, nominal: .rpm45), 0.1, accuracy: 1e-9)
    }

    func testStabilityGateRejectsSpinUp() {
        let steady = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 3, noisePercent: 0.05, seed: 5)
        XCTAssertTrue(SpeedStatistics.isStable(steady.samples))

        // 線性加速段：相對標準差遠超過 2%
        var spinUp: [SpinSample] = []
        for i in 0 ..< 300 {
            let t = Double(i) / 100.0
            spinUp.append(SpinSample(t: t, omega: 200.0 * (t / 3.0)))
        }
        XCTAssertFalse(SpeedStatistics.isStable(spinUp))
    }

    func testMeanToleratesJitteredTimestamps() {
        // 取樣間隔抖動時，梯形積分仍然要回到 200 °/s
        var rng = SplitMix64(seed: 9)
        var samples: [SpinSample] = []
        var t = 0.0
        for _ in 0 ..< 3000 {
            samples.append(SpinSample(t: t, omega: 200.0))
            t += 0.01 * (0.7 + 0.6 * rng.nextUniform())
        }
        XCTAssertEqual(SpeedStatistics.meanOmega(samples)!, 200.0, accuracy: 1e-9)
    }
}
