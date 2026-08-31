import XCTest
@testable import TurntableCore

final class StabilityGateTests: XCTestCase {

    /// 造一段量測：前面接一段加速（或任何前綴），後面接一段減速。
    private func run(leadInSeconds: Double = 0,
                     steadySeconds: Double = 60,
                     tailSeconds: Double = 0,
                     rpm: Double = 100.0 / 3.0,
                     fs: Double = 100,
                     leadInFrom: Double = 0,
                     tailTo: Double = 0) -> [SpinSample] {
        let steady = rpm * 6.0
        var out: [SpinSample] = []
        var t = 0.0
        for i in 0 ..< Int(leadInSeconds * fs) {
            let frac = Double(i) / max(1, leadInSeconds * fs)
            out.append(SpinSample(t: t, omega: leadInFrom + (steady - leadInFrom) * frac))
            t += 1 / fs
        }
        for i in 0 ..< Int(steadySeconds * fs) {
            // 一點正常的抖晃，± 0.5%
            let wow = steady * 0.005 * sin(2 * Double.pi * 0.55 * Double(i) / fs)
            out.append(SpinSample(t: t, omega: steady + wow))
            t += 1 / fs
        }
        for i in 0 ..< Int(tailSeconds * fs) {
            let frac = Double(i) / max(1, tailSeconds * fs)
            out.append(SpinSample(t: t, omega: steady + (tailTo - steady) * frac))
            t += 1 / fs
        }
        return out
    }

    // MARK: - 基本行為

    /// 乾淨的量測不該被動到。使用者照正確順序操作時，程式不能自作聰明砍資料。
    func testPristineMeasurementIsUntouched() {
        let s = run(steadySeconds: 60)
        let w = StabilityGate.find(s)
        XCTAssertNotNil(w)
        XCTAssertTrue(w!.isPristine, "乾淨的資料不該被裁切，實際丟了 \(w!.droppedTotal) 筆")
        XCTAssertEqual(w!.range, 0 ..< s.count)
    }

    /// 從靜止開始的加速段必須被切掉 —— 這是最常見也最嚴重的污染。
    func testTrimsSpinUpFromRest() {
        let s = run(leadInSeconds: 6, steadySeconds: 60, leadInFrom: 0)
        let w = StabilityGate.find(s)!
        XCTAssertGreaterThan(w.droppedAtStart, Int(5.0 * 100), "6 秒的加速段幾乎要全丟")
        XCTAssertEqual(w.droppedAtEnd, 0)
        XCTAssertEqual(w.droppedInMiddle, 0)
        // 保留區間的平均要回到標稱值，不被加速段拉低。
        let kept = Array(s[w.range])
        XCTAssertEqual(SpeedStatistics.meanRPM(kept)!, 100.0 / 3.0, accuracy: 0.05)
    }

    /// 結束後才停轉盤是對的操作；但先停轉盤再按停止的話，尾端的減速要切掉。
    func testTrimsSpinDownAtEnd() {
        let s = run(steadySeconds: 60, tailSeconds: 5, tailTo: 0)
        let w = StabilityGate.find(s)!
        XCTAssertEqual(w.droppedAtStart, 0)
        XCTAssertGreaterThan(w.droppedAtEnd, Int(4.0 * 100))
    }

    func testTrimsBothEnds() {
        let s = run(leadInSeconds: 5, steadySeconds: 60, tailSeconds: 5,
                    leadInFrom: 0, tailTo: 0)
        let w = StabilityGate.find(s)!
        XCTAssertGreaterThan(w.droppedAtStart, Int(4.0 * 100))
        XCTAssertGreaterThan(w.droppedAtEnd, Int(4.0 * 100))
        let kept = Array(s[w.range])
        XCTAssertEqual(SpeedStatistics.meanRPM(kept)!, 100.0 / 3.0, accuracy: 0.05)
    }

    // MARK: - 不能過度反應

    /// 單一根毛刺不該把資料切成兩半。
    ///
    /// 少了 closeShortGaps，取最長區間的策略會在毛刺處斷開，然後丟掉其中一半 ——
    /// 一個取樣的雜訊造成 50% 的資料損失。
    func testSingleSpikeDoesNotSplitTheRecording() {
        var s = run(steadySeconds: 60)
        s[3000] = SpinSample(t: s[3000].t, omega: s[3000].omega * 3)   // 一根毛刺
        let w = StabilityGate.find(s)!
        XCTAssertGreaterThan(w.range.count, s.count - 100,
                             "毛刺只該被忽略，不該讓區間斷開")
    }

