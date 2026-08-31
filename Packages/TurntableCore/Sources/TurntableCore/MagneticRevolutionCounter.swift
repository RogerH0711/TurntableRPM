import Foundation

/// 完全不經過陀螺儀的圈數計 —— 直接數地磁向量在裝置座標系裡轉了幾圈。
///
/// **為什麼需要這個。** `CMDeviceMotion.attitude.yaw` 是融合出來的，盤面連續高速轉動時
/// CoreMotion 會把磁力計降權，yaw 退化成純陀螺儀積分（見 `CalibrationConfidence`）。
/// 拿它去校準陀螺儀是同義反覆 —— 不管陀螺儀準不準都會吐出 k ≈ 1。
///
/// 這裡繞過融合器，改吃 `CMDeviceMotion.magneticField.field` 的原始向量：
/// 手機在盤上轉一圈，地磁的水平分量在裝置座標系裡就恰好掃過 360°。
/// 這個角度只由「手機相對房間轉了多少」決定，**跟陀螺儀的比例因子完全無關**。
///
/// **基底固定在第一筆樣本。** 盤面若沒完全水平，逐樣本重算基底會把盤面的章動
/// 灌進角度。固定基底造成的是每圈一次的失真，在整數圈上相減會抵消 ——
/// 這也是 `ScaleCalibrator` 把時間窗切在整數圈的理由。
///
/// **注意這條路徑量的是「手機相對房間」的轉動。** 房間裡的固定磁源（喇叭磁鐵、
/// 音響變壓器、鋼筋）會造成每圈一次的角度失真，同樣靠整數圈抵消；
/// 但會隨時間變動的磁場（旁邊有人走動、馬達啟停）沒有辦法，只能拉長量測時間平均掉。
public struct MagneticRevolutionCounter: Sendable {

    /// 地磁水平分量累積轉過的角度（絕對值，度）。
    public private(set) var totalDegrees: Double = 0
    /// 完整圈數。
    public private(set) var revolutions: Int = 0
    /// 實際用到的樣本數。水平分量太小的樣本會被丟掉。
    public private(set) var sampleCount: Int = 0
    /// 最新一筆的水平分量量值（µT）。
    public private(set) var horizontalMagnitude: Double = 0
    /// 整段量測中水平分量的最小值與最大值。**這兩個數字能完整診斷這條路徑。**
    public private(set) var minHorizontal: Double = .infinity
    public private(set) var maxHorizontal: Double = 0

    private var e1 = Vector3(0, 0, 0)
    private var e2 = Vector3(0, 0, 0)
    private var hasBasis = false

    private var accumulated: Double = 0
    private var lastAngle: Double?

    /// 水平面上的投影點，留著給 `refined()` 擬合圓心。
    /// 100 Hz × 10 分鐘 = 60000 點，約 1 MB，可以接受。
    private var points: [(x: Double, y: Double)] = []
    private let maxPoints = 100 * 600

    public init() {}

    public mutating func add(field: Vector3, gravity: Vector3) {
        if !hasBasis {
            guard let basis = Self.makeBasis(gravity: gravity) else { return }
            e1 = basis.e1
            e2 = basis.e2
            hasBasis = true
        }

        // 先用「當下這一筆」的重力把垂直分量扣掉，再投影到固定基底。
        // 少了這一步，強磁場的垂直分量會隨盤面章動洩漏進來（見型別說明）。
        guard let down = gravity.normalized else { return }
        let vertical = field.dot(down)
        let horizontal = Vector3(field.x - vertical * down.x,
                                 field.y - vertical * down.y,
                                 field.z - vertical * down.z)
        let x = horizontal.dot(e1)
        let y = horizontal.dot(e2)
        let h = (x * x + y * y).squareRoot()
        guard h > 1e-9 else { return }

        horizontalMagnitude = h
        minHorizontal = Swift.min(minHorizontal, h)
        maxHorizontal = Swift.max(maxHorizontal, h)
        sampleCount += 1

        if points.count < maxPoints { points.append((x, y)) }

        let angle = atan2(y, x)
        if let previous = lastAngle {
            var delta = angle - previous
            while delta > Double.pi { delta -= 2 * Double.pi }
            while delta < -Double.pi { delta += 2 * Double.pi }
            accumulated += delta
            totalDegrees = abs(accumulated) * 180.0 / Double.pi
            revolutions = Int(totalDegrees / 360.0)
        }
        lastAngle = angle
    }

