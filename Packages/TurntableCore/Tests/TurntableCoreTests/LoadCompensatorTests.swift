import Foundation
import XCTest
import TurntableCore

/// 規格 §3.8。
final class LoadCompensatorTests: XCTestCase {

    func testTwoPointExtrapolation() {
        let result = LoadCompensator.extrapolate(rpmWithPhone: 33.300,
                                                 rpmWithAddedMass: 33.240,
                                                 addedMassGrams: 100.0,
                                                 phoneMassGrams: 170.0)!
        XCTAssertEqual(result.slopeRPMPerGram, -0.0006, accuracy: 1e-12)
        XCTAssertEqual(result.phoneEffectRPM, -0.102, accuracy: 1e-12)
        XCTAssertEqual(result.zeroLoadRPM, 33.402, accuracy: 1e-12)
        XCTAssertTrue(result.isSignificant)
    }

    func testSynchronousMotorReportsNoCompensationNeeded() {
        // 同步交流馬達：加重量幾乎不影響穩態轉速，斜率落在雜訊內
        let result = LoadCompensator.extrapolate(rpmWithPhone: 33.3340,
                                                 rpmWithAddedMass: 33.3335,
                                                 addedMassGrams: 100.0,
                                                 phoneMassGrams: 170.0,
                                                 noiseRPM: 0.005)!
        XCTAssertFalse(result.isSignificant, "斜率在雜訊內時應回報不需要補償")
        XCTAssertEqual(result.zeroLoadRPM, 33.3349, accuracy: 1e-4)
    }

    func testRejectsInvalidInput() {
        XCTAssertNil(LoadCompensator.extrapolate(rpmWithPhone: 33.3, rpmWithAddedMass: 33.3,
                                                 addedMassGrams: 0, phoneMassGrams: 170))
        XCTAssertNil(LoadCompensator.extrapolate(rpmWithPhone: 33.3, rpmWithAddedMass: 33.3,
                                                 addedMassGrams: 100, phoneMassGrams: 0))
    }
}
