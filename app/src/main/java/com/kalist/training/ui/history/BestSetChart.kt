package com.kalist.training.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Best set per session over time: one series (accent color), 2 dp line, 8 dp markers with a surface
 * ring, recessive gridlines, direct labels on the latest and the best point only, tap a point to
 * inspect it. x is proportional to the date.
 */
@Composable
fun BestSetChart(
    entries: List<HistoryEntry>,
    formatValue: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (entries.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    var selected by remember(entries) { mutableStateOf<Int?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }

    val first = entries.first().date
    val spanDays = ChronoUnit.DAYS.between(first, entries.last().date).coerceAtLeast(1)
    val maxValue = entries.maxOf { it.best }
    val yMax = niceMax(maxValue)
    val bestIndex = entries.indices.maxBy { entries[it].best }

    Column(modifier) {
        val sel = selected
        Text(
            if (sel != null) "${entries[sel].date.format(dateFormat)} · ${formatValue(entries[sel].best)}" else " ",
            style = MaterialTheme.typography.titleSmall,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = description }
                .pointerInput(entries) {
                    detectTapGestures { tap ->
                        val left = 40.dp.toPx(); val right = size.width - 16.dp.toPx()
                        val xs = entries.map { left + (right - left) * ChronoUnit.DAYS.between(first, it.date) / spanDays.toFloat() }
                        val nearest = xs.indices.minBy { abs(xs[it] - tap.x) }
                        selected = if (selected == nearest) null else nearest
                    }
                },
        ) {
            val left = 40.dp.toPx()
            val right = size.width - 16.dp.toPx()
            val top = 20.dp.toPx()
            val bottom = size.height - 20.dp.toPx()
            fun x(i: Int) = left + (right - left) * ChronoUnit.DAYS.between(first, entries[i].date) / spanDays.toFloat()
            fun y(v: Int) = bottom - (bottom - top) * v / yMax.toFloat()

            // Recessive grid with y labels (4 steps from 0).
            for (step in 0..4) {
                val v = yMax * step / 4
                val gy = y(v)
                drawLine(grid, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx(),
                    pathEffect = if (step == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawLabel(measurer, formatValue(v), labelStyle.copy(color = muted), Offset(0f, gy), alignRight = left - 6.dp.toPx())
            }
            // x labels: first and last date.
            drawLabel(measurer, entries.first().date.format(dateFormat), labelStyle.copy(color = muted), Offset(left, bottom + 4.dp.toPx()), centerTop = true)
            drawLabel(measurer, entries.last().date.format(dateFormat), labelStyle.copy(color = muted), Offset(right, bottom + 4.dp.toPx()), centerTop = true)

            sel?.let { drawLine(grid, Offset(x(it), top), Offset(x(it), bottom), strokeWidth = 1.dp.toPx()) }

            val path = Path().apply {
                entries.indices.forEach { i -> if (i == 0) moveTo(x(i), y(entries[i].best)) else lineTo(x(i), y(entries[i].best)) }
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            entries.indices.forEach { i ->
                val c = Offset(x(i), y(entries[i].best))
                val r = if (i == sel) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(surface, r + 2.dp.toPx(), c)
                drawCircle(line, r, c)
            }
            // Direct labels: latest and best only.
            setOf(entries.lastIndex, bestIndex).forEach { i ->
                drawLabel(measurer, formatValue(entries[i].best), valueStyle.copy(color = ink), Offset(x(i), y(entries[i].best) - 8.dp.toPx()), centerBottom = true, clampRight = size.width)
            }
        }
    }
}

/** Top of the y axis: 4 gridline steps of a "nice" size (1, 1.5, 2, 2.5, 3, 4, 5 × 10^k) with ~15% headroom. */
internal fun niceMax(max: Int): Int {
    val rawStep = (max * 1.15 / 4).coerceAtLeast(1.0)
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawStep)))
    val step = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep }
    return (step * 4).toInt()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    anchor: Offset,
    alignRight: Float? = null,
    centerTop: Boolean = false,
    centerBottom: Boolean = false,
    clampRight: Float? = null,
) {
    val layout = measurer.measure(text, style)
    var x = anchor.x
    var y = anchor.y
    when {
        alignRight != null -> { x = alignRight - layout.size.width; y -= layout.size.height / 2f }
        centerTop -> x -= layout.size.width / 2f
        centerBottom -> { x -= layout.size.width / 2f; y -= layout.size.height }
    }
    x = x.coerceAtLeast(0f)
    clampRight?.let { x = x.coerceAtMost(it - layout.size.width) }
    if (centerTop) x = x.coerceIn(0f, size.width - layout.size.width)
    drawText(layout, topLeft = Offset(x, y))
}
