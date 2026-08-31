import XCTest
@testable import TurntableCore

final class MagneticRevolutionCounterTests: XCTestCase {

    /// 合成一組「手機平放在水平盤面上轉動」時，裝置座標系看到的地磁與重力。
    ///
    /// 房間座標系的地磁是 (H, 0, V)，盤面繞鉛直軸轉 θ，手機相對盤面有固定傾角 α。
    /// 裝置座標 = R_x(−α) · R_z(−θ) 作用在房間向量上。
    private func synthesize(theta: Double,
                           tiltRadians a: Double = 0,
                           H: Double = 22.0,
                           vertical V: Double = 41.0,
                           localOffset: Vector3 = Vector3(0, 0, 0)) -> (field: Vector3, gravity: Vector3) {
        // localOffset 是裝置座標系裡的固定量 —— 磁吸殼、磁化盤面這類「跟著手機一起轉」
        // 的磁場，在裝置座標系裡看起來是靜止的，所以直接加上去。
        let field = Vector3(H * cos(theta) + localOffset.x,
                            cos(a) * (-H * sin(theta)) + sin(a) * V + localOffset.y,
                            sin(a) * (H * sin(theta)) + cos(a) * V + localOffset.z)
        let gravity = Vector3(0, -sin(a), -cos(a))
        return (field, gravity)
    }

    private func run(revolutions: Double,
                     tiltRadians: Double = 0,
                     samplesPerRevolution: Int = 180,
                     reversed: Bool = false,
                     horizontal: Double = 22.0,
                     localOffset: Vector3 = Vector3(0, 0, 0)) -> MagneticRevolutionCounter {
        var counter = MagneticRevolutionCounter()
        let total = Int(revolutions * Double(samplesPerRevolution))
        for i in 0 ... total {
            var theta = 2 * Double.pi * Double(i) / Double(samplesPerRevolution)
            if reversed { theta = -theta }
            let s = synthesize(theta: theta, tiltRadians: tiltRadians,
                               H: horizontal, localOffset: localOffset)
            counter.add(field: s.field, gravity: s.gravity)
        }
        return counter
    }

    func testCountsCompleteRevolutions() {
        let counter = run(revolutions: 10.5)
        XCTAssertEqual(counter.revolutions, 10)
        XCTAssertEqual(counter.totalDegrees, 3780, accuracy: 1e-6)
    }

    /// 唱盤從上方看是順時針，裝置姿態的角度遞減。這是 CLAUDE.md 第 4 個坑的同型測試：
    /// 凡是會累積角度的東西都要測正反兩個方向。
    func testReversedRotationCountsTheSame() {
        let forward = run(revolutions: 10.5)
        let backward = run(revolutions: 10.5, reversed: true)
        XCTAssertEqual(backward.revolutions, forward.revolutions)
        XCTAssertEqual(backward.totalDegrees, forward.totalDegrees, accuracy: 1e-6)
    }

    /// 手機相對盤面的固定傾角必須完全不影響角度。
    /// 這正是取重力方向當基底的用意 —— 傾角在投影裡代數上完全消掉。
    func testTiltIsProjectedOut() {
        for degrees in [0.0, 1.8, 5.0, 12.0] {
            let counter = run(revolutions: 10.5, tiltRadians: degrees * .pi / 180)
            XCTAssertEqual(counter.revolutions, 10, "傾角 \(degrees)°")
            XCTAssertEqual(counter.totalDegrees, 3780, accuracy: 1e-6, "傾角 \(degrees)°")
        }
    }

    /// 這條路徑存在的全部理由：它讀不到陀螺儀，所以陀螺儀錯多少都不影響它。
    ///
    /// 這裡的 1.01837 是刻意放大的合成比例因子誤差，不是實測值 ——
    /// 這支陀螺儀實測 k = 0.99915，太接近 1 反而測不出「有沒有真的還原」。
    /// 舊的 `PhaseIntegrator.calibrationEstimate` 在這個情境會回報 1.0（同義反覆），
    /// 這裡必須回報 1.01837。
    func testRecoversScaleFactorTheFusedPathCannot() {
        let counter = run(revolutions: 10.5)
        let trueTotalDegrees = 3780.0
        let gyroTotalDegrees = trueTotalDegrees / 1.01837

        let k = counter.calibrationFactor(gyroTotalDegrees: gyroTotalDegrees)
        XCTAssertNotNil(k)
        XCTAssertEqual(k!, 1.01837, accuracy: 1e-5)
    }

