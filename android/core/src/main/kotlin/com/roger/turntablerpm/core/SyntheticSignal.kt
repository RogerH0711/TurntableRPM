package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** 一個抖晃成分。 */
data class WowComponent(
    val amplitudePercent: Double,
    val frequencyHz: Double,
    val phaseRadians: Double = 0.0,
)

/** 合成訊號的輸出。 */
data class SyntheticRun(
    val samples: List<SpinSample>,
    val trueOmega: DoubleArray,
    val trueAngleDegrees: DoubleArray,
    val rotationRates: List<Vector3>,
    val gravities: List<Vector3>,
)

/**
 * 可重現的合成訊號，測試用。與 Swift 版及 Reference/reference.py 對齊。
 *
 * `reversedYaw` 產生**遞減**的 yaw，模擬從上方看順時針旋轉的唱盤 —— 真實唱盤就是這個方向。
 * 凡是會累積或比較 yaw 的東西，兩個方向都要測。
 */
object SyntheticSignal {

    fun make(
        nominalRPM: Double,
        durationSeconds: Double,
        sampleRate: Double = 100.0,
        wow: List<WowComponent> = emptyList(),
        noisePercent: Double = 0.0,
        scaleError: Double = 0.0,
        tiltDegrees: Double = 0.0,
        yawNoiseDegrees: Double = 0.0,
        reversedYaw: Boolean = false,
        seed: Long = 1L,
    ): SyntheticRun {
        val rng = SplitMix64(seed)
        val count = maxOf(2, (durationSeconds * sampleRate).toInt())
        val omega0 = nominalRPM * 6.0                    // °/s
        val tilt = tiltDegrees * PI / 180.0
        // 自轉軸沿重力；手機傾斜 tiltDegrees
        val gravity = Vector3(sin(tilt), 0.0, -cos(tilt))
        val yawSign = if (reversedYaw) -1.0 else 1.0

        val trueOmega = DoubleArray(count)
        val angle = DoubleArray(count)
        val rotationRates = ArrayList<Vector3>(count)
        val gravities = ArrayList<Vector3>(count)
        val samples = ArrayList<SpinSample>(count)

        for (i in 0 until count) {
            val t = i / sampleRate
            var dev = 0.0
            for (w in wow) {
                dev += (w.amplitudePercent / 100.0) * sin(2.0 * PI * w.frequencyHz * t + w.phaseRadians)
            }
            if (noisePercent != 0.0) dev += rng.nextGaussian() * (noisePercent / 100.0)
            trueOmega[i] = omega0 * (1.0 + dev)
        }
        // 梯形積分出真實轉角
        for (i in 1 until count) {
            val dt = 1.0 / sampleRate
            angle[i] = angle[i - 1] + dt * (trueOmega[i - 1] + trueOmega[i]) / 2.0
        }
        for (i in 0 until count) {
            val t = i / sampleRate
            val omegaMeasured = trueOmega[i] * (1.0 + scaleError)   // 陀螺儀量到的
            val radPerSec = omegaMeasured * PI / 180.0
            // 角速度向量 = 量到的大小 × (−重力方向)
            val rot = Vector3(-gravity.x * radPerSec, -gravity.y * radPerSec, -gravity.z * radPerSec)
            rotationRates += rot
            gravities += gravity
            val yawNoise = if (yawNoiseDegrees != 0.0) rng.nextGaussian() * yawNoiseDegrees else 0.0
            val yaw = yawSign * (angle[i] + yawNoise) * PI / 180.0
            samples += SpinSample(t = t, omega = SpinProjector.project(rot, gravity), yaw = yaw)
        }
        return SyntheticRun(samples, trueOmega, angle, rotationRates, gravities)
    }
}

/** 可重現的偽亂數，測試用，不需要密碼學強度。與 Swift 版同一個演算法。 */
class SplitMix64(seed: Long) {
    private var state: Long = seed

    fun next(): Long {
        state += -0x61c8864680b583ebL          // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L   // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L   // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    fun nextUniform(): Double = (next() ushr 11).toDouble() * (1.0 / 9_007_199_254_740_992.0)

    /** Box–Muller。 */
    fun nextGaussian(): Double {
        val u1 = maxOf(nextUniform(), 1e-12)
        val u2 = nextUniform()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}
