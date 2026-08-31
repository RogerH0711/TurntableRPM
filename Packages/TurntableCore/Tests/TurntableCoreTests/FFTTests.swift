import Foundation
import XCTest
import TurntableCore

final class FFTTests: XCTestCase {

    /// 直接定義的 DFT，用來當 FFT 的獨立對照。
    private func naiveDFT(_ x: [Double]) -> (re: [Double], im: [Double]) {
        let n = x.count
        var re = [Double](repeating: 0, count: n)
        var im = [Double](repeating: 0, count: n)
        for k in 0 ..< n {
            for t in 0 ..< n {
                let angle = -2.0 * Double.pi * Double(k) * Double(t) / Double(n)
                re[k] += x[t] * cos(angle)
                im[k] += x[t] * sin(angle)
            }
        }
        return (re, im)
    }

    func testMatchesNaiveDFT() {
        var rng = SplitMix64(seed: 17)
        let x = (0 ..< 64).map { _ in rng.nextGaussian() }
        let expected = naiveDFT(x)
        var re = x
        var im = [Double](repeating: 0, count: 64)
        FFT.transform(real: &re, imag: &im)
        for k in 0 ..< 64 {
            XCTAssertEqual(re[k], expected.re[k], accuracy: 1e-9, "bin \(k) 實部")
            XCTAssertEqual(im[k], expected.im[k], accuracy: 1e-9, "bin \(k) 虛部")
        }
    }

    func testRoundTrip() {
        var rng = SplitMix64(seed: 23)
        let x = (0 ..< 1024).map { _ in rng.nextGaussian() }
        var re = x
        var im = [Double](repeating: 0, count: 1024)
        FFT.transform(real: &re, imag: &im, inverse: false)
        FFT.transform(real: &re, imag: &im, inverse: true)
        for i in 0 ..< 1024 {
            XCTAssertEqual(re[i], x[i], accuracy: 1e-10)
            XCTAssertEqual(im[i], 0, accuracy: 1e-10)
        }
    }

    func testAmplitudeSpectrumFindsKnownTone() {
        // 33⅓ 轉的每圈一次分量：0.5556 Hz，振幅 0.4%
        let fs = 100.0
        let f0 = (100.0 / 3.0) / 60.0
        let n = 6000
        let x = (0 ..< n).map { 0.4 * sin(2.0 * Double.pi * f0 * Double($0) / fs) }
        let spectrum = FFT.amplitudeSpectrum(x, sampleRate: fs)
        var peakIndex = 0
        for k in 1 ..< spectrum.amplitudes.count where spectrum.amplitudes[k] > spectrum.amplitudes[peakIndex] {
            peakIndex = k
        }
        XCTAssertEqual(spectrum.frequencies[peakIndex], f0, accuracy: 0.02)
        XCTAssertEqual(spectrum.amplitudes[peakIndex], 0.4, accuracy: 0.05)
    }

    func testPowerOfTwoHelpers() {
        XCTAssertTrue(FFT.isPowerOfTwo(1024))
        XCTAssertFalse(FFT.isPowerOfTwo(6000))
        XCTAssertEqual(FFT.nextPowerOfTwo(atLeast: 6000), 8192)
        XCTAssertEqual(FFT.nextPowerOfTwo(atLeast: 12000), 16384)
    }
}
