package com.roger.turntablerpm.core

import kotlin.math.pow

/**
 * 規格 §3.4：IEC 386 / DIN 45507 加權曲線的閉式近似（三極高通 × 三極低通）。
 *
 * 對 AES 公布的四個錨點做最小平方擬合，峰值落在 3.968 Hz，錨點最大誤差 3.5%：
 * 0.2 Hz→0.0292（標準 0.0296）、0.8 Hz→0.5176（0.500）、4 Hz→1.000、20 Hz→0.5081（0.508）。
 */
object WowFlutterWeighting {
    const val HIGH_PASS_CORNER = 0.635    // Hz
    const val LOW_PASS_CORNER = 24.8      // Hz

    /** 未正規化曲線的峰值，出現在 3.9683751 Hz。 */
    const val PEAK_VALUE = 0.926957486998

    fun weight(frequency: Double): Double {
        if (frequency <= 0) return 0.0
        val a = frequency / HIGH_PASS_CORNER
        val b = frequency / LOW_PASS_CORNER
        val highPass = (a * a * a) / (1.0 + a * a).pow(1.5)
        val lowPass = 1.0 / (1.0 + b * b).pow(1.5)
        return highPass * lowPass / PEAK_VALUE
    }
}
