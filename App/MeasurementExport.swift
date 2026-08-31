import Foundation
import TurntableCore

/// 一次量測的完整原始記錄。
///
/// 存在的理由：截圖只看得到摘要數字，而摘要數字不足以診斷。實測遇到的狀況是
/// 「圓擬合出來的半徑/偏移」跟「min/max 推出來的半徑/偏移」互相矛盾 ——
/// 要判斷是哪裡出問題，必須把逐樣本的磁場向量整包拿出來自己畫圖。
///
/// 同時記錄兩種磁場來源：
/// - `field`：`CMDeviceMotion.magneticField`，CoreMotion 已經扣過偏置的版本。
/// - `rawField`：`CMMagnetometerData.magneticField`，完全沒動過的感測器讀數。
///
/// 兩者並排才能判斷偏置估計器有沒有在量測過程中改動讀數。
struct RawFrame {
    let t: TimeInterval
    let omega: Double
    let yaw: Double?
    let gravity: Vector3
    let field: Vector3?
    let rawField: Vector3?
}

enum MeasurementExport {

    /// 寫成 JSON。摘要放前面方便直接看，逐樣本資料用陣列的陣列，省掉重複的鍵名 ——
    /// 6800 筆樣本若每筆都帶鍵名會膨脹三倍以上。
    static func write(frames: [RawFrame],
                      summary: [String: Any],
                      to directory: URL) throws -> URL {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let name = "TurntableRPM-\(formatter.string(from: Date())).json"
        let url = directory.appendingPathComponent(name)

        var out = Data()
        func emit(_ s: String) { out.append(Data(s.utf8)) }

        let iso = ISO8601DateFormatter().string(from: Date())
        emit("{\n")
        emit("\"version\": 1,\n")
        emit("\"recordedAt\": \"\(iso)\",\n")

        let summaryData = try JSONSerialization.data(withJSONObject: summary,
                                                     options: [.sortedKeys, .prettyPrinted])
        emit("\"summary\": ")
        out.append(summaryData)
        emit(",\n")

        emit("\"columns\": [\"t\",\"omega\",\"yaw\",")
        emit("\"gx\",\"gy\",\"gz\",")
        emit("\"bx\",\"by\",\"bz\",")
        emit("\"rx\",\"ry\",\"rz\"],\n")

        // 時間戳改成相對於第一筆，數字短很多也比較好讀。
        let t0 = frames.first?.t ?? 0
        emit("\"samples\": [\n")
        for (i, f) in frames.enumerated() {
            func num(_ v: Double, _ digits: Int) -> String {
                String(format: "%.\(digits)f", v)
            }
            func vec(_ v: Vector3?, _ digits: Int) -> String {
                guard let v else { return "null,null,null" }
                return "\(num(v.x, digits)),\(num(v.y, digits)),\(num(v.z, digits))"
            }
            let yaw = f.yaw.map { num($0, 6) } ?? "null"
            emit("[\(num(f.t - t0, 5)),\(num(f.omega, 5)),\(yaw),")
            emit("\(vec(f.gravity, 5)),")
            emit("\(vec(f.field, 3)),")
            emit("\(vec(f.rawField, 3))]")
            emit(i == frames.count - 1 ? "\n" : ",\n")
        }
        emit("]\n}\n")

        try out.write(to: url, options: .atomic)
        return url
    }
}
