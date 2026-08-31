import XCTest
@testable import TurntableCore

final class StopwatchCalibrationTests: XCTestCase {

    /// 真機實測：TD 235 EV，碼錶與 App 同步量測 100 圈 187.91 秒，App 量到 31.9695 RPM。
    /// 同一台裝置的另一次（101 圈 / 189.6 s）給出 0.99953，兩次差 0.076%。
    func testReproducesTheMeasuredScaleFactor() {
        let c = StopwatchCalibration(revolutions: 100,
                                     seconds: 187.91,
                                     measuredRPM: 31.9695,
                                     deviceModel: "iPhone16,2")
        XCTAssertNotNil(c)
        XCTAssertEqual(c!.trueRPM, 31.9302, accuracy: 1e-4)
        XCTAssertEqual(c!.factor, 0.99877, accuracy: 1e-4)
        XCTAssertTrue(c!.isPlausible)
    }

    /// 規格 §3.7 的精度表：100 圈 ±0.3 s → 0.17%，200 圈 → 0.08%。
    func testPrecisionMatchesSpecTable() {
        let hundred = StopwatchCalibration(revolutions: 100, seconds: 180.0,
                                           measuredRPM: 33.0, deviceModel: "x")!
        XCTAssertEqual(hundred.precision() * 100, 0.167, accuracy: 0.005)

        let twoHundred = StopwatchCalibration(revolutions: 200, seconds: 360.0,
                                              measuredRPM: 33.0, deviceModel: "x")!
        XCTAssertEqual(twoHundred.precision() * 100, 0.083, accuracy: 0.005)
    }

    func testAppliesToReadings() {
        let c = StopwatchCalibration(revolutions: 100, seconds: 185.14,
                                     measuredRPM: 31.8119, deviceModel: "x")!
        XCTAssertEqual(c.apply(to: 31.8119), 32.4079, accuracy: 1e-4)
    }

    /// 打錯圈數會產生離譜的 k，必須擋下來 —— 存進去會讓之後每一次讀數都錯。
    func testImplausibleFactorIsFlagged() {
        // 100 圈打成 10 圈
        let typo = StopwatchCalibration(revolutions: 10, seconds: 185.14,
                                        measuredRPM: 31.8119, deviceModel: "x")!
        XCTAssertFalse(typo.isPlausible)
        XCTAssertEqual(typo.factor, 0.10187, accuracy: 1e-4)

        // 合理範圍的邊界仍要放行
        let edge = StopwatchCalibration(revolutions: 100, seconds: 180.0,
                                        measuredRPM: 32.0, deviceModel: "x")!
        XCTAssertTrue(edge.isPlausible)
    }

    func testRejectsInvalidInput() {
        XCTAssertNil(StopwatchCalibration(revolutions: 0, seconds: 180, measuredRPM: 33, deviceModel: "x"))
        XCTAssertNil(StopwatchCalibration(revolutions: 100, seconds: 0, measuredRPM: 33, deviceModel: "x"))
        XCTAssertNil(StopwatchCalibration(revolutions: 100, seconds: 180, measuredRPM: 0, deviceModel: "x"))
    }

    /// 存進 UserDefaults 要能原樣還原。
    func testRoundTripsThroughCodable() throws {
        let c = StopwatchCalibration(revolutions: 100, seconds: 185.14,
                                     measuredRPM: 31.8119, deviceModel: "iPhone16,2")!
        let data = try JSONEncoder().encode(c)
        let back = try JSONDecoder().decode(StopwatchCalibration.self, from: data)
        XCTAssertEqual(back, c)
    }
}
