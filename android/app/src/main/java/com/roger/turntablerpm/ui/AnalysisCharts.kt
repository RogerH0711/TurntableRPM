package com.roger.turntablerpm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roger.turntablerpm.R
import com.roger.turntablerpm.core.MeasurementAnalysis
import com.roger.turntablerpm.core.PolarAccumulator
import com.roger.turntablerpm.core.PolarBin
import com.roger.turntablerpm.core.SpectralPeak
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

private val Orange = Color(0xFFCC6600)
private val Blue = Color(0xFF1565C0)

// 頻譜的可用範圍。低於 0.1 Hz 是量測時長不夠解析的漂移，高於 50 Hz 超出取樣能力。
private const val FMIN = 0.1
private const val FMAX = 50.0

/**
 * 分析結果的三張圖：頻譜、極座標熱圖、瞬時偏差。
 *
 * **這三張圖是這個 app 跟「只顯示一個 RPM 數字」的工具之間的差別。**
 * 平均轉速只告訴你盤轉得快不快；頻譜與極座標告訴你問題出在哪個零件。
 *
 * 全部用 Canvas 手繪，不引第三方圖表函式庫 —— 需要的東西很少
 * （對數橫軸、環狀扇形、一條折線），而多一個相依就多一份 APK 體積與版本風險。
 */
@Composable
fun AnalysisCharts(a: MeasurementAnalysis, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SpectrumCard(a)
        HeatmapCard(a)
        RollingCard(a)
    }
}

// ── 頻譜 ────────────────────────────────────────────────────────────────

@Composable
private fun SpectrumCard(a: MeasurementAnalysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.chart_spectrum), style = MaterialTheme.typography.titleMedium)
            SpectrumChart(
                a.spectrumFrequencies, a.spectrumAmplitudes, a.peaks,
                Modifier.fillMaxWidth().height(190.dp),
            )
            Text(
                stringResource(R.string.chart_spectrum_caption),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SpectrumChart(
    frequencies: DoubleArray,
    amplitudes: DoubleArray,
    peaks: List<SpectralPeak>,
    modifier: Modifier = Modifier,
) {
    // 頻譜點數可達數萬。抽樣到大約 400 點，但每段取**最大值**而不是平均 ——
    // 取平均會把窄峰洗掉，那正是要看的東西。
    val points = remember(frequencies, amplitudes) {
        decimate(frequencies, amplitudes, targetCount = 400)
    }
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = axis.copy(alpha = 0.22f)
    val labelPx = with(LocalDensity.current) { 11.sp.toPx() }

    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val padBottom = labelPx * 1.8f
        val plotH = size.height - padBottom
        val peakAmp = points.maxOf { it.second }
        if (plotH <= 0 || peakAmp <= 0) return@Canvas

        fun xOf(f: Double): Float =
            ((log10(f) - log10(FMIN)) / (log10(FMAX) - log10(FMIN)) * size.width).toFloat()
        fun yOf(amp: Double): Float = (plotH * (1.0 - amp / peakAmp)).toFloat()

        val paint = labelPaint(labelPx, axis)
        for (f in listOf(0.1, 0.5, 1.0, 5.0, 10.0, 50.0)) {
            val x = xOf(f).coerceIn(0.5f, size.width - 0.5f)
            drawLine(grid, Offset(x, 0f), Offset(x, plotH), strokeWidth = 1f)
            val text = if (f < 1) "%.1f".format(f) else "%.0f".format(f)
            val w = paint.measureText(text)
            // 兩端的標籤要往內收，否則會被畫布邊緣切掉。
            val tx = (x - w / 2f).coerceIn(0f, size.width - w)
            drawContext.canvas.nativeCanvas.drawText(text, tx, size.height - labelPx * 0.3f, paint)
        }
        drawLine(grid, Offset(0f, plotH), Offset(size.width, plotH), strokeWidth = 1f)

        for (i in 1 until points.size) {
            drawLine(
                Blue,
                Offset(xOf(points[i - 1].first), yOf(points[i - 1].second)),
                Offset(xOf(points[i].first), yOf(points[i].second)),
                strokeWidth = 2f,
            )
        }

        for (p in peaks.take(3)) {
            if (p.frequencyHz < FMIN || p.frequencyHz > FMAX) continue
            drawCircle(
                if (p.isRotationHarmonic) Orange else Color.Gray,
                radius = 6f,
                center = Offset(xOf(p.frequencyHz), yOf(p.amplitudePercent)),
            )
        }

        drawContext.canvas.nativeCanvas.drawText(
            "%.3f %%".format(peakAmp), 4f, labelPx, paint,
        )
    }
}

