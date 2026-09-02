package com.roger.turntablerpm.export

import com.roger.turntablerpm.core.Vector3
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 匯出的格式是 `tools/analyze_export.py` 與 `ExportCrossCheck` 依賴的契約，
 * 而且修剪那一段**會刪檔案**。兩件事都值得有測試守著。
 */
class MeasurementExportTest {

    @get:Rule val temp = TemporaryFolder()

    private fun frames(n: Int) = (0 until n).map {
        RawFrame(t = 100.0 + it * 0.01, omega = 200.0 + it, gravity = Vector3(0.0, 0.0, 9.8))
    }

    @Test
    fun `沒有樣本就不產生檔案`() {
        assertNull(MeasurementExport.write(emptyList(), emptyMap(), temp.root))
        assertEquals(0, temp.root.listFiles()!!.size)
    }

    @Test
    fun `欄位順序與 iOS 一致`() {
        val f = MeasurementExport.write(frames(3), emptyMap(), temp.root)!!
        val text = f.readText()
        assertTrue(
            text.contains(
                """"columns": ["t","omega","yaw","gx","gy","gz","bx","by","bz","rx","ry","rz"]""",
            ),
        )
    }

    @Test
    fun `時間戳改成相對於第一筆`() {
        // 感測器時間戳的絕對值沒有意義（開機以來的奈秒），相對值才好讀。
        val f = MeasurementExport.write(frames(3), emptyMap(), temp.root)!!
        val first = f.readText().substringAfter("\"samples\": [\n").substringBefore(",")
        assertEquals("[0.00000", first)
    }

    @Test
    fun `Android 沒有磁力計所以磁場欄位是 null`() {
        val f = MeasurementExport.write(frames(2), emptyMap(), temp.root)!!
        // 非最後一筆的樣本行結尾帶逗號，比對前先去掉。
        val line = f.readText().lines().first { it.startsWith("[0.00000") }.trimEnd(',')
        assertTrue(line.endsWith("null,null,null,null,null,null]"))
        // yaw 一個，已校準磁場三個，未校準磁力計三個。
        assertEquals(7, Regex("null").findAll(line).count())
    }

    @Test
    fun `NaN 不會寫出非法 JSON`() {
        // 退化的量測（例如所有樣本都一樣）真的會產生 NaN，而 NaN 不是合法 JSON。
        // 匯出失敗的時機正好是最需要那份資料的時候。
        val f = MeasurementExport.write(
            frames(2),
            mapOf("ratio" to Double.NaN, "inf" to Double.POSITIVE_INFINITY, "ok" to 1.5),
            temp.root,
        )!!
        val text = f.readText()
        assertFalse(text.contains("NaN"))
        assertFalse(text.contains("Infinity"))
        assertTrue(text.contains("\"ratio\":null"))
        assertTrue(text.contains("\"ok\":1.500000"))
    }

    @Test
    fun `摘要的巢狀結構也寫得出來`() {
        val f = MeasurementExport.write(
            frames(2),
            mapOf(
                "peaks" to listOf(mapOf("hz" to 0.55, "harmonic" to true)),
                "name" to "Sony G8142",
                "missing" to null,
            ),
            temp.root,
        )!!
        val text = f.readText()
        assertTrue(text.contains(""""peaks":[{"hz":0.550000,"harmonic":true}]"""))
        assertTrue(text.contains(""""name":"Sony G8142""""))
        assertTrue(text.contains(""""missing":null"""))
    }

    @Test
    fun `超過上限時刪掉最舊的`() {
        // 檔名帶 yyyyMMdd-HHmmss，字典序就是時間序。
        repeat(25) { File(temp.root, "TurntableRPM-20250101-%06d.json".format(it)).writeText("x") }
        MeasurementExport.write(frames(2), emptyMap(), temp.root)
        val names = temp.root.listFiles()!!.map { it.name }.sorted()
        assertEquals(20, names.size)
        assertFalse(names.contains("TurntableRPM-20250101-000000.json"))
        assertTrue(names.contains("TurntableRPM-20250101-000024.json"))
    }

    @Test
    fun `不是匯出檔的東西不會被刪掉`() {
        repeat(25) { File(temp.root, "TurntableRPM-20250101-%06d.json".format(it)).writeText("x") }
        val bystander = File(temp.root, "notes.txt").apply { writeText("keep me") }
        MeasurementExport.write(frames(2), emptyMap(), temp.root)
        assertTrue(bystander.exists())
    }

    @Test
    fun `檔名帶時間戳`() {
        val f = MeasurementExport.write(frames(2), emptyMap(), temp.root)
        assertNotNull(f)
        assertTrue(f!!.name.matches(Regex("""TurntableRPM-\d{8}-\d{6}\.json""")))
    }
}
