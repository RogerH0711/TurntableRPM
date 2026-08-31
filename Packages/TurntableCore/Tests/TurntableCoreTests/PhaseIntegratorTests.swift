import Foundation
import XCTest
import TurntableCore

/// 規格 §3.6：熱圖的相位不能只靠陀螺儀積分。
///
/// 這組測試不只看最後一筆的誤差，而是追蹤整段的誤差軌跡；而且**每個案例都跑正反兩個轉向**。
/// 真實唱盤從上方看是順時針轉，裝置姿態的 yaw 會遞減 —— 早期版本的合成訊號只產生遞增的 yaw，
/// 讓一個轉向的 bug 溜過了整組測試，直到放上真的唱盤才炸掉。
final class PhaseIntegratorTests: XCTestCase {

    private struct Trace {
        let maxError: Double
        let finalError: Double
        let maxErrorFirstThird: Double
        let maxErrorLastThird: Double
        let revolutions: Int
        let calibrationEstimate: Double?
        let magneticTotalDegrees: Double
        let gyroTotalDegrees: Double
    }

    private func circularError(_ a: Double, _ b: Double) -> Double {
        var d = (a - b).truncatingRemainder(dividingBy: 360.0)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return abs(d)
    }

    private func trace(rpm: Double, duration: Double, scaleError: Double,
                       useMagnetometer: Bool, reversedYaw: Bool = false) -> Trace {
        let signal = SyntheticSignal.make(nominalRPM: rpm, durationSeconds: duration,
                                          scaleError: scaleError, reversedYaw: reversedYaw, seed: 4)
        var integrator = PhaseIntegrator()
        var errors: [Double] = []
        errors.reserveCapacity(signal.samples.count)

        for i in 0 ..< signal.samples.count {
            let sample = signal.samples[i]
            integrator.add(useMagnetometer
                           ? sample
                           : SpinSample(t: sample.t, omega: sample.omega, yaw: nil))
            var truePhase = signal.trueAngleDegrees[i].truncatingRemainder(dividingBy: 360.0)
            if truePhase < 0 { truePhase += 360 }
            errors.append(circularError(integrator.phaseDegrees, truePhase))
        }

        let third = errors.count / 3
        return Trace(maxError: errors.max() ?? 0,
                     finalError: errors[errors.count - 1],
                     maxErrorFirstThird: errors[0 ..< third].max() ?? 0,
                     maxErrorLastThird: errors[(errors.count - third)...].max() ?? 0,
                     revolutions: integrator.revolutions,
                     calibrationEstimate: integrator.calibrationEstimate,
                     magneticTotalDegrees: integrator.magneticTotalDegrees,
                     gyroTotalDegrees: integrator.gyroTotalDegrees)
    }

    /// 誤差的理論上界：一個取樣間隔（偵測必然落在跨圈後的第一個取樣）加上圈內的比例因子漂移。
    /// 乘 1.2 是浮點餘裕 —— 45 轉的超調量剛好逼近整整一個取樣，卡在等號上會讓測試變得脆弱。
    private func errorBound(rpm: Double, scaleError: Double, sampleRate: Double = 100.0) -> Double {
        (rpm * 6.0 / sampleRate + abs(scaleError) * 360.0) * 1.2
    }

    /// 回歸測試：真實唱盤的 yaw 是遞減的。
    ///
    /// 舊版無條件把錨點 += 2π，遇到遞減的 yaw 時錨點會朝反方向跑掉，
    /// 之後每一個取樣都滿足「走了超過一圈」——圈數暴增到接近取樣數，相位恆為 0。
    /// 真機上量到的就是這個：4921 個樣本、4730 圈、相位 0.0°。
    func testReversedRotationCountsRevolutionsCorrectly() {
        let result = trace(rpm: 100.0 / 3.0, duration: 60, scaleError: 0,
                           useMagnetometer: true, reversedYaw: true)
        XCTAssertEqual(result.revolutions, 33, "60 秒 33⅓ 轉應該是 33 圈，不是幾千圈")
        XCTAssertLessThan(result.maxError, errorBound(rpm: 100.0 / 3.0, scaleError: 0))
        XCTAssertLessThan(result.maxErrorLastThird, result.maxErrorFirstThird * 2.0 + 1.0)
    }

