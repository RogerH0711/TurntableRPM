package com.roger.turntablerpm.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 移動平均**只能用在顯示路徑**。N=25 在 100 Hz 下的第一個零點正好是 4 Hz ——
 * 加權曲線的峰值 —— 用在抖晃率計算上會把要量的東西整個挖掉。
 */
class MovingAverageTest {

    @Test
    fun `零點與截止頻率符合黃金值`() {
        val fs = 100.0
        for ((key, obj) in Golden.json["moving_average"]!!.let {
            (it as kotlinx.serialization.json.JsonObject).entries
        }) {
            val window = key.toInt()
            val expectedNull = Golden.number(obj as kotlinx.serialization.json.JsonObject, "null_hz")
            val expected3dB = Golden.number(obj, "minus3db_hz")
            val gotNull = MovingAverage.firstNullFrequency(window, fs)
            val got3dB = MovingAverage.cutoffFrequency(window, fs)
            assertTrue(abs(gotNull - expectedNull) < 1e-3, "N=$window 零點：期望 $expectedNull，實得 $gotNull")
            assertTrue(abs(got3dB - expected3dB) < 1e-3, "N=$window −3dB：期望 $expected3dB，實得 $got3dB")
        }
    }

    /** 這是「抖晃率不能算在平滑訊號上」的直接證據。 */
    @Test
    fun `N 等於 25 時 4 Hz 被完全挖掉`() {
        val response = MovingAverage.magnitudeResponse(frequency = 4.0, window = 25, sampleRate = 100.0)
        assertTrue(response < 1e-12, "4 Hz 的響應應該是零點，實得 $response")
    }

    @Test
    fun `直流成分不受影響`() {
        val x = DoubleArray(100) { 5.0 }
        val out = MovingAverage.apply(x, 25)
        for (v in out) assertTrue(abs(v - 5.0) < 1e-12)
    }
}
