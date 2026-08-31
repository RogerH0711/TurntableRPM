import Foundation

/// 規格 §3.6：混合式相位。
///
/// 圈內用陀螺儀積分（解析度高、不受磁干擾），每偵測到磁北 yaw 走完一整圈就把相位重新錨定。
/// 純陀螺儀積分的相位會以 ε × N圈 × 360° 持續漂移：未校準（ε=1%）時 60 秒累積 120°，熱圖直接糊掉。
public struct PhaseIntegrator: Sendable {

    /// 陀螺儀積分的角度，每圈被磁北錨點重設一次。供熱圖定位用。
    public private(set) var angleDegrees: Double = 0
    /// 未經重設的陀螺儀積分總角度。
    public private(set) var gyroTotalDegrees: Double = 0
    /// 磁北參考量到的總轉角（絕對值）。這是不受陀螺儀比例因子影響的獨立量測。
    public private(set) var magneticTotalDegrees: Double = 0
    public private(set) var revolutions: Int = 0

    private var lastTime: TimeInterval?
    private var lastOmega: Double = 0
    private var unwrappedYaw: Double?
    private var lastYaw: Double?
    private var yawAtAnchor: Double = 0

    public init() {}

    public mutating func add(_ sample: SpinSample) {
        if let previousTime = lastTime {
            let step = (sample.t - previousTime) * (sample.omega + lastOmega) / 2.0
            angleDegrees += step
            gyroTotalDegrees += step
        }
        lastTime = sample.t
        lastOmega = sample.omega

        guard let yaw = sample.yaw else { return }

        if let previousYaw = lastYaw, var accumulated = unwrappedYaw {
            var delta = yaw - previousYaw
            while delta > Double.pi { delta -= 2 * Double.pi }
            while delta < -Double.pi { delta += 2 * Double.pi }
            accumulated += delta
            unwrappedYaw = accumulated
            magneticTotalDegrees = abs(accumulated) * 180.0 / Double.pi

            let travelled = accumulated - yawAtAnchor
            if abs(travelled) >= 2 * Double.pi {
                revolutions += 1
                // 錨點推進「剛好一整圈」，而且要依實際轉向推進。
                //
                // 這裡有兩個都真的踩過的坑：
                //
                // 1. 錨點若設成偵測到的實際位置而不是推進整整一圈，不足一個取樣的超調量會逐圈
                //    累積。45 轉、100 Hz 時每圈 133.33 個取樣，偵測落在第 134 個（361.8°），
                //    每圈多 1.8°，60 秒 44 圈就累積 79°。
                //
                // 2. 不能假設 yaw 是遞增的。唱盤從上方看是順時針轉，裝置姿態的 yaw 會遞減，
                //    accumulated 是負的。若無條件 += 2π，錨點會朝反方向跑掉，於是之後每一個
                //    取樣都滿足「走了超過一圈」——圈數變成幾乎等於取樣數，相位恆為 0。
                //    合成訊號原本只產生遞增的 yaw，所以 42 個測試全過卻在真機上炸掉。
                yawAtAnchor += travelled < 0 ? -2 * Double.pi : 2 * Double.pi
                angleDegrees = Double(revolutions) * 360.0
            }
        } else {
            unwrappedYaw = 0
            yawAtAnchor = 0
        }
        lastYaw = yaw
    }

    /// 目前在圈內的角度，0–360°。
    ///
    /// 殘餘誤差是「不足一個取樣的超調量」，33⅓ 轉時 < 2°、78 轉時 < 4.7°，都在一格熱圖（5°）
    /// 之內，而且不累積。刻意不用磁力計量到的超調量去補這一點：那會把每圈的 yaw 雜訊
    /// （實測環境常有 5°）灌進圈內相位，比殘餘誤差本身還大。
    public var phaseDegrees: Double {
        var angle = angleDegrees.truncatingRemainder(dividingBy: 360.0)
        if angle < 0 { angle += 360.0 }
        return angle
    }

    /// 校準倍率的即時估計：k = 磁北總轉角 ÷ 陀螺儀總轉角。
    ///
    /// **要跑滿數十圈才可信。** 單圈的時間尺度上，CMDeviceMotion 的姿態幾乎完全由陀螺儀積分
    /// 決定，磁力計的修正時間常數是好幾秒到數十秒；圈數夠多之後磁力計才會主導，這個比值
    /// 才代表真正的比例因子誤差（規格 §3.7 建議 90–120 秒）。
    public var calibrationEstimate: Double? {
        guard revolutions >= 1, gyroTotalDegrees > 0, magneticTotalDegrees > 0 else { return nil }
        return magneticTotalDegrees / gyroTotalDegrees
    }
}