    /// 正常的抖晃不該被當成不穩定。實測唱盤的瞬時偏差在 ±1.5% 以內。
    func testNormalWowIsNotTrimmed() {
        let fs = 100.0, steady = (100.0 / 3.0) * 6.0
        var s: [SpinSample] = []
        for i in 0 ..< 6000 {
            let wow = steady * 0.015 * sin(2 * Double.pi * 0.53 * Double(i) / fs)
            s.append(SpinSample(t: Double(i) / fs, omega: steady + wow))
        }
        XCTAssertTrue(StabilityGate.find(s)!.isPristine)
    }

    // MARK: - 中途干擾

    /// 量測中途碰到桌子：保留最長的那一段，而且要如實回報中間丟了東西。
    func testReportsMidRecordingDisturbance() {
        var s = run(steadySeconds: 60)
        for i in 2000 ..< 2200 {              // 2 秒的干擾，超過 gapSeconds
            s[i] = SpinSample(t: s[i].t, omega: s[i].omega * 0.5)
        }
        let w = StabilityGate.find(s)!
        XCTAssertFalse(w.isPristine)
        XCTAssertGreaterThan(w.droppedInMiddle + w.droppedAtStart, 0)
        // 保留的是後面比較長的那一段。
        XCTAssertGreaterThan(w.range.count, 3000)
    }

    // MARK: - 失敗情況

    /// 全程都在加速 —— 沒有可用的區間，要回 nil 而不是硬給一段。
    func testReturnsNilWhenNothingIsStable() {
        var s: [SpinSample] = []
        for i in 0 ..< 3000 {
            s.append(SpinSample(t: Double(i) / 100, omega: Double(i) * 0.1))
        }
        XCTAssertNil(StabilityGate.find(s))
    }

    /// 穩定段太短也不能用。
    func testRejectsTooShortStableSection() {
        let s = run(leadInSeconds: 10, steadySeconds: 2, leadInFrom: 0)
        XCTAssertNil(StabilityGate.find(s, minimumSeconds: 5))
    }

    func testRejectsTooFewSamples() {
        XCTAssertNil(StabilityGate.find([]))
        XCTAssertNil(StabilityGate.find([SpinSample(t: 0, omega: 200)]))
    }

    // MARK: - 與分析串起來

    /// 這是整件事的重點：被加速段污染的資料，頻譜診斷會出錯。
    ///
    /// 一段 −100% 的前綴會在低頻灌進巨大能量，把每圈一次的偏心峰淹掉。
    /// 套上 StabilityGate 之後，偏心必須重新變成最強的峰。
    func testGateRescuesSpectralDiagnosis() {
        let rot = (100.0 / 3.0) / 60.0
        let clean = SyntheticSignal.make(
            nominalRPM: 100.0 / 3.0, durationSeconds: 90,
            wow: [WowComponent(amplitudePercent: 0.40, frequencyHz: rot)]).samples

        // 前面接 8 秒從靜止開始的加速
        var polluted: [SpinSample] = []
        let t0 = clean[0].t
        for i in 0 ..< 800 {
            let frac = Double(i) / 800
            polluted.append(SpinSample(t: t0 - 8.0 + Double(i) / 100,
                                       omega: 200.0 * frac))
        }
        polluted.append(contentsOf: clean)

        // MeasurementAnalysis 內建閘門，所以直接餵髒資料也會得到乾淨的結果。
        let a = MeasurementAnalysis.analyze(samples: polluted)!

        XCTAssertEqual(a.onePerRevolutionPercent, 0.40, accuracy: 0.05,
                       "偏心峰不能被加速段淹掉")
        XCTAssertEqual(a.meanRPM, 100.0 / 3.0, accuracy: 0.1)
        XCTAssertEqual(a.trimmedStartSeconds, 8.0, accuracy: 0.5, "開頭 8 秒要被切掉")
        XCTAssertEqual(a.trimmedEndSeconds, 0, accuracy: 0.1)
        XCTAssertFalse(a.stableWindow.isPristine)

        // 對照組：不切區間直接平均，會被加速段拉低 —— 這就是閘門存在的理由。
        let ungated = SpeedStatistics.meanRPM(polluted)!
        XCTAssertLessThan(ungated, a.meanRPM - 0.5,
                          "沒有閘門的話平均轉速會被拉低 \(a.meanRPM - ungated) RPM")
    }

    /// 乾淨的量測經過 analyze 之後不該回報任何裁切。
    func testAnalysisReportsNoTrimForCleanData() {
        let run = SyntheticSignal.make(nominalRPM: 100.0 / 3.0, durationSeconds: 60,
                                       wow: [WowComponent(amplitudePercent: 0.3,
                                                          frequencyHz: 0.5556)])
        let a = MeasurementAnalysis.analyze(samples: run.samples)!
        XCTAssertTrue(a.stableWindow.isPristine)
        XCTAssertEqual(a.trimmedStartSeconds, 0, accuracy: 0.01)
        XCTAssertEqual(a.trimmedEndSeconds, 0, accuracy: 0.01)
    }
}