    /// 正反轉向必須得到完全一致的結果。
    func testBothRotationDirectionsAgree() {
        let cases: [(rpm: Double, duration: Double, scaleError: Double)] = [
            (50.0 / 3.0, 90, 0.02),
            (100.0 / 3.0, 60, 0.01),
            (45.0, 60, 0.005),
            (78.0, 60, 0.005)
        ]
        for c in cases {
            let forward = trace(rpm: c.rpm, duration: c.duration, scaleError: c.scaleError,
                                useMagnetometer: true, reversedYaw: false)
            let reverse = trace(rpm: c.rpm, duration: c.duration, scaleError: c.scaleError,
                                useMagnetometer: true, reversedYaw: true)
            XCTAssertEqual(forward.revolutions, reverse.revolutions, "\(c.rpm) RPM：圈數不一致")
            XCTAssertEqual(forward.maxError, reverse.maxError, accuracy: 0.01,
                           "\(c.rpm) RPM：轉向不該影響誤差")
            let bound = errorBound(rpm: c.rpm, scaleError: c.scaleError)
            XCTAssertLessThan(reverse.maxError, bound)
            XCTAssertLessThan(reverse.maxErrorLastThird, reverse.maxErrorFirstThird * 2.0 + 1.0)
        }
    }

    func testMagneticAnchoringStopsPhaseDrift() {
        for reversed in [false, true] {
            let anchored = trace(rpm: 100.0 / 3.0, duration: 60, scaleError: 0.01,
                                 useMagnetometer: true, reversedYaw: reversed)
            XCTAssertLessThan(anchored.maxError, errorBound(rpm: 100.0 / 3.0, scaleError: 0.01),
                              "錨定後的誤差應落在「一個取樣 + ε×360°」之內")
            XCTAssertEqual(anchored.revolutions, 33)
        }
        let freeRunning = trace(rpm: 100.0 / 3.0, duration: 60, scaleError: 0.01,
                                useMagnetometer: false)
        XCTAssertGreaterThan(freeRunning.finalError, 100.0,
                             "純陀螺儀積分 60 秒應該漂移約 120° —— 這正是需要錨定的理由")
    }

    func testNoDriftWhenGyroIsPerfect() {
        let result = trace(rpm: 45.0, duration: 30, scaleError: 0, useMagnetometer: true)
        XCTAssertLessThan(result.maxError, errorBound(rpm: 45.0, scaleError: 0))
        XCTAssertEqual(result.revolutions, 22)
    }

    /// 回歸測試：錨點若設成偵測到的實際位置而不是推進整整一圈，
    /// 不足一個取樣的超調量會逐圈累積。45 轉 60 秒 44 圈會累積到 79°。
    func testAnchorOvershootDoesNotAccumulate() {
        let result = trace(rpm: 45.0, duration: 60, scaleError: 0, useMagnetometer: true)
        XCTAssertLessThan(result.maxError, errorBound(rpm: 45.0, scaleError: 0))
        XCTAssertLessThan(result.maxErrorLastThird, result.maxErrorFirstThird * 2.0 + 1.0,
                          "誤差不該隨時間成長：前 1/3 \(result.maxErrorFirstThird)°，後 1/3 \(result.maxErrorLastThird)°")
        XCTAssertEqual(result.revolutions, 44)
    }

    func testErrorBoundHoldsAcrossAllSpeeds() {
        let cases: [(rpm: Double, duration: Double, scaleError: Double)] = [
            (50.0 / 3.0, 90, 0.02),
            (100.0 / 3.0, 60, 0.01),
            (45.0, 60, 0.005),
            (78.0, 60, 0.005)
        ]
        for c in cases {
            let result = trace(rpm: c.rpm, duration: c.duration, scaleError: c.scaleError,
                               useMagnetometer: true)
            let bound = errorBound(rpm: c.rpm, scaleError: c.scaleError)
            XCTAssertLessThan(result.maxError, bound,
                              "\(c.rpm) RPM：最大誤差 \(result.maxError)°，上界 \(bound)°")
            XCTAssertLessThan(result.maxErrorLastThird, result.maxErrorFirstThird * 2.0 + 1.0,
                              "\(c.rpm) RPM：誤差隨時間成長了")
        }
    }

    /// 校準倍率的即時估計：合成訊號裡磁北是完美的，所以 k 應該精準回推 1/(1+ε)。
    /// 真機上要跑滿數十圈磁力計才會主導，這裡只驗算式本身。
    func testCalibrationEstimateRecoversScaleFactor() {
        for reversed in [false, true] {
            for epsilon in [0.03, 0.01, -0.015] {
                let result = trace(rpm: 100.0 / 3.0, duration: 120, scaleError: epsilon,
                                   useMagnetometer: true, reversedYaw: reversed)
                let k = result.calibrationEstimate
                XCTAssertNotNil(k)
                let expected = 1.0 / (1.0 + epsilon)
                XCTAssertEqual(k!, expected, accuracy: expected * 0.002,
                               "ε=\(epsilon) reversed=\(reversed)")
            }
        }
    }

    func testCalibrationEstimateIsNilWithoutMagnetometer() {
        let result = trace(rpm: 100.0 / 3.0, duration: 60, scaleError: 0, useMagnetometer: false)
        XCTAssertNil(result.calibrationEstimate)
        XCTAssertEqual(result.magneticTotalDegrees, 0, accuracy: 1e-9)
        XCTAssertGreaterThan(result.gyroTotalDegrees, 10_000)
    }
}
