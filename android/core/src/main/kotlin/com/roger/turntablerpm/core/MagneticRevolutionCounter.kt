package com.roger.turntablerpm.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 完全不經過陀螺儀的圈數計 —— 直接數地磁向量在裝置座標系裡轉了幾圈。
 *
 * **為什麼需要這個。** 融合出來的方位角（Android 的 `TYPE_ROTATION_VECTOR`、
 * iOS 的 `attitude.yaw`）在盤面連續高速轉動時會被降權，退化成純陀螺儀積分
 * （見 [CalibrationConfidence]）。拿它去校準陀螺儀是同義反覆 ——
 * 不管陀螺儀準不準都會吐出 k ≈ 1。
 *
 * 這裡繞過融合器，改吃磁力計的原始向量：手機在盤上轉一圈，地磁的水平分量
 * 在裝置座標系裡就恰好掃過 360°。這個角度只由「手機相對房間轉了多少」決定，
 * **跟陀螺儀的比例因子完全無關**。
 *
 * **基底固定在第一筆樣本。** 盤面若沒完全水平，逐樣本重算基底會把盤面的章動
 * 灌進角度。固定基底造成的是每圈一次的失真，在整數圈上相減會抵消 ——
 * 這也是 [ScaleCalibrator] 把時間窗切在整數圈的理由。
 *
 * **注意這條路徑量的是「手機相對房間」的轉動。** 房間裡的固定磁源（喇叭磁鐵、
 * 音響變壓器、鋼筋）會造成每圈一次的角度失真，同樣靠整數圈抵消；
 * 但會隨時間變動的磁場沒有辦法，只能拉長量測時間平均掉。
 */
class MagneticRevolutionCounter {

    /** 地磁水平分量累積轉過的角度（絕對值，度）。 */
    var totalDegrees: Double = 0.0
        private set

    /** 完整圈數。 */
    var revolutions: Int = 0
        private set

    /** 實際用到的樣本數。水平分量太小的樣本會被丟掉。 */
    var sampleCount: Int = 0
        private set

    /** 最新一筆的水平分量量值（µT）。 */
    var horizontalMagnitude: Double = 0.0
        private set

    /** 整段量測中水平分量的最小值與最大值。**這兩個數字能完整診斷這條路徑。** */
    var minHorizontal: Double = Double.POSITIVE_INFINITY
        private set
    var maxHorizontal: Double = 0.0
        private set

    private var e1 = Vector3(0.0, 0.0, 0.0)
    private var e2 = Vector3(0.0, 0.0, 0.0)
    private var hasBasis = false

    private var accumulated = 0.0
    private var lastAngle: Double? = null

    // 水平面上的投影點，留著給 refined() 擬合圓心。
    // 100 Hz × 10 分鐘 = 60000 點，可以接受。
    private val xs = ArrayList<Double>()
    private val ys = ArrayList<Double>()

    fun add(field: Vector3, gravity: Vector3) {
        if (!hasBasis) {
            val basis = makeBasis(gravity) ?: return
            e1 = basis.first
            e2 = basis.second
            hasBasis = true
        }

        // 先用「當下這一筆」的重力把垂直分量扣掉，再投影到固定基底。
        // 少了這一步，強磁場的垂直分量會隨盤面章動洩漏進來：實測 470 µT 的
        // 垂直場配上 1.8° 的軸傾斜就洩漏 15 µT，而水平訊號只有 25 µT。
        val down = gravity.normalized ?: return
        val vertical = field.dot(down)
        val horizontal = Vector3(
            field.x - vertical * down.x,
            field.y - vertical * down.y,
            field.z - vertical * down.z,
        )
        val x = horizontal.dot(e1)
        val y = horizontal.dot(e2)
        val h = hypot(x, y)
        if (h <= 1e-9) return

        horizontalMagnitude = h
        if (h < minHorizontal) minHorizontal = h
        if (h > maxHorizontal) maxHorizontal = h
        sampleCount += 1

        if (xs.size < MAX_POINTS) { xs += x; ys += y }

        val angle = atan2(y, x)
        lastAngle?.let { previous ->
            var delta = angle - previous
            while (delta > PI) delta -= 2 * PI
            while (delta < -PI) delta += 2 * PI
            accumulated += delta
            totalDegrees = abs(accumulated) * 180.0 / PI
            revolutions = (totalDegrees / 360.0).toInt()
        }
        lastAngle = angle
    }

    /**
     * 把水平分量的 min/max 拆成「地磁圓的半徑」與「圓心偏移」。
     *
     * 地磁在裝置座標系裡畫一個圓：**半徑 R** 是地磁的水平分量，**圓心偏移 d** 是
     * 跟著手機一起轉的本地磁場（磁吸殼、磁化的盤面、唱片鎮）。量到的水平分量在
     * |d − R| 與 d + R 之間擺盪，所以：
     *
     *     (max + min) / 2 = max(R, d)
     *     (max − min) / 2 = min(R, d)
     *
     * 誰是誰由「有沒有繞圈」決定 —— 圓包住原點才繞得起來，也就是 R > d：
     *
     * - **會繞圈** → 半徑是大的那個，這條路徑健康。
     * - **繞不起來** → 偏移是大的那個，本地磁場蓋過地磁，得先把磁鐵找出來拿掉。
     * - **max ≈ min 且會繞圈** → 完全沒有本地磁場，最理想的情況。
     * - **max ≈ min 但繞不起來** → 水平分量根本沒變化，這個資料來源不能用。
     */
    val horizontalRange: Pair<Double, Double>?
        get() {
            if (sampleCount < 2 || maxHorizontal <= 0 || !minHorizontal.isFinite()) return null
            return Pair((maxHorizontal + minHorizontal) / 2, (maxHorizontal - minHorizontal) / 2)
        }

