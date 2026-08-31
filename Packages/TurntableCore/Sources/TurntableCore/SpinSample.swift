import Foundation

/// 一筆已處理過的樣本。
/// - `t`: CMLogItem.timestamp（開機以來的秒數），務必用真實時間戳，不要假設等間隔。
/// - `omega`: 已投影到自轉軸的角速度，單位 °/s。
/// - `yaw`: 磁北參考的偏航角，單位弧度；裝置不支援時為 nil。
public struct SpinSample: Sendable {
    public let t: TimeInterval
    public let omega: Double
    public let yaw: Double?

    public init(t: TimeInterval, omega: Double, yaw: Double? = nil) {
        self.t = t
        self.omega = omega
        self.yaw = yaw
    }
}
