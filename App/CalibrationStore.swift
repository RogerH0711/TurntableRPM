import Foundation
import TurntableCore

/// 碼錶校準的存放處。
///
/// k 是綁在**這一支**陀螺儀上的固定性質，所以連機型一起存。
/// 從備份還原到新手機時 `UserDefaults` 會跟著搬過去，但那支陀螺儀的 k 不一樣 ——
/// 機型對不上就必須失效，不能默默套用，否則之後每一次讀數都是錯的而且看不出來。
@MainActor
final class CalibrationStore: ObservableObject {

    /// 目前生效的校準。機型對不上時為 nil。
    @Published private(set) var calibration: StopwatchCalibration?
    /// 存著但因為機型不符而被停用的那一筆，用來在畫面上解釋為什麼校準不見了。
    @Published private(set) var mismatched: StopwatchCalibration?

    private let key = "stopwatchCalibration"
    private let defaults: UserDefaults

    /// 這台裝置的機型識別字串，例如 `iPhone16,2`。
    static let deviceModel: String = {
        var info = utsname()
        uname(&info)
        let machine = withUnsafePointer(to: &info.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) }
        }
        return machine.isEmpty ? "unknown" : machine
    }()

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    private func load() {
        guard let data = defaults.data(forKey: key),
              let stored = try? JSONDecoder().decode(StopwatchCalibration.self, from: data)
        else { return }

        if stored.deviceModel == Self.deviceModel {
            calibration = stored
            mismatched = nil
        } else {
            calibration = nil
            mismatched = stored
        }
    }

    /// 存下新的校準。不合理的 k 直接拒絕 —— 幾乎一定是輸入打錯。
    @discardableResult
    func save(_ c: StopwatchCalibration) -> Bool {
        guard c.isPlausible else { return false }
        guard let data = try? JSONEncoder().encode(c) else { return false }
        defaults.set(data, forKey: key)
        calibration = c
        mismatched = nil
        return true
    }

    func clear() {
        defaults.removeObject(forKey: key)
        calibration = nil
        mismatched = nil
    }

    /// 套用到未修正的轉速。沒有校準時原樣回傳。
    func apply(to rpm: Double) -> Double {
        calibration.map { $0.apply(to: rpm) } ?? rpm
    }
}
