package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs

/**
 * 規格 §2.2：把三軸角速度投影到重力方向，取出轉盤的自轉分量。
 *
 * 直接讀陀螺儀 z 軸會有 cos 誤差：手機或盤面傾斜 5° 就低估 0.38%，
 * 已經超過 0.1% 的目標精度。投影法讓手機怎麼擺都不影響讀數。
 */
object SpinProjector {

    /**
     * @param rotationRate TYPE_GYROSCOPE，rad/s，裝置座標系。
     * @param gravity      TYPE_GRAVITY，m/s²（或單位 g，只用方向所以不影響）。
     * @return 自轉角速度，°/s，恆為非負。
     */
    fun project(rotationRate: Vector3, gravity: Vector3): Double {
        val gm = gravity.magnitude
        if (gm <= 1e-9) return 0.0
        val radiansPerSecond = -rotationRate.dot(gravity) / gm
        return abs(radiansPerSecond) * 180.0 / PI
    }

    /** 只讀 z 軸的做法，保留下來供測試對照，正式路徑請勿使用。 */
    fun projectNaiveZ(rotationRate: Vector3): Double = abs(rotationRate.z) * 180.0 / PI
}
