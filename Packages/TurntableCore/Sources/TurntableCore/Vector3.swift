import Foundation

/// 三維向量。對應 CoreMotion 的 CMRotationRate / CMAcceleration。
public struct Vector3: Equatable, Sendable {
    public var x: Double
    public var y: Double
    public var z: Double

    public init(_ x: Double, _ y: Double, _ z: Double) {
        self.x = x
        self.y = y
        self.z = z
    }

    public func dot(_ other: Vector3) -> Double {
        x * other.x + y * other.y + z * other.z
    }

    public var magnitude: Double {
        (x * x + y * y + z * z).squareRoot()
    }

    public func cross(_ other: Vector3) -> Vector3 {
        Vector3(y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x)
    }

    /// 單位化。長度為零時回 nil，讓呼叫端自己決定怎麼處理退化情況。
    public var normalized: Vector3? {
        let m = magnitude
        guard m > 1e-12 else { return nil }
        return Vector3(x / m, y / m, z / m)
    }
}