    func testReturnsNilBelowOneRevolution() {
        let counter = run(revolutions: 0.4)
        XCTAssertEqual(counter.revolutions, 0)
        XCTAssertNil(counter.calibrationFactor(gyroTotalDegrees: 144))
    }

    /// 重力讀不到就建不出基底，不能當成「轉了 0 度」，要整批略過。
    func testZeroGravityIsSafe() {
        var counter = MagneticRevolutionCounter()
        for i in 0 ... 360 {
            let s = synthesize(theta: 2 * Double.pi * Double(i) / 180)
            counter.add(field: s.field, gravity: Vector3(0, 0, 0))
        }
        XCTAssertEqual(counter.sampleCount, 0)
        XCTAssertEqual(counter.totalDegrees, 0)
        XCTAssertNil(counter.calibrationFactor(gyroTotalDegrees: 3600))
    }

    /// 沒有本地磁場時，地磁圓的圓心就在原點，水平分量是定值。
    func testCleanFieldHasConstantHorizontalMagnitude() {
        let counter = run(revolutions: 2.0)
        XCTAssertEqual(counter.horizontalMagnitude, 22.0, accuracy: 1e-6)
        XCTAssertEqual(counter.minHorizontal, 22.0, accuracy: 1e-6)
        XCTAssertEqual(counter.maxHorizontal, 22.0, accuracy: 1e-6)

        let range = counter.horizontalRange
        XCTAssertNotNil(range)
        XCTAssertEqual(range!.larger, 22.0, accuracy: 1e-6, "半徑")
        XCTAssertEqual(range!.smaller, 0.0, accuracy: 1e-6, "圓心偏移為零")
    }

    /// 偏移小於半徑時圓仍然包住原點，照樣繞得起來，只是水平分量開始擺盪。
    func testSmallOffsetStillWinds() {
        let counter = run(revolutions: 10.5, localOffset: Vector3(8, 0, 0))
        XCTAssertEqual(counter.revolutions, 10)
        XCTAssertEqual(counter.totalDegrees, 3780, accuracy: 0.5)

        let range = counter.horizontalRange!
        XCTAssertEqual(range.larger, 22.0, accuracy: 0.2, "會繞圈時，大的那個是半徑")
        XCTAssertEqual(range.smaller, 8.0, accuracy: 0.2, "小的那個是圓心偏移")
    }

    /// 真機遇到的情況：本地磁場蓋過地磁，圓心被推到半徑之外，角度只能來回擺盪。
    ///
    /// 這正是實測看到的 —— 盤面轉了 35 圈（12950°），地磁總轉角卻只累積 576°。
    func testLargeOffsetStopsWinding() {
        let counter = run(revolutions: 10.5, localOffset: Vector3(60, 0, 0))
        XCTAssertEqual(counter.revolutions, 0, "圓沒包住原點就繞不起來")
        XCTAssertLessThan(counter.totalDegrees, 360)

        let range = counter.horizontalRange!
        XCTAssertEqual(range.larger, 60.0, accuracy: 0.2, "繞不起來時，大的那個是圓心偏移")
        XCTAssertEqual(range.smaller, 22.0, accuracy: 0.2, "小的那個才是地磁半徑")
    }

    /// 取樣密度不影響總轉角 —— 解捲只要求相鄰樣本之間不超過半圈。
    func testSampleRateDoesNotChangeTheAngle() {
        for perRevolution in [12, 60, 180, 400] {
            let counter = run(revolutions: 10.5, samplesPerRevolution: perRevolution)
            XCTAssertEqual(counter.totalDegrees, 3780, accuracy: 1e-6,
                           "每圈 \(perRevolution) 個取樣")
        }
    }

    // MARK: - 扣掉圓心偏移之後的重新解捲

