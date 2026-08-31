import Foundation
import XCTest
import TurntableCore

/// 規格 §3.4。黃金值 = A × W(f) / √2，由 Python 參考實作獨立算出。
final class WowFlutterTests: XCTestCase {

    func testWeightingCurveAnchors() {
        // AES 公布的錨點；閉式近似的最大誤差 3.5%
        let anchors: [(f: Double, standard: Double, tolerance: Double)] = [
            (0.2, 0.0296, 0.05),
            (0.8, 0.5000, 0.04),
            (4.0, 1.0000, 0.01),
            (20.0, 0.5080, 0.01)
        ]
        for anchor in anchors {
            let w = WowFlutterWeighting.weight(anchor.f)
            XCTAssertEqual(w, anchor.standard, accuracy: anchor.standard * anchor.tolerance,
                           "W(\(anchor.f) Hz) = \(w)")
        }
    }

    func testWeightingCurveGoldenValues() {
        let golden: [(Double, Double)] = [
            (0.2, 0.02924499), (0.5, 0.25526499), (0.8, 0.51756588), (1.0, 0.64742712),
            (2.0, 0.92501937), (3.15, 0.99212208), (4.0, 0.99999080), (6.0, 0.97415414),
            (10.0, 0.85541769), (20.0, 0.50805897), (50.0, 0.09462257)
        ]
        for (f, expected) in golden {
            XCTAssertEqual(WowFlutterWeighting.weight(f), expected, accuracy: 1e-7, "W(\(f))")
        }
        XCTAssertEqual(WowFlutterWeighting.weight(0), 0)
        // 峰值落在 3.968 Hz，且值為 1
        XCTAssertEqual(WowFlutterWeighting.weight(3.9683751), 1.0, accuracy: 1e-9)
    }

    private func deviation(amplitude: Double, frequency: Double) -> [Double] {
        let run = SyntheticSignal.make(nominalRPM: 100.0 / 3.0,
                                       durationSeconds: 60,
                                       wow: [WowComponent(amplitudePercent: amplitude, frequencyHz: frequency)])
        return DeviationSeries.make(from: run.trueOmega)!.deviationPercent
    }

    func testWRMSOfPureSineMatchesTheory() {
        let cases: [(amplitude: Double, frequency: Double, expected: Double)] = [
            (0.5, 4.0, 0.35355014),
            (0.5, 0.8, 0.18298717),
            (0.5, 20.0, 0.17962597),
            (1.0, 4.0, 0.70710027),
            (0.2, 2.0, 0.13081749)
        ]
        for c in cases {
            let result = WowFlutterAnalyzer.analyze(deviationPercent: deviation(amplitude: c.amplitude,
                                                                                frequency: c.frequency),
                                                    sampleRate: 100)
            XCTAssertNotNil(result)
            XCTAssertEqual(result!.wrmsPercent, c.expected, accuracy: c.expected * 0.015,
                           "A=\(c.amplitude)% @ \(c.frequency) Hz")
        }
    }

    func testPeakToRMSRatioIdentifiesSourceType() {
        // 單頻正弦 wow（偏心、皮帶接縫）-> 約 1.41
        let sine = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 120,
                                        wow: [WowComponent(amplitudePercent: 0.5, frequencyHz: 4.0)])
        let sineResult = WowFlutterAnalyzer.analyze(
            deviationPercent: DeviationSeries.make(from: sine.trueOmega)!.deviationPercent,
            sampleRate: 100)!
        XCTAssertEqual(sineResult.peakToRMSRatio, 1.41, accuracy: 0.06)

        // 高斯型隨機抖動（軸承、馬達雜訊）-> 約 1.96
        let noise = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 120,
                                         noisePercent: 0.5, seed: 7)
        let noiseResult = WowFlutterAnalyzer.analyze(
            deviationPercent: DeviationSeries.make(from: noise.trueOmega)!.deviationPercent,
            sampleRate: 100)!
        XCTAssertEqual(noiseResult.peakToRMSRatio, 1.96, accuracy: 0.12)
    }

    func testWeightingRejectsVerySlowDrift() {
        // 0.05 Hz 的慢漂移在標準曲線下幾乎不計分（W < 0.002）
        let result = WowFlutterAnalyzer.analyze(deviationPercent: deviation(amplitude: 2.0, frequency: 0.05),
                                                sampleRate: 100)!
        XCTAssertLessThan(result.wrmsPercent, 0.02)
    }

    func testMaxDeviationDependsOnSmoothingWindow() {
        // 這就是「最大偏差必須連同頻寬一起回報」的原因
        let d = deviation(amplitude: 0.5, frequency: 4.0)
        let tight = DeviationSeries.maxDeviation(d, sampleRate: 100, smoothingWindow: 5)
        let loose = DeviationSeries.maxDeviation(d, sampleRate: 100, smoothingWindow: 40)
        XCTAssertEqual(tight.windowSeconds, 0.05, accuracy: 1e-12)
        XCTAssertEqual(loose.windowSeconds, 0.4, accuracy: 1e-12)
        XCTAssertGreaterThan(tight.value, loose.value * 3.0,
                             "同一段訊號在不同平滑視窗下的最大偏差可以差好幾倍")
    }
}
