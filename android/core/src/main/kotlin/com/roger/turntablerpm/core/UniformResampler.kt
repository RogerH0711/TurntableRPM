package com.roger.turntablerpm.core

/**
 * FFT 前必做：先線性內插到等間隔網格。
 *
 * **這個模組在 Android 上比在 iOS 上重要得多。** CoreMotion 的派送間隔只是輕微抖動，
 * 而 Android 的 SensorManager 取樣率設定只是「建議值」，實際頻率由廠商實作決定，
 * 不同晶片與機型差異顯著。非均勻取樣會直接汙染頻域分析。
 */
object UniformResampler {

    data class Result(val startTime: Double, val values: DoubleArray)

    fun resample(samples: List<SpinSample>, sampleRate: Double): Result? {
        if (samples.size < 2 || sampleRate <= 0) return null
        val start = samples.first().t
        val span = samples.last().t - start
        if (span <= 0) return null

        val count = (span * sampleRate).toInt() + 1
        val out = DoubleArray(count)
        var j = 0
        for (i in 0 until count) {
            val t = start + i / sampleRate
            while (j + 2 < samples.size && samples[j + 1].t < t) j++
            val a = samples[j]
            val b = samples[j + 1]
            val dt = b.t - a.t
            val u = if (dt > 0) ((t - a.t) / dt).coerceIn(0.0, 1.0) else 0.0
            out[i] = a.omega + (b.omega - a.omega) * u
        }
        return Result(start, out)
    }
}