    /// 把水平分量的 min/max 拆成「地磁圓的半徑」與「圓心偏移」。
    ///
    /// 地磁在裝置座標系裡畫一個圓：**半徑 R** 是地磁的水平分量，**圓心偏移 d** 是
    /// 跟著手機一起轉的本地磁場（磁吸殼、磁化的盤面、唱片鎮）。量到的水平分量在
    /// |d − R| 與 d + R 之間擺盪，所以：
    ///
    ///     (max + min) / 2 = max(R, d)
    ///     (max − min) / 2 = min(R, d)
    ///
    /// 誰是誰由「有沒有繞圈」決定 —— 圓包住原點才繞得起來，也就是 R > d：
    ///
    /// - **會繞圈**（`revolutions` 正常增加）→ 半徑是大的那個，這條路徑健康。
    /// - **繞不起來** → 偏移是大的那個，本地磁場蓋過地磁，得先把磁鐵找出來拿掉。
    /// - **max ≈ min 且會繞圈** → 完全沒有本地磁場，最理想的情況。
    /// - **max ≈ min 但繞不起來** → 水平分量根本沒變化，代表地磁訊號本身就被抵消了，
    ///   那就不是磁鐵的問題，而是這個資料來源不能用。
    public var horizontalRange: (larger: Double, smaller: Double)? {
        guard sampleCount >= 2, maxHorizontal > 0, minHorizontal.isFinite else { return nil }
        return ((maxHorizontal + minHorizontal) / 2, (maxHorizontal - minHorizontal) / 2)
    }

    /// 扣掉圓心偏移之後重新解捲的結果。
    ///
    /// **這是把「繞不起來」救回來的關鍵。** 圓心偏移是裝置座標系裡的固定向量
    /// （手機自帶的磁鐵、跟著轉的磁化物），地磁繞著它畫圓。只要盤面轉過幾圈，
    /// 資料就已經把整個圓掃過很多遍，圓心可以直接擬合出來再減掉 ——
    /// 減完之後圓心回到原點，d = 0，必然繞得起來。
    ///
    /// 這就是硬鐵校準，只是離線做。`residual` 是擬合殘差：
    /// 值遠小於 `radius` 代表資料確實是個圓、這次擬合可信；
    /// 兩者同一量級代表偏移在量測過程中有變動（有人走過、旁邊的馬達啟停），
    /// 這時結果不能用。
    public struct Refined: Sendable {
        public let totalDegrees: Double
        public let revolutions: Int
        /// 擬合出來的圓心偏移量值（µT）—— 跟著手機一起轉的本地磁場。
        public let centerOffset: Double
        /// 擬合出來的圓半徑（µT）—— 真正的地磁水平分量。
        public let radius: Double
        /// 平均擬合殘差（µT）。
        public let residual: Double

        /// 擬合可不可信。殘差要遠小於半徑，而且半徑不能小到跟雜訊同級。
        ///
        /// 門檻原本設 0.25，實測踩到：殘差 4.71 µT／半徑 24.9 µT = 19% 被判成可信，
        /// 但那組資料的圓擬合結果跟 min/max 推出來的完全對不上，根本不是個圓。
        /// 乾淨的圓殘差應該在 1% 以下，5% 已經很寬鬆了。
        public var isTrustworthy: Bool { radius > 1.0 && residual < radius * 0.05 }
    }

