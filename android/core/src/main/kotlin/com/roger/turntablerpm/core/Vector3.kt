package com.roger.turntablerpm.core

import kotlin.math.sqrt

/** 三維向量。對應 Android SensorEvent 的三軸值。 */
data class Vector3(val x: Double, val y: Double, val z: Double) {

    fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

    val magnitude: Double get() = sqrt(x * x + y * y + z * z)

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    /** 單位化。長度為零時回 null，讓呼叫端自己決定怎麼處理退化情況。 */
    val normalized: Vector3?
        get() {
            val m = magnitude
            return if (m > 1e-12) Vector3(x / m, y / m, z / m) else null
        }
}
