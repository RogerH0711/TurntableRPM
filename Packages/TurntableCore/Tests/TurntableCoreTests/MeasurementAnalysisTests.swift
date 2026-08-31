import XCTest
@testable import TurntableCore

final class MeasurementAnalysisTests: XCTestCase {

    /// 33⅓ 轉 = 0.5556 Hz。每圈一次的 wow 就是這個頻率。
    private let rot33 = (100.0 / 3.0) / 60.0

    // MARK: - 譜峰判讀

    /// 每圈一次的偏心必須被認出來，而且振幅要對得上。
    ///
    /// 這是真機上最重要的一項：實測 TD 235 EV 的 1× 成分是 0.40%，
    /// 佔了加權 W&F 功率的一半。
    func testIdentifiesOncePerRevolutionEccentricity() {
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.40, frequencyHz: rot33)])

        let a = MeasurementAnalysis.analyze(samples: run.samples)
        XCTAssertNotNil(a)
        let top = a!.peaks.first
        XCTAssertNotNil(top)
        XCTAssertEqual(top!.frequencyHz, rot33, accuracy: 0.02)
        XCTAssertEqual(top!.orderOfRotation, 1.0, accuracy: 0.04)
        XCTAssertTrue(top!.isRotationHarmonic)
        XCTAssertEqual(top!.amplitudePercent, 0.40, accuracy: 0.04)
        XCTAssertEqual(a!.onePerRevolutionPercent, 0.40, accuracy: 0.04)
        XCTAssertTrue(top!.interpretation.contains("偏心"))
    }

    /// 非整數倍的峰要被判成傳動鏈零件，不能誤認成轉盤諧波。
    ///
    /// 實測 TD 235 EV 在 18.82 Hz 有一根峰，是轉盤基頻的 35.32 倍 —— 不是整數，
    /// 所以它來自馬達而不是盤面。這個判別是頻譜真正的診斷價值所在。
    func testDistinguishesDriveChainPeakFromHarmonic() {
        let driveHz = rot33 * 35.32          // 刻意非整數倍
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.30, frequencyHz: rot33),
                  WowComponent(amplitudePercent: 0.20, frequencyHz: driveHz)])

        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        let drive = a.peaks.first { abs($0.frequencyHz - driveHz) < 0.05 }
        XCTAssertNotNil(drive, "傳動鏈那根峰要找得到")
        XCTAssertFalse(drive!.isRotationHarmonic)
        XCTAssertEqual(drive!.orderOfRotation, 35.32, accuracy: 0.1)
        XCTAssertTrue(drive!.interpretation.contains("非諧波"))

        let one = a.peaks.first { $0.isRotationHarmonic }
        XCTAssertNotNil(one)
        XCTAssertEqual(one!.orderOfRotation, 1.0, accuracy: 0.04)
    }

    /// 2× 是盤面橢圓，判讀文字要跟 1× 不同。
    func testSecondHarmonicHasItsOwnInterpretation() {
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.35, frequencyHz: rot33 * 2)])
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        let top = a.peaks.first!
        XCTAssertEqual(top.orderOfRotation, 2.0, accuracy: 0.04)
        XCTAssertTrue(top.isRotationHarmonic)
        XCTAssertTrue(top.interpretation.contains("橢圓"))
    }

    // MARK: - 抖晃率

    /// 純正弦的 WRMS 應該是振幅 × W(f) ÷ √2。
    func testWowFlutterMatchesTheoryForPureSine() {
        let f = 4.0                                   // 加權曲線的峰值頻率，W(f)=1
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 60,
            wow: [WowComponent(amplitudePercent: 0.20, frequencyHz: f)])
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        XCTAssertEqual(a.wowFlutter.wrmsPercent, 0.20 / 2.0.squareRoot(), accuracy: 0.01)
    }

    // MARK: - 極座標

    /// 每圈一次的偏心在極座標上必須集中在一個角度，而不是均勻攤開。
    func testPolarBinsLocaliseEccentricity() {
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.50, frequencyHz: rot33)])
        let a = MeasurementAnalysis.analyze(samples: run.samples)!

        XCTAssertNotNil(a.peakAngleDegrees)
        let values = a.polarBins.map { $0.meanDeviation }
        let peak = values.map { abs($0) }.max()!
        XCTAssertGreaterThan(peak, 0.3, "偏心應該在某個角度累積出可見的偏差")

        // 每格樣本數的保證是「每圈最多差一個樣本」，不是「幾乎相等」。
        //
        // 33⅓ 轉、100 Hz、72 格時，每格每圈只拿到 180/72 = 2.5 個樣本。2.5 不是
        // 整數，所以每次經過只能拿 2 或 3 個 —— 跑 50 圈之後就是 100 對 150。
        // 這是分箱的量化必然，不是 bug。真正該擋的是「有格子完全沒被走到」
        // 或「差距遠超過圈數」，那才代表角度累積壞了。
        let counts = a.polarBins.map { $0.count }
        let revolutions = Int((a.durationSeconds * a.rotationHz).rounded())
        XCTAssertGreaterThan(counts.min()!, 0, "不能有格子完全沒被走到")
        XCTAssertLessThanOrEqual(counts.max()! - counts.min()!, revolutions + 2,
                                 "每格每圈最多差一個樣本")
    }

    /// 沒有 wow 的乾淨訊號不該生出假峰。
    func testCleanSignalProducesNoSpuriousPeaks() {
        let run = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 60)
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        XCTAssertLessThan(a.wowFlutter.wrmsPercent, 0.01)
        XCTAssertEqual(a.onePerRevolutionPercent, 0, accuracy: 0.02)
    }

    // MARK: - 基本量

    func testReportsSpeedAndDuration() {
        let run = SyntheticSignal.make(nominalRPM: 45.0, durationSeconds: 30)
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        XCTAssertEqual(a.meanRPM, 45.0, accuracy: 0.01)
        XCTAssertEqual(a.rotationHz, 45.0 / 60.0, accuracy: 0.001)
        XCTAssertEqual(a.durationSeconds, 30.0, accuracy: 0.2)
    }

    /// 樣本太少要回 nil，不能硬給一個沒有意義的分析。
    func testRejectsTooFewSamples() {
        let run = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 0.3)
        XCTAssertNil(MeasurementAnalysis.analyze(samples: run.samples))
    }

    // MARK: - 主導成分

    /// 單一正弦：最強的峰應該佔掉絕大部分功率。
    func testDominantPeakShareIsHighForSingleTone() {
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.40, frequencyHz: rot33)])
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        XCTAssertGreaterThan(a.dominantPeakShare, 0.9)
    }

    /// 多個相當的成分：沒有單一主導。
    func testDominantPeakShareIsLowWhenSpread() {
        let run = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.20, frequencyHz: rot33),
                  WowComponent(amplitudePercent: 0.20, frequencyHz: rot33 * 2),
                  WowComponent(amplitudePercent: 0.20, frequencyHz: rot33 * 5),
                  WowComponent(amplitudePercent: 0.20, frequencyHz: 4.0)])
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        XCTAssertLessThan(a.dominantPeakShare, 0.45)
    }

    /// 這個指標比峰值/RMS 穩定：實測同一台唱盤兩次得到 1.67 與 1.95，
    /// 譜峰內容卻一樣。換成功率佔比之後，同樣的頻譜要給出同樣的結論。
    func testShareIsStableAcrossRunsWithSameContent() {
        var shares: [Double] = []
        for seed in UInt64(1) ... 4 {
            let run = SyntheticSignal.make(
                nominalRPM: 100.0 / 3.0, durationSeconds: 90,
                wow: [WowComponent(amplitudePercent: 0.42, frequencyHz: rot33),
                      WowComponent(amplitudePercent: 0.047, frequencyHz: rot33 * 35.32)],
                noisePercent: 0.03, seed: seed)
            shares.append(MeasurementAnalysis.analyze(samples: run.samples)!.dominantPeakShare)
        }
        XCTAssertLessThan(shares.max()! - shares.min()!, 0.05,
                          "同樣的頻譜內容應該得到一致的佔比，實際 \(shares)")
        XCTAssertGreaterThan(shares.min()!, 0.6, "0.42% 對 0.047% 顯然是單頻主導")
    }
}
