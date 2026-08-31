import Foundation
import XCTest
import TurntableCore

/// FFT 前必做：CoreMotion 的派送間隔會抖動。
final class UniformResamplerTests: XCTestCase {

    func testRecoversKnownSineFromJitteredTimestamps() {
        var rng = SplitMix64(seed: 31)
        var samples: [SpinSample] = []
        var t = 0.0
        while t < 20.0 {
            samples.append(SpinSample(t: t, omega: 200.0 + 1.0 * sin(2.0 * Double.pi * 0.5 * t)))
            t += 0.01 * (0.6 + 0.8 * rng.nextUniform())
        }
        let resampled = UniformResampler.resample(samples, sampleRate: 100.0)
        XCTAssertNotNil(resampled)
        let values = resampled!.values
        for i in 0 ..< values.count {
            let time = Double(i) / 100.0
            let expected = 200.0 + 1.0 * sin(2.0 * Double.pi * 0.5 * time)
            XCTAssertEqual(values[i], expected, accuracy: 0.01, "第 \(i) 筆")
        }
    }

    func testRejectsTooFewSamples() {
        XCTAssertNil(UniformResampler.resample([SpinSample(t: 0, omega: 200)], sampleRate: 100))
    }
}
