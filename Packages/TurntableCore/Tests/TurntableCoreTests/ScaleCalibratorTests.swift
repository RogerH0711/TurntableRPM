import Foundation
import XCTest
import TurntableCore

/// 規格 §3.7：整個 app 的成敗都在這裡。
final class ScaleCalibratorTests: XCTestCase {

    func testRecoversScaleFactor() {
        for epsilon in [0.03, 0.01, -0.015, 0.001] {
            let signal = SyntheticSignal.make(nominalRPM: 100.0 / 3.0,
                                              durationSeconds: 120,
                                              scaleError: epsilon,
                                              yawNoiseDegrees: 2.0,
                                              seed: 3)
            let result = ScaleCalibrator.calibrate(signal.samples)
            XCTAssertNotNil(result)
            let expected = 1.0 / (1.0 + epsilon)
            XCTAssertEqual(result!.factor, expected, accuracy: expected * 0.003,
                           "ε=\(epsilon) 時應回推 k=\(expected)")
            XCTAssertGreaterThan(result!.revolutions, 60)
        }
    }

    func testCorrectedSpeedHitsTargetAccuracy() {
        // 未校準 3% 誤差 -> 校準後應落在 0.1% 以內
        let epsilon = 0.03
        let signal = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 120,
                                          scaleError: epsilon, yawNoiseDegrees: 2.0, seed: 11)
        let raw = SpeedStatistics.meanRPM(signal.samples)!
        XCTAssertEqual(raw, (100.0 / 3.0) * (1 + epsilon), accuracy: 0.01)

        let k = ScaleCalibrator.calibrate(signal.samples)!.factor
        let corrected = raw * k
        XCTAssertEqual(corrected, 100.0 / 3.0, accuracy: (100.0 / 3.0) * 0.001)
    }

    func testRequiredRevolutionsMatchesSpecTable() {
        // 目標精度 0.05%：乾淨環境 2° -> 11 圈；一般客廳 5° -> 28 圈；干擾大 10° -> 56 圈
        XCTAssertEqual(ScaleCalibrator.requiredRevolutions(yawNoiseDegrees: 2, targetPrecision: 0.0005), 12)
        XCTAssertEqual(ScaleCalibrator.requiredRevolutions(yawNoiseDegrees: 5, targetPrecision: 0.0005), 28)
        XCTAssertEqual(ScaleCalibrator.requiredRevolutions(yawNoiseDegrees: 10, targetPrecision: 0.0005), 56)
    }

    func testManualStopwatchFallback() {
        // 100 圈的 33⅓ 轉需要 180 秒
        let k = ScaleCalibrator.manualFactor(revolutions: 100, seconds: 180.0, measuredRPM: 33.5)
        XCTAssertNotNil(k)
        XCTAssertEqual(k!, (100.0 / 3.0) / 33.5, accuracy: 1e-9)

        // 人為計時誤差 ±0.3 s：100 圈 -> 0.17%，200 圈 -> 0.08%
        XCTAssertEqual(ScaleCalibrator.manualPrecision(revolutions: 100, rpm: 100.0 / 3.0, timingErrorSeconds: 0.3),
                       0.001667, accuracy: 1e-5)
        XCTAssertEqual(ScaleCalibrator.manualPrecision(revolutions: 200, rpm: 100.0 / 3.0, timingErrorSeconds: 0.3),
                       0.000833, accuracy: 1e-5)
    }

    func testReturnsNilWithoutMagnetometer() {
        let samples = (0 ..< 500).map { SpinSample(t: Double($0) / 100.0, omega: 200.0, yaw: nil) }
        XCTAssertNil(ScaleCalibrator.calibrate(samples))
    }

    func testReturnsNilBelowOneRevolution() {
        let signal = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 1.0)
        XCTAssertNil(ScaleCalibrator.calibrate(signal.samples))
    }
}
