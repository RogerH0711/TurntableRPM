package com.roger.turntablerpm.export

import com.roger.turntablerpm.core.Vector3
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 一筆原始記錄。Android 沒有走磁力計路徑，所以只有時間、角速度與重力。 */
data class RawFrame(val t: Double, val omega: Double, val gravity: Vector3)

/**
 * 把一次量測的逐樣本資料寫成 JSON。
 *
 * **存在的理由是「摘要數字診斷不出問題」。** iOS 端靠這個檔案才查得出磁場的
 * 空間失真；Android 端靠它才查得出取樣率為什麼是 107.9 Hz 而不是要求的 100 Hz。
 * 兩者都不是看畫面上的數字看得出來的。
 *
 * **格式跟 iOS 完全一致，包括那些 Android 填不了的欄位。** 這樣
 * `tools/analyze_export.py` 與 `ExportCrossCheck` 不用改就能吃兩邊的檔案 ——
 * 為了少寫幾個 null 而讓兩個平台的檔案格式分岔，代價是每個離線工具都要分兩套。
 *
 * 磁場欄位（bx/by/bz、rx/ry/rz）一律是 null：Android 版沒有註冊磁力計。
 * 那條路在 iOS 上已經證明走不通（坑 15），不值得為了填滿欄位再開一顆感測器。
 */
object MeasurementExport {

    fun write(frames: List<RawFrame>, summary: Map<String, Any?>, directory: File): File? {
        if (frames.isEmpty()) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "TurntableRPM-$stamp.json")

        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        file.bufferedWriter().use { w ->
            w.write("{\n")
            w.write("\"version\": 1,\n")
            w.write("\"recordedAt\": \"$iso\",\n")
            w.write("\"platform\": \"android\",\n")
            w.write("\"summary\": ")
            w.write(jsonObject(summary))
            w.write(",\n")
            // 逐樣本用陣列的陣列，省掉重複的鍵名 —— 兩萬筆樣本若每筆都帶鍵名會膨脹三倍。
            w.write("\"columns\": [\"t\",\"omega\",\"yaw\",")
            w.write("\"gx\",\"gy\",\"gz\",")
            w.write("\"bx\",\"by\",\"bz\",")
            w.write("\"rx\",\"ry\",\"rz\"],\n")
            w.write("\"samples\": [\n")
            // 時間戳改成相對於第一筆，數字短很多也比較好讀。
            val t0 = frames.first().t
            for ((i, f) in frames.withIndex()) {
                w.write("[${num(f.t - t0, 5)},${num(f.omega, 5)},null,")
                w.write("${num(f.gravity.x, 5)},${num(f.gravity.y, 5)},${num(f.gravity.z, 5)},")
                w.write("null,null,null,null,null,null]")
                w.write(if (i == frames.size - 1) "\n" else ",\n")
            }
            w.write("]\n}\n")
        }
        prune(directory)
        return file
    }

    /**
     * 只留最新的 [KEEP] 份。
     *
     * 一次 3 分鐘的量測大約 1.5 MB，而使用者不會想到要去刪 —— 這個目錄在
     * app 專屬空間裡，檔案管理員也不一定看得到。沒有上限就是無聲地長大。
     * 想留久一點的檔案自己分享出去，那本來就是這個功能的用途。
     */
    private fun prune(directory: File, keep: Int = KEEP) {
        val files = directory.listFiles { f -> f.isFile && f.name.startsWith("TurntableRPM-") }
            ?: return
        if (files.size <= keep) return
        // 檔名帶 yyyyMMdd-HHmmss，字典序就是時間序。
        files.sortedBy { it.name }.dropLast(keep).forEach { it.delete() }
    }

    private const val KEEP = 20

    private fun num(v: Double, digits: Int): String =
        if (v.isFinite()) "%.${digits}f".format(Locale.US, v) else "null"

    /**
     * 手寫 JSON 而不用 org.json：`JSONObject.put` 遇到 NaN／Infinity 會丟例外，
     * 而摘要裡的比值在退化的量測（例如全部樣本都一樣）確實會是 NaN。
     * 匯出失敗的時機正好是最需要那份資料的時候。
     */
    private fun jsonObject(map: Map<String, Any?>): String =
        map.entries.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:${jsonValue(v)}" }

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Double -> if (v.isFinite()) "%.6f".format(Locale.US, v) else "null"
        is Float -> jsonValue(v.toDouble())
        is Int, is Long -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) ->
            "${quote(k.toString())}:${jsonValue(x)}"
        }
        is List<*> -> v.joinToString(",", "[", "]") { jsonValue(it) }
        else -> quote(v.toString())
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
}