    /// 真機遇到的那個邊緣情況：圓心偏移 20.4 µT 只比半徑 19.7 µT 大一點點，
    /// 圓幾乎剛好通過原點，直接解捲繞不起來。擬合圓心減掉之後必須完全救回來。
    func testRefinedRescuesTheMarginalCase() {
        // 用 35.5 圈而不是剛好 35 圈：整數圈會落在 Int() 的邊界上，
        // 浮點誤差讓結果在 34/35 之間跳，那是測試的假象而不是程式的問題。
        let counter = run(revolutions: 35.5,
                          horizontal: 19.75,
                          localOffset: Vector3(20.45, 0, 0))

        XCTAssertEqual(counter.revolutions, 0, "直接解捲：圓沒包住原點，繞不起來")

        let refined = counter.refined()
        XCTAssertNotNil(refined)
        XCTAssertEqual(refined!.revolutions, 35, "扣掉圓心之後應該完全數得出來")
        XCTAssertEqual(refined!.totalDegrees, 35.5 * 360, accuracy: 1.0)
        XCTAssertEqual(refined!.centerOffset, 20.45, accuracy: 0.01)
        XCTAssertEqual(refined!.radius, 19.75, accuracy: 0.01)
        XCTAssertTrue(refined!.isTrustworthy)
    }

    /// 偏移大到完全沒得救的程度，擬合一樣要能還原。
    func testRefinedHandlesLargeOffset() {
        let counter = run(revolutions: 20.5, localOffset: Vector3(120, -80, 0))
        XCTAssertEqual(counter.revolutions, 0)

        let refined = counter.refined()!
        XCTAssertEqual(refined.revolutions, 20)
        XCTAssertEqual(refined.centerOffset, (120.0 * 120 + 80 * 80).squareRoot(), accuracy: 0.05)
        XCTAssertEqual(refined.radius, 22.0, accuracy: 0.05)
    }

    /// 沒有偏移時，擬合不該把好好的訊號弄壞。
    func testRefinedAgreesWithNaiveWhenClean() {
        let counter = run(revolutions: 10.5)
        let refined = counter.refined()!
        XCTAssertEqual(refined.revolutions, counter.revolutions)
        XCTAssertEqual(refined.totalDegrees, counter.totalDegrees, accuracy: 0.5)
        XCTAssertEqual(refined.centerOffset, 0, accuracy: 0.01)
        XCTAssertEqual(refined.radius, 22.0, accuracy: 0.01)
    }

    /// 擬合殘差是這個結果可不可信的把關指標。乾淨的圓殘差應該趨近零。
    func testResidualIsSmallForACleanCircle() {
        let refined = run(revolutions: 10.5, localOffset: Vector3(15, 0, 0)).refined()!
        XCTAssertLessThan(refined.residual, 0.01)
        XCTAssertTrue(refined.isTrustworthy)
    }

    /// 樣本太少擬合不出圓，要回 nil 而不是硬給一個數字。
    func testRefinedNeedsEnoughPoints() {
        var counter = MagneticRevolutionCounter()
        for i in 0 ..< 10 {
            let s = synthesize(theta: Double(i) * 0.05)
            counter.add(field: s.field, gravity: s.gravity)
        }
        XCTAssertNil(counter.refined())
    }

    /// 真機的坑：強垂直磁場 + 盤面軸不鉛直 = 垂直分量洩漏進水平投影。
    ///
    /// 這裡讓自轉軸相對鉛直傾斜（所以重力在裝置座標系裡每圈擺動），
    /// 同時把垂直磁場放大到 470 µT（實測 iPhone 自帶磁鐵環的量級）。
    /// 沒有逐樣本扣掉垂直分量的話，洩漏量會蓋過 22 µT 的水平訊號。
    func testStrongVerticalFieldDoesNotLeakIntoTheHorizontalPlane() {
        var counter = MagneticRevolutionCounter()
        let wobble = 1.8 * Double.pi / 180      // 盤面軸相對鉛直的傾角
        let samplesPerRevolution = 180
        let revolutions = 10.5
        let H = 22.0, V = 470.0

        for i in 0 ... Int(revolutions * Double(samplesPerRevolution)) {
            let theta = 2 * Double.pi * Double(i) / Double(samplesPerRevolution)

            // 自轉軸繞鉛直傾斜 wobble：重力在裝置座標系裡畫一個錐面。
            let g = Vector3(sin(wobble) * cos(theta), sin(wobble) * sin(theta), -cos(wobble))
            // 地磁水平分量隨轉動掃過一整圈；垂直分量沿真正的鉛直方向。
            let field = Vector3(H * cos(theta) + V * g.x,
                                -H * sin(theta) + V * g.y,
                                V * g.z)
            counter.add(field: field, gravity: g)
        }

        XCTAssertEqual(counter.revolutions, 10, "垂直分量必須被完整扣掉")
        XCTAssertEqual(counter.totalDegrees, 3780, accuracy: 5.0)
    }
}