/** 抽樣到大約 [targetCount] 點，每段取最大值。窄峰不能被平均洗掉。 */
private fun decimate(
    frequencies: DoubleArray,
    amplitudes: DoubleArray,
    targetCount: Int,
): List<Pair<Double, Double>> {
    if (frequencies.size != amplitudes.size || frequencies.size < 2) return emptyList()
    val usable = frequencies.indices.filter { frequencies[it] in FMIN..FMAX }
    if (usable.isEmpty()) return emptyList()
    val step = max(1, usable.size / targetCount)
    val out = ArrayList<Pair<Double, Double>>(usable.size / step + 1)
    var i = 0
    while (i < usable.size) {
        val chunk = usable.subList(i, minOf(i + step, usable.size))
        val best = chunk.maxByOrNull { amplitudes[it] }!!
        out += frequencies[best] to amplitudes[best]
        i += step
    }
    return out
}

// ── 極座標熱圖 ──────────────────────────────────────────────────────────

@Composable
private fun HeatmapCard(a: MeasurementAnalysis) {
    // 色階。
    //
    // 核心建議 ±2 × 加權 WRMS，理由是固定上下限才能跨次量測比較顏色。但慢速 wow
    // 會被加權曲線大幅折減（0.55 Hz 的權重只有 0.29），所以 WRMS 常常遠低於原始
    // 偏差 —— 實測 1× 振幅 0.40% 配上建議色階 0.19%，整張圖有一半是削平的。
    //
    // 折衷：不夠時把上限撐到剛好包住最大值，**並且把數值印在圖例上**。
    // 可比較性靠印出來的數字維持，不是靠固定色階犧牲掉圖面資訊。
    val suggested = PolarAccumulator.suggestedColorScale(a.wowFlutter.wrmsPercent)
    val peak = a.polarBins.maxOfOrNull { abs(it.meanDeviation) } ?: 0.0
    val scale = maxOf(suggested, peak * 1.05, 0.001)
    val expanded = scale > suggested * 1.001

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.chart_heatmap),
                style = MaterialTheme.typography.titleMedium,
            )
            PolarHeatmap(
                a.polarBins, scale, a.peakAngleDegrees,
                Modifier.fillMaxWidth().height(260.dp),
            )
            HeatmapLegend(scale)
            a.peakAngleDegrees?.let {
                Text(
                    stringResource(R.string.chart_peak_angle, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.chart_angle_not_comparable),
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                Text(
                    stringResource(R.string.chart_scale_expanded),
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange,
                )
            }
        }
    }
}

/**
 * 極座標熱圖：把偏差依「圈內角度」畫成一個環。
 *
 * 這張圖回答的是「誤差集中在盤面的哪一段」。均勻散開代表隨機抖動；
 * 集中在某個角度代表**偏心** —— 盤面、主軸或皮帶接觸面沒對正。
 *
 * 環用「粗筆畫的圓弧」畫，不是「扇形再挖洞」—— 挖洞要填背景色，
 * 那會把這個元件綁死在某一個主題色上。
 */
@Composable
private fun PolarHeatmap(
    bins: List<PolarBin>,
    scale: Double,
    peakAngleDegrees: Double?,
    modifier: Modifier = Modifier,
) {
    val pointer = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        if (bins.isEmpty() || scale <= 0) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = minOf(size.width, size.height) / 2f * 0.94f
        val inner = outer * 0.42f
        val mid = (outer + inner) / 2f
        val band = outer - inner
        val step = 360f / bins.size

        for ((i, bin) in bins.withIndex()) {
            // 0° 畫在正上方，順時針增加 —— 跟從上方看唱盤的方向一致。
            // 掃角多給一點點，否則相鄰扇形之間會露出背景細縫。
            drawArc(
                color = heatColor(bin.meanDeviation / scale),
                startAngle = i * step - 90f,
                sweepAngle = step + 0.6f,
                useCenter = false,
                topLeft = Offset(cx - mid, cy - mid),
                size = Size(mid * 2, mid * 2),
                style = Stroke(width = band),
            )
        }

        peakAngleDegrees?.let { deg ->
            val a = ((deg - 90.0) * Math.PI / 180.0)
            drawLine(
                pointer,
                Offset(cx + (cos(a) * inner * 0.82).toFloat(), cy + (sin(a) * inner * 0.82).toFloat()),
                Offset(cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat()),
                strokeWidth = 5f,
            )
        }
    }
}

/** 色階說明條。沒有這個，圖上的顏色不知道對應多少。 */
@Composable
private fun HeatmapLegend(scale: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.horizontalGradient(
                        (0..20).map { heatColor(it / 10.0 - 1.0) },
                    ),
                ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.chart_legend_slow, scale),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.chart_legend_accurate),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.chart_legend_fast, scale),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 藍（偏慢）→ 灰（準）→ 紅（偏快）。[t] 已除以色階，會夾在 ±1。 */
