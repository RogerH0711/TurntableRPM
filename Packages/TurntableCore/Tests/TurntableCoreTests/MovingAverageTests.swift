import Foundation
import XCTest
import TurntableCore

/// 規格 §3.3：移動平均是低通濾波器，這是「別把抖晃率算在平滑訊號上」的證據。
final class MovingAverageTests: XCTestCase {

    func testFirstNullFrequencies() {
        let fs = 100.0
        let expected: [Int: Double] = [10: 10.0, 25: 4.0, 50: 2.0, 100: 1.0, 300: 100.0 / 300.0]
        for (n, nullHz) in expected {
            XCTAssertEqual(MovingAverage.firstNullFrequency(window: n, sampleRate: fs), nullHz, accuracy: 1e-9)
            let response = MovingAverage.magnitudeResponse(frequency: nullHz, window: n, sampleRate: fs)
            XCTAssertLessThan(response, 1e-12, "N=\(n) 在 \(nullHz) Hz 應該是零點")
        }
    }

    func testWindow25KillsTheWeightingPeak() {
        // N=25（0.25 s）的第一個零點正好落在加權曲線的 4 Hz 峰值上。
        XCTAssertEqual(MovingAverage.firstNullFrequency(window: 25, sampleRate: 100), 4.0, accuracy: 1e-12)
        let response = MovingAverage.magnitudeResponse(frequency: 4.0, window: 25, sampleRate: 100)
        XCTAssertLessThan(response, 1e-12)
    }

    func testCutoffIsMinus3dB() {
        for n in [10, 25, 50, 100, 300] {
            let fc = MovingAverage.cutoffFrequency(window: n, sampleRate: 100)
            let db = 20 * log10(MovingAverage.magnitudeResponse(frequency: fc, window: n, sampleRate: 100))
            XCTAssertEqual(db, -3.0, accuracy: 0.05, "N=\(n)")
        }
    }

    func testSmoothingReducesNoise() {
        var rng = SplitMix64(seed: 3)
        let noisy = (0 ..< 4000).map { _ in rng.nextGaussian() }
        let smoothed = MovingAverage.apply(noisy, window: 50)
        func rms(_ x: ArraySlice<Double>) -> Double {
            var s = 0.0
            for v in x { s += v * v }
            return (s / Double(x.count)).squareRoot()
        }
        // 白雜訊經 N 點平均後標準差約降為 1/√N
        XCTAssertEqual(rms(smoothed[100 ..< 3900]), 1.0 / Double(50).squareRoot(), accuracy: 0.03)
    }

    func testConstantSignalIsUnchanged() {
        let x = [Double](repeating: 7.5, count: 500)
        for v in MovingAverage.apply(x, window: 51) {
            XCTAssertEqual(v, 7.5, accuracy: 1e-12)
        }
    }
}
