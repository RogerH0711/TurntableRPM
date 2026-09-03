package com.roger.turntablerpm.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.history.MeasurementRecord
import kotlin.math.abs

private val TrendBlue = Color(0xFF1565C0)
private val TrendOrange = Color(0xFFCC6600)
private val TrendGreen = Color(0xFF2E7D32)

/** 趨勢圖看的三個指標。 */
private enum class Metric(@StringRes val label: Int) {
    Error(R.string.trend_metric_error),
    Wow(R.string.trend_metric_wow),
    Eccentricity(R.string.trend_metric_eccentricity),
    ;

    /** 偏差有正負、目標是 0；另外兩個恆為正、愈小愈好。 */
    val hasZeroTarget: Boolean get() = this == Error

    fun of(r: MeasurementRecord): Double? = when (this) {
        Error -> r.errorPercent
        Wow -> r.wrmsPercent
        Eccentricity -> r.onePerRevPercent
    }
}

/**
 * 歷史趨勢。
 *
 * **這張圖是歷史記錄存在的理由。** 一筆一筆看數字沒辦法回答「上次調整之後
 * 有沒有變好」，也沒辦法做「手機轉 180° 再量一次」那種需要並排比較的實驗。
 *
 * 三個指標分開看，因為它們的行為不一樣：偏差是校準之後才有意義的絕對量，
 * 抖晃率與偏心是比值、不受校準影響。**未校準的點畫成空心**，
 * 它的偏差不可採信，不該跟其他點看起來一樣有份量。
 *
 * @param records 由**新到舊**（跟列表同一個順序），內部會反轉成時間序。
 */
@Composable
fun TrendChart(records: List<MeasurementRecord>, modifier: Modifier = Modifier) {
    var metric by remember { mutableStateOf(Metric.Error) }
    // 橫軸用序號而不是時間：量測之間的間隔可能差好幾天，按真實時間排會把
    // 同一個晚上連做的幾次擠成一團，而那幾次正是最需要互相比較的。
    val points = remember(records, metric) {
        records.reversed().mapNotNull { r -> metric.of(r)?.let { it to r.isCalibrated } }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Metric.entries) {
                FilterChip(
                    selected = metric == m,
                    onClick = { metric = m },
                    label = { Text(stringResource(m.label)) },
                )
            }
        }

        if (points.size < 2) {
            Text(
                stringResource(R.string.trend_not_enough_data),
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        Plot(points, metric, Modifier.fillMaxWidth().height(180.dp))

        if (points.any { !it.second }) {
            // **只在偏差那一頁示警。** 抖晃率與偏心是比值，陀螺儀的比例因子誤差
            // 會完全抵消 —— 對那兩個指標把空心點標成橘色是喊假警報，
            // 喊多了真正該注意的那次就沒人看了。
            if (metric.hasZeroTarget) {
                Text(
                    stringResource(R.string.trend_uncalibrated_error_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = TrendOrange,
                )
            } else {
                Text(
                    stringResource(R.string.trend_uncalibrated_ratio_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        ChangeDescription(points, metric)
    }
}

@Composable
private fun Plot(
    points: List<Pair<Double, Boolean>>,
    metric: Metric,
    modifier: Modifier = Modifier,
) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPx = with(LocalDensity.current) { 11.sp.toPx() }
    val older = stringResource(R.string.trend_axis_old)
    val newer = stringResource(R.string.trend_axis_new)

    Canvas(modifier) {
        val values = points.map { it.first }
        // 縱軸範圍。偏差有正負且目標是 0，所以上下都要留白；
        // **抖晃率與偏心恆為正，下緣就鎖在 0** —— 往下留白會印出一個
        // 「−0.046%」的軸標，而那是不可能存在的值。
        var lo = if (metric.hasZeroTarget) minOf(values.min(), 0.0) else 0.0
        var hi = maxOf(values.max(), 0.0)
        if (hi - lo < 1e-6) hi = lo + 1.0
        val pad = (hi - lo) * 0.1
        hi += pad
        if (metric.hasZeroTarget) lo -= pad

        val padBottom = labelPx * 1.6f
        val plotH = size.height - padBottom
        fun yOf(v: Double): Float = (plotH * (hi - v) / (hi - lo)).toFloat()
        val dx = if (points.size > 1) size.width / (points.size - 1) else 0f

        if (metric.hasZeroTarget) {
            val y = yOf(0.0)
            drawLine(
                TrendGreen.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        for (i in 1 until points.size) {
            drawLine(
                TrendBlue,
                Offset((i - 1) * dx, yOf(values[i - 1])),
                Offset(i * dx, yOf(values[i])),
                strokeWidth = 2.5f,
            )
        }
        for ((i, p) in points.withIndex()) {
            val c = Offset(i * dx, yOf(p.first))
            if (p.second) {
                drawCircle(TrendBlue, radius = 7f, center = c)
            } else {
                // 空心 —— 未校準的點不該跟其他點看起來一樣有份量。
                drawCircle(TrendOrange, radius = 7f, center = c, style = Stroke(width = 2.5f))
            }
        }

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = labelPx
            color = axis.toArgb()
        }
        // 正負號只在偏差那一頁有意義。
        val fmt = if (metric.hasZeroTarget) "%+.3f%%" else "%.3f%%"
        drawContext.canvas.nativeCanvas.drawText(fmt.format(hi), 4f, labelPx, paint)
        drawContext.canvas.nativeCanvas.drawText(fmt.format(lo), 4f, plotH - 2f, paint)
        drawContext.canvas.nativeCanvas.drawText(older, 0f, size.height - 2f, paint)
        drawContext.canvas.nativeCanvas.drawText(
            newer, size.width - paint.measureText(newer), size.height - 2f, paint,
        )
    }
}

/**
 * 直接講出「變好還是變差」。看圖要自己判讀，寫出來就不用。
 *
 * 只拿已校準的點來比：未校準的偏差是唱盤誤差與陀螺儀誤差相乘的結果，
 * 跟已校準的點放在一起比較沒有意義。
 */
@Composable
private fun ChangeDescription(points: List<Pair<Double, Boolean>>, metric: Metric) {
    val usable = if (metric.hasZeroTarget) points.filter { it.second } else points
    if (usable.size < 2) return
    val first = usable.first().first
    val last = usable.last().first
    val delta = abs(last) - abs(first)
    val text = if (abs(delta) <= 0.001) {
        stringResource(R.string.trend_unchanged)
    } else {
        // 「改善／變差」是動詞，語序在日文裡跟中文相反 —— 所以整句用位置參數，
        // 由翻譯決定動詞擺在哪裡（CLAUDE.md 坑 29c）。
        val word = stringResource(
            if (delta < 0) R.string.trend_improved else R.string.trend_worse,
        )
        stringResource(R.string.trend_change, word, abs(delta), first, last)
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
}