private fun heatColor(t: Double): Color {
    val v = t.coerceIn(-1.0, 1.0)
    return if (v >= 0) {
        Color.hsv(7f, (0.75 * v).toFloat(), (0.55 + 0.35 * (1 - v)).toFloat())
    } else {
        Color.hsv(209f, (0.75 * -v).toFloat(), (0.55 + 0.35 * (1 + v)).toFloat())
    }
}

// ── 瞬時偏差 ────────────────────────────────────────────────────────────

@Composable
private fun RollingCard(a: MeasurementAnalysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.chart_rolling),
                style = MaterialTheme.typography.titleMedium,
            )
            RollingChart(
                a.deviationPercent, a.sampleRate,
                Modifier.fillMaxWidth().height(150.dp),
            )
            Text(
                stringResource(R.string.chart_rolling_caption),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RollingChart(
    deviation: DoubleArray,
    sampleRate: Double,
    modifier: Modifier = Modifier,
) {
    // **不能抽樣，要畫封包。** 每 N 筆取一筆會把高於 fs/2N 的成分混疊掉 ——
    // 12000 筆抽到 600 點等於重新用 5 Hz 取樣，3.15 Hz 的抖晃會變成一個
    // 根本不存在的低頻波紋，振幅也看不出來。改成每一欄畫「這一段的最小到最大」，
    // 沒有混疊，而且真實的振幅範圍一定看得到。
    val envelope = remember(deviation) { envelopeOf(deviation, columns = 600) }
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val zero = axis.copy(alpha = 0.4f)
    val labelPx = with(LocalDensity.current) { 11.sp.toPx() }

    Canvas(modifier) {
        if (envelope.size < 2) return@Canvas
        // 上下對稱，零線一定在正中間 —— 不對稱的縱軸會讓「偏快」看起來比「偏慢」嚴重。
        val span = max(envelope.maxOf { max(abs(it.low), abs(it.high)) }, 1e-6)
        val padBottom = labelPx * 1.8f
        val plotH = size.height - padBottom
        fun yOf(d: Double): Float = (plotH / 2 * (1 - d / span)).toFloat()

        drawLine(
            zero, Offset(0f, plotH / 2), Offset(size.width, plotH / 2), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        val dx = size.width / envelope.size
        for ((i, col) in envelope.withIndex()) {
            val x = i * dx + dx / 2
            drawLine(
                Blue.copy(alpha = 0.32f),
                Offset(x, yOf(col.high)),
                Offset(x, yOf(col.low)),
                strokeWidth = maxOf(dx, 1.2f),
            )
        }
        // 每欄平均疊在封包上。淡色的帶是「快的抖」，這條線是「慢的漂」——
        // 兩者在同一張圖上分得開，光看封包只會看到一條實心帶。
        for (i in 1 until envelope.size) {
            drawLine(
                Blue,
                Offset((i - 1) * dx + dx / 2, yOf(envelope[i - 1].mean)),
                Offset(i * dx + dx / 2, yOf(envelope[i].mean)),
                strokeWidth = 2f,
            )
        }

        val paint = labelPaint(labelPx, axis)
        drawContext.canvas.nativeCanvas.drawText("+%.2f%%".format(span), 4f, labelPx, paint)
        drawContext.canvas.nativeCanvas.drawText("−%.2f%%".format(span), 4f, plotH - 2f, paint)
        val seconds = "%.0f s".format((deviation.size - 1) / sampleRate)
        val w = paint.measureText(seconds)
        drawContext.canvas.nativeCanvas.drawText(
            seconds, size.width - w, size.height - labelPx * 0.3f, paint,
        )
        drawContext.canvas.nativeCanvas.drawText("0 s", 0f, size.height - labelPx * 0.3f, paint)
    }
}

private data class Column(val low: Double, val high: Double, val mean: Double)

/** 把序列壓成 [columns] 段，每段取最小、最大與平均。 */
private fun envelopeOf(values: DoubleArray, columns: Int): List<Column> {
    if (values.isEmpty()) return emptyList()
    val n = minOf(columns, values.size)
    return (0 until n).map { c ->
        val from = (c.toLong() * values.size / n).toInt()
        val to = maxOf(from + 1, ((c + 1).toLong() * values.size / n).toInt())
        var lo = values[from]
        var hi = values[from]
        var sum = 0.0
        for (i in from until to) {
            if (values[i] < lo) lo = values[i]
            if (values[i] > hi) hi = values[i]
            sum += values[i]
        }
        Column(lo, hi, sum / (to - from))
    }
}

private fun DrawScope.labelPaint(sizePx: Float, color: Color) = android.graphics.Paint().apply {
    isAntiAlias = true
    textSize = sizePx
    this.color = color.toArgb()
}
