package com.wefit.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wefit.app.domain.calc.ChartBar
import com.wefit.app.ui.theme.WeFitTheme
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

fun compactNumber(v: Float): String = when {
    v >= 1_000_000f -> "${(v / 100_000f).toInt() / 10f}M".replace(".0M", "M")
    v >= 1_000f -> "${(v / 100f).toInt() / 10f}K".replace(".0K", "K")
    else -> v.toInt().toString()
}

/** Rounds up to a "nice" axis maximum such as 2K, 5K or 10K. */
fun niceCeiling(v: Float): Float {
    if (v <= 0f) return 1f
    val exp = 10.0.pow(log10(v.toDouble()).toInt().toDouble()).toFloat()
    val f = v / exp
    val nice = when {
        f <= 1f -> 1f
        f <= 2f -> 2f
        f <= 2.5f -> 2.5f
        f <= 5f -> 5f
        else -> 10f
    }
    return nice * exp
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    brush: Brush? = null,
    trackColor: Color = WeFitTheme.colors.chartTrack,
    content: @Composable () -> Unit = {},
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 1f), tween(700)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (brush != null) {
                drawArc(brush, -90f, 360f * anim.value, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            } else {
                drawArc(color, -90f, 360f * anim.value, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightColor: Color = WeFitTheme.colors.accent,
    goalValue: Float? = null,
    labelEvery: Int = 1,
    description: String = "Bar chart",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val track = WeFitTheme.colors.chartTrack
    val goalColor = WeFitTheme.colors.accent
    // Never scale an empty chart to "1": keep a readable minimum axis.
    val maxValue = niceCeiling(maxOf(bars.maxOfOrNull { it.value } ?: 0f, goalValue ?: 0f, 100f))
    val anim = remember { Animatable(0f) }
    LaunchedEffect(bars) { anim.snapTo(0f); anim.animateTo(1f, tween(600)) }

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (bars.isEmpty()) return@Canvas
        val left = 34.dp.toPx()
        val bottom = 20.dp.toPx()
        val top = 8.dp.toPx()
        val chartH = size.height - bottom - top
        val chartW = size.width - left
        for (i in 0..2) {
            val v = maxValue * i / 2f
            val y = top + chartH * (1f - i / 2f)
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
            val layout = measurer.measure(compactNumber(v), labelStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }
        val slot = chartW / bars.size
        val barW = (slot * 0.56f).coerceAtMost(26.dp.toPx())
        bars.forEachIndexed { i, b ->
            val x = left + slot * i + (slot - barW) / 2f
            val radius = CornerRadius(barW / 2.4f)
            drawRoundRect(track, Offset(x, top), Size(barW, chartH), radius)
            val h = chartH * (b.value / maxValue).coerceIn(0f, 1f) * anim.value
            if (h > 0f) {
                drawRoundRect(if (b.highlighted) highlightColor else barColor, Offset(x, top + chartH - h), Size(barW, h), radius)
            }
            if (i % labelEvery == 0) {
                val layout = measurer.measure(b.label, labelStyle)
                drawText(layout, topLeft = Offset(x + barW / 2f - layout.size.width / 2f, size.height - layout.size.height))
            }
        }
        goalValue?.takeIf { it > 0f }?.let { g ->
            val y = top + chartH * (1f - g / maxValue)
            drawLine(goalColor, Offset(left, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    targetValue: Float? = null,
    valueFormatter: (Float) -> String = { "%.1f".format(it) },
    description: String = "Line chart",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val targetColor = WeFitTheme.colors.accent
    val surface = MaterialTheme.colorScheme.surface
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) { anim.snapTo(0f); anim.animateTo(1f, tween(700)) }

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val all = values + listOfNotNull(targetValue)
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1f) { lo -= 1f; hi += 1f }
        val pad = (hi - lo) * 0.15f
        lo -= pad; hi += pad
        val left = 40.dp.toPx()
        val bottom = 20.dp.toPx()
        val top = 10.dp.toPx()
        val right = 10.dp.toPx()
        val chartH = size.height - top - bottom
        val chartW = size.width - left - right
        fun yOf(v: Float) = top + chartH * (1f - (v - lo) / (hi - lo))
        fun xOf(i: Int) = if (values.size == 1) left + chartW / 2f else left + chartW * i / (values.size - 1)

        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3f
            val y = yOf(v)
            drawLine(gridColor, Offset(left, y), Offset(size.width - right, y), 1f)
            val layout = measurer.measure(valueFormatter(v), labelStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }
        targetValue?.let { t ->
            drawLine(targetColor, Offset(left, yOf(t)), Offset(size.width - right, yOf(t)), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        val visible = ceil(values.size * anim.value).toInt().coerceIn(1, values.size)
        val line = Path()
        val fill = Path()
        for (i in 0 until visible) {
            val p = Offset(xOf(i), yOf(values[i]))
            if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, top + chartH); fill.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
        }
        fill.lineTo(xOf(visible - 1), top + chartH)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent), startY = top, endY = top + chartH))
        drawPath(line, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0 until visible) {
            val p = Offset(xOf(i), yOf(values[i]))
            drawCircle(lineColor, if (i == values.lastIndex) 6.dp.toPx() else 4.dp.toPx(), p)
            drawCircle(surface, if (i == values.lastIndex) 3.dp.toPx() else 2.dp.toPx(), p)
        }
        if (labels.isNotEmpty()) {
            val idx = if (labels.size <= 5) labels.indices.toList() else listOf(0, labels.size / 2, labels.lastIndex)
            idx.forEach { i ->
                val layout = measurer.measure(labels[i], labelStyle)
                val x = (xOf(i) - layout.size.width / 2f).coerceIn(left - 10f, size.width - layout.size.width.toFloat())
                drawText(layout, topLeft = Offset(x, size.height - layout.size.height))
            }
        }
    }
}
