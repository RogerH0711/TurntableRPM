import XCTest
@testable import TurntableCore

final class CalibrationConfidenceTests: XCTestCase {

    /// 真機實測的回歸測試：這組數字曾經讓 UI 說出「這個倍率可以參考了」，
    /// 而那個倍率（0.99994）跟碼錶真值（1.01837）差了 1.8%。
    func testRealMeasurementIsRejectedAsTautology() {
        let confidence = ScaleCalibrator.confidence(gyroTotalDegrees: 12987,
                                                    magneticTotalDegrees: 12986,
                                                    revolutions: 36)
        guard case .indistinguishable(let divergence, let floor) = confidence else {
            return XCTFail("兩條路徑只差 1°，必須判為無法區分，實際得到 \(confidence)")
        }
        XCTAssertEqual(divergence, 1, accuracy: 1e-9)
        XCTAssertEqual(floor, 15, accuracy: 1e-9)
        XCTAssertFalse(confidence.isUsable)
    }

    /// 舊判準的回歸測試：圈數再多，只要兩條路徑沒有分歧就不能採信。
    func testRevolutionCountAloneIsNotEnough() {
        for revolutions in [30, 100, 1000] {
            let confidence = ScaleCalibrator.confidence(gyroTotalDegrees: 360 * Double(revolutions),
                                                        magneticTotalDegrees: 360 * Double(revolutions),
                                                        revolutions: revolutions)
            XCTAssertFalse(confidence.isUsable, "\(revolutions) 圈但零分歧，不該可用")
        }
    }

    /// 分歧量超過雜訊底線時才算數。這裡用「陀螺儀真的低估 1.804%」的情境：
    /// 真實轉角 13225°，陀螺儀只積到 12987°，差 238° 遠高於 15° 的底線。
    func testGenuineDivergenceIsUsable() {
        let confidence = ScaleCalibrator.confidence(gyroTotalDegrees: 12987,
                                                    magneticTotalDegrees: 13225,
                                                    revolutions: 36)
        guard case .usable(let precision) = confidence else {
            return XCTFail("238° 的分歧遠高於底線，應該可用，實際得到 \(confidence)")
        }
        XCTAssertEqual(precision, 5.0 / 13225.0, accuracy: 1e-12)
        XCTAssertTrue(confidence.isUsable)
    }

    func testInsufficientBelowOneRevolution() {
        XCTAssertEqual(ScaleCalibrator.confidence(gyroTotalDegrees: 200,
                                                  magneticTotalDegrees: 240,
                                                  revolutions: 0),
                       .insufficient)
    }

    func testRejectsDegenerateInput() {
        XCTAssertEqual(ScaleCalibrator.confidence(gyroTotalDegrees: 0,
                                                  magneticTotalDegrees: 3600,
                                                  revolutions: 10),
                       .insufficient)
        XCTAssertEqual(ScaleCalibrator.confidence(gyroTotalDegrees: 3600,
                                                  magneticTotalDegrees: 0,
                                                  revolutions: 10),
                       .insufficient)
    }

    /// 雜訊底線是可調的：安靜環境雜訊小，同樣的分歧量就變得可信。
    func testNoiseFloorIsConfigurable() {
        let borderline = ScaleCalibrator.confidence(gyroTotalDegrees: 3600,
                                                    magneticTotalDegrees: 3620,
                                                    revolutions: 10,
                                                    yawNoiseDegrees: 5.0)
        XCTAssertTrue(borderline.isUsable, "20° 分歧 > 15° 底線")

        let noisy = ScaleCalibrator.confidence(gyroTotalDegrees: 3600,
                                               magneticTotalDegrees: 3620,
                                               revolutions: 10,
                                               yawNoiseDegrees: 10.0)
        XCTAssertFalse(noisy.isUsable, "雜訊 10° 時底線是 30°，20° 的分歧不夠")
    }
}
