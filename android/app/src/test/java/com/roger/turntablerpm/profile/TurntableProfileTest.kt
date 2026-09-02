package com.roger.turntablerpm.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 傳動比是「這根峰是不是你的馬達」的判準，公式錯了整個判讀就錯了。
 * 純 Kotlin，不需要 Android 環境。
 */
class TurntableProfileTest {

    private fun profile(
        pulley: Double? = null,
        platter: Double? = null,
        belt: Double? = null,
    ) = TurntableProfile(
        id = 1,
        pulleyDiameterMM = pulley,
        platterDiameterMM = platter,
        beltThicknessMM = belt,
    )

    @Test
    fun `沒有尺寸就沒有傳動比`() {
        assertNull(profile().expectedDriveRatio)
        assertNull(profile(pulley = 8.5).expectedDriveRatio)
        assertNull(profile(platter = 300.0).expectedDriveRatio)
    }

    @Test
    fun `沒填皮帶厚度時退化成直徑比`() {
        val r = profile(pulley = 10.0, platter = 300.0).expectedDriveRatio!!
        assertEquals(30.0, r, 1e-9)
    }

    @Test
    fun `皮帶厚度加在兩邊的有效直徑上`() {
        // (300 + 0.5) / (8.5 + 0.5) = 33.389
        val r = profile(pulley = 8.5, platter = 300.0, belt = 0.5).expectedDriveRatio!!
        assertEquals(33.3889, r, 1e-4)
    }

    @Test
    fun `皮帶輪很小時厚度的影響很大`() {
        // 這是把「符合／不符合」的二分判定拿掉的理由（CLAUDE.md 坑 27）：
        // 光是 0.5 mm 的厚度就讓比值差 5.4%，使用者量差一點判讀就整個消失。
        // （CLAUDE.md 記的 5.6% 是 D→∞ 的漸近值 8.5/9；300 mm 的盤面是 5.40%。）
        val without = profile(pulley = 8.5, platter = 300.0).expectedDriveRatio!!
        val with = profile(pulley = 8.5, platter = 300.0, belt = 0.5).expectedDriveRatio!!
        assertEquals(-5.40, (with / without - 1) * 100, 0.01)
    }

    @Test
    fun `零或負的尺寸不算數`() {
        assertNull(profile(pulley = 0.0, platter = 300.0).expectedDriveRatio)
        assertNull(profile(pulley = 8.5, platter = 0.0).expectedDriveRatio)
    }

    @Test
    fun `顯示名稱在完全空白時有預設值`() {
        assertEquals("未命名唱盤", TurntableProfile(id = 1).displayName)
        assertEquals("Thorens TD 235 EV", TurntableProfile(1, "TD 235 EV", "Thorens").displayName)
        assertEquals("TD 235 EV", TurntableProfile(1, name = "TD 235 EV").displayName)
    }
}