    public func refined() -> Refined? {
        guard points.count >= 32, let fit = Self.fitCircle(points) else { return nil }

        // 扣掉圓心之後重新解捲。
        var accumulated = 0.0
        var previous: Double?
        for p in points {
            let angle = atan2(p.y - fit.b, p.x - fit.a)
            if let q = previous {
                var delta = angle - q
                while delta > Double.pi { delta -= 2 * Double.pi }
                while delta < -Double.pi { delta += 2 * Double.pi }
                accumulated += delta
            }
            previous = angle
        }

        var residual = 0.0
        for p in points {
            let dx = p.x - fit.a, dy = p.y - fit.b
            residual += abs((dx * dx + dy * dy).squareRoot() - fit.r)
        }
        residual /= Double(points.count)

        let total = abs(accumulated) * 180.0 / Double.pi
        return Refined(totalDegrees: total,
                       revolutions: Int(total / 360.0),
                       centerOffset: (fit.a * fit.a + fit.b * fit.b).squareRoot(),
                       radius: fit.r,
                       residual: residual)
    }

    /// Kåsa 代數圓擬合。把 x²+y² = 2ax + 2by + c 當成對 (a, b, c) 的線性最小平方，
    /// 一次解出來，不需要疊代也不會有收斂問題。
    static func fitCircle(_ points: [(x: Double, y: Double)]) -> (a: Double, b: Double, r: Double)? {
        let n = Double(points.count)
        guard n >= 3 else { return nil }

        var Sx = 0.0, Sy = 0.0, Sxx = 0.0, Syy = 0.0, Sxy = 0.0
        var Sxz = 0.0, Syz = 0.0, Sz = 0.0
        for p in points {
            let z = p.x * p.x + p.y * p.y
            Sx += p.x;  Sy += p.y;  Sz += z
            Sxx += p.x * p.x;  Syy += p.y * p.y;  Sxy += p.x * p.y
            Sxz += p.x * z;    Syz += p.y * z
        }

        // [2Sxx 2Sxy Sx] [a]   [Sxz]
        // [2Sxy 2Syy Sy] [b] = [Syz]
        // [2Sx  2Sy  n ] [c]   [Sz ]
        let m = [[2 * Sxx, 2 * Sxy, Sx],
                 [2 * Sxy, 2 * Syy, Sy],
                 [2 * Sx,  2 * Sy,  n]]
        let rhs = [Sxz, Syz, Sz]

        func det3(_ a: [[Double]]) -> Double {
            a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1])
          - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
          + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
        }
        let base = det3(m)
        guard abs(base) > 1e-12 else { return nil }

        func solve(_ column: Int) -> Double {
            var c = m
            for row in 0 ..< 3 { c[row][column] = rhs[row] }
            return det3(c) / base
        }
        let a = solve(0), b = solve(1), c = solve(2)

        let rSquared = c + a * a + b * b
        guard rSquared > 0 else { return nil }
        return (a, b, rSquared.squareRoot())
    }

    /// k = 地磁總轉角 ÷ 陀螺儀總轉角。**這個比值才是真正獨立的。**
    ///
    /// 跟 `PhaseIntegrator.calibrationEstimate` 用同樣的定義，好讓兩者可以並排比對：
    /// 兩個數字若一起貼在 1.0，代表這條路徑也被磁干擾吃掉了；
    /// 若這個偏離 1.0 而另一個沒有，就證明融合器確實是元兇。
    public func calibrationFactor(gyroTotalDegrees: Double) -> Double? {
        guard revolutions >= 1, gyroTotalDegrees > 0, totalDegrees > 0 else { return nil }
        return totalDegrees / gyroTotalDegrees
    }

    /// 從重力方向建出水平面的正交基底。
    static func makeBasis(gravity: Vector3) -> (e1: Vector3, e2: Vector3)? {
        guard let axis = gravity.normalized else { return nil }

        // 挑一個跟 axis 最不平行的座標軸當種子，避免外積退化。
        let seed: Vector3
        if abs(axis.x) <= abs(axis.y) && abs(axis.x) <= abs(axis.z) {
            seed = Vector3(1, 0, 0)
        } else if abs(axis.y) <= abs(axis.z) {
            seed = Vector3(0, 1, 0)
        } else {
            seed = Vector3(0, 0, 1)
        }

        guard let e1 = seed.cross(axis).normalized else { return nil }
        return (e1, axis.cross(e1))
    }
}
