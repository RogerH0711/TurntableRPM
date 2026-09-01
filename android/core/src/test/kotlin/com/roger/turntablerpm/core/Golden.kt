package com.roger.turntablerpm.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.fail

/**
 * 黃金值來自 `Packages/TurntableCore/Reference/golden.json`，由獨立的 Python 參考實作產生
 * （`make reference`），**不是任何一邊自己的輸出**。
 *
 * 這裡是直接讀那個檔。Swift 那邊目前是把數值抄進原始碼裡，所以 Kotlin 這條路
 * 反而更嚴謹 —— 改了 Python 而忘記重跑，這邊會立刻紅。
 */
object Golden {

    private val root: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "Packages/TurntableCore/Reference/golden.json").isFile) return@lazy dir
            dir = dir.parentFile
        }
        fail("往上找不到 Packages/TurntableCore/Reference/golden.json（從 ${File("").absolutePath} 開始）")
    }

    val json: JsonObject by lazy {
        val f = File(root, "Packages/TurntableCore/Reference/golden.json")
        Json.parseToJsonElement(f.readText()) as JsonObject
    }

    /** 取一個巢狀的數值，例如 `number("projection_naive_z_error_pct", "5")`。 */
    fun number(vararg path: String): Double {
        var node: Any = json
        for (key in path) {
            node = (node as? JsonObject)?.get(key)
                ?: fail("golden.json 裡沒有 ${path.joinToString(".")}")
        }
        return (node as kotlinx.serialization.json.JsonElement).jsonPrimitive.content.toDouble()
    }
}