    /**
     * 扣掉圓心偏移之後重新解捲的結果。
     *
     * **這是把「繞不起來」救回來的關鍵。** 圓心偏移是裝置座標系裡的固定向量，
     * 地磁繞著它畫圓。只要盤面轉過幾圈，資料就已經把整個圓掃過很多遍，
     * 圓心可以直接擬合出來再減掉。這就是硬鐵校準，只是離線做。
     */
    data class Refined(
        val totalDegrees: Double,
        val revolutions: Int,
        /** 擬合出來的圓心偏移量值（µT）—— 跟著手機一起轉的本地磁場。 */
        val centerOffset: Double,
        /** 擬合出來的圓半徑（µT）—— 真正的地磁水平分量。 */
        val radius: Double,
        /** 平均擬合殘差（µT）。 */
        val residual: Double,
    ) {
        /**
         * 擬合可不可信。殘差要遠小於半徑，而且半徑不能小到跟雜訊同級。
         *
         * 門檻原本設 0.25，實測踩到：殘差 4.71 µT／半徑 24.9 µT = 19% 被判成可信，
         * 但那組資料的圓擬合結果跟 min/max 推出來的完全對不上，根本不是個圓。
         * 乾淨的圓殘差應該在 1% 以下，5% 已經很寬鬆了。
         */
        val isTrustworthy: Boolean get() = radius > 1.0 && residual < radius * 0.05
    }

    fun refined(): Refined? {
        if (xs.size < 32) return null
        val fit = fitCircle(xs, ys) ?: return null
        val (a, b, r) = fit

        var accumulated = 0.0
        var previous: Double? = null
        for (i in xs.indices) {
            val angle = atan2(ys[i] - b, xs[i] - a)
            previous?.let { q ->
                var delta = angle - q
                while (delta > PI) delta -= 2 * PI
                while (delta < -PI) delta += 2 * PI
                accumulated += delta
            }
            previous = angle
        }

        var residual = 0.0
        for (i in xs.indices) {
            residual += abs(hypot(xs[i] - a, ys[i] - b) - r)
        }
        residual /= xs.size

        val total = abs(accumulated) * 180.0 / PI
        return Refined(
            totalDegrees = total,
            revolutions = (total / 360.0).toInt(),
            centerOffset = hypot(a, b),
            radius = r,
            residual = residual,
        )
    }

    /**
     * k = 地磁總轉角 ÷ 陀螺儀總轉角。**這個比值才是真正獨立的。**
     *
     * 跟融合路徑的估計用同樣的定義，好讓兩者可以並排比對：兩個數字若一起貼在 1.0，
     * 代表這條路徑也被磁干擾吃掉了；若這個偏離 1.0 而另一個沒有，
     * 就證明融合器確實是元兇。
     */
    fun calibrationFactor(gyroTotalDegrees: Double): Double? {
        if (revolutions < 1 || gyroTotalDegrees <= 0 || totalDegrees <= 0) return null
        return totalDegrees / gyroTotalDegrees
    }

    companion object {
        private const val MAX_POINTS = 100 * 600

        /**
         * Kåsa 代數圓擬合。把 x²+y² = 2ax + 2by + c 當成對 (a, b, c) 的線性最小平方，
         * 一次解出來，不需要疊代也不會有收斂問題。
         */
        internal fun fitCircle(xs: List<Double>, ys: List<Double>): Triple<Double, Double, Double>? {
            val n = xs.size.toDouble()
            if (xs.size < 3 || xs.size != ys.size) return null

            var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            var sxz = 0.0; var syz = 0.0; var sz = 0.0
            for (i in xs.indices) {
                val x = xs[i]; val y = ys[i]
                val z = x * x + y * y
                sx += x; sy += y; sz += z
                sxx += x * x; syy += y * y; sxy += x * y
                sxz += x * z; syz += y * z
            }

            val m = arrayOf(
                doubleArrayOf(2 * sxx, 2 * sxy, sx),
                doubleArrayOf(2 * sxy, 2 * syy, sy),
                doubleArrayOf(2 * sx, 2 * sy, n),
            )
            val rhs = doubleArrayOf(sxz, syz, sz)

            fun det3(a: Array<DoubleArray>): Double =
                a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) -
                    a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0]) +
                    a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])

            val base = det3(m)
            if (abs(base) < 1e-12) return null

            fun solve(column: Int): Double {
                val c = Array(3) { m[it].copyOf() }
                for (row in 0 until 3) c[row][column] = rhs[row]
                return det3(c) / base
            }
            val a = solve(0); val b = solve(1); val c = solve(2)

            val rSquared = c + a * a + b * b
            if (rSquared <= 0) return null
            return Triple(a, b, sqrt(rSquared))
        }

        /** 從重力方向建出水平面的正交基底。 */
        internal fun makeBasis(gravity: Vector3): Pair<Vector3, Vector3>? {
            val axis = gravity.normalized ?: return null
            // 挑一個跟 axis 最不平行的座標軸當種子，避免外積退化。
            val ax = abs(axis.x); val ay = abs(axis.y); val az = abs(axis.z)
            val seed = when {
                ax <= ay && ax <= az -> Vector3(1.0, 0.0, 0.0)
                ay <= az -> Vector3(0.0, 1.0, 0.0)
                else -> Vector3(0.0, 0.0, 1.0)
            }
            val e1 = seed.cross(axis).normalized ?: return null
            return Pair(e1, axis.cross(e1))
        }
    }
}
