import Foundation

/// 標稱轉速。換算關係：RPM × 6 = °/s。
public struct TurntableSpeed: Equatable, Sendable {
    public let rpm: Double
    public let label: String

    public init(rpm: Double, label: String) {
        self.rpm = rpm
        self.label = label
    }

    public static let rpm16 = TurntableSpeed(rpm: 50.0 / 3.0, label: "16\u{2154}")
    public static let rpm33 = TurntableSpeed(rpm: 100.0 / 3.0, label: "33\u{2153}")
    public static let rpm45 = TurntableSpeed(rpm: 45.0, label: "45")
    public static let rpm78 = TurntableSpeed(rpm: 78.0, label: "78")

    public static let standard: [TurntableSpeed] = [.rpm16, .rpm33, .rpm45, .rpm78]

    public var degreesPerSecond: Double { rpm * 6.0 }
    public var secondsPerRevolution: Double { 60.0 / rpm }
}
