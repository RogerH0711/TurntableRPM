import Foundation
import XCTest
import TurntableCore

/// 規格 §2.2。黃金值由 Python 參考實作算出。
final class SpinProjectorTests: XCTestCase {

    func testTiltInvariance() {
        // 手機或盤面傾斜 0–30°，投影法的讀數都必須是 200 °/s（33⅓ RPM）。
        for tilt in [0.0, 5.0, 15.0, 30.0] {
            let run = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 5, tiltDegrees: tilt)
            var sum = 0.0
            for i in 0 ..< run.rotationRates.count {
                sum += SpinProjector.project(rotationRate: run.rotationRates[i], gravity: run.gravities[i])
            }
            let mean = sum / Double(run.rotationRates.count)
            XCTAssertEqual(mean, 200.0, accuracy: 1e-9, "傾斜 \(tilt)° 時投影結果應與傾角無關")
        }
    }

    func testNaiveZAxisCosineError() {
        // 對照組：只讀 z 軸的 cos 誤差。5° 就已經 0.38%，超過 0.1% 的目標精度。
        let expected: [Double: Double] = [5.0: -0.3805, 15.0: -3.4074, 30.0: -13.3975]
        for (tilt, expectedErrorPercent) in expected {
            let run = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 2, tiltDegrees: tilt)
            var sum = 0.0
            for rate in run.rotationRates { sum += SpinProjector.projectNaiveZ(rotationRate: rate) }
            let mean = sum / Double(run.rotationRates.count)
            let errorPercent = (mean / 200.0 - 1.0) * 100.0
            XCTAssertEqual(errorPercent, expectedErrorPercent, accuracy: 0.001)
        }
    }

    func testZeroGravityIsSafe() {
        XCTAssertEqual(SpinProjector.project(rotationRate: Vector3(1, 2, 3), gravity: Vector3(0, 0, 0)), 0)
    }
}
