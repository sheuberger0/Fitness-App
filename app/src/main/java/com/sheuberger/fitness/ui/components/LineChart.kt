package com.sheuberger.fitness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Selbst gezeichneter Liniengraph (ohne externe Bibliothek).
 *
 * - Die x-Achse ist eine echte Zeitachse: Abstände zwischen Punkten entsprechen den Tagen dazwischen.
 * - Die y-Achse bekommt "runde" Beschriftungen (z. B. 60, 65, 70 kg).
 * - Antippen oder waagerecht Wischen wählt einen Punkt aus; [onSelect] meldet ihn nach außen,
 *   damit die Überschrift über dem Graphen den gewählten Wert zeigen kann.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    onSelect: (ChartPoint?) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow

    val scale = remember(points) { YScale.from(points.map { it.value }) }
    val yLabels = remember(scale, labelStyle) {
        scale.ticks.map { textMeasurer.measure(Format.number(it), labelStyle) }
    }
    val xLabels = remember(points, labelStyle) { buildXLabels(points).map { (date, text) -> date to textMeasurer.measure(text, labelStyle) } }

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    fun select(index: Int?) {
        if (index != selectedIndex) {
            if (index != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedIndex = index
            onSelect(index?.let(points::get))
        }
    }

    val yLabelWidth = yLabels.maxOfOrNull { it.size.width } ?: 0
    val xLabelHeight = xLabels.maxOfOrNull { it.second.size.height } ?: 0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val geometry = chartGeometry(size.width.toFloat(), size.height.toFloat(), this, points, scale, yLabelWidth, xLabelHeight)
                    val index = geometry.nearestIndex(points, offset.x)
                    select(if (index == selectedIndex) null else index)
                }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val geometry = chartGeometry(size.width.toFloat(), size.height.toFloat(), this, points, scale, yLabelWidth, xLabelHeight)
                        select(geometry.nearestIndex(points, offset.x))
                    },
                    onDragEnd = { select(null) },
                    onDragCancel = { select(null) },
                    onHorizontalDrag = { change, _ ->
                        val geometry = chartGeometry(size.width.toFloat(), size.height.toFloat(), this, points, scale, yLabelWidth, xLabelHeight)
                        select(geometry.nearestIndex(points, change.position.x))
                        change.consume()
                    },
                )
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val geometry = chartGeometry(size.width, size.height, this, points, scale, yLabelWidth, xLabelHeight)

        drawGrid(geometry, scale, yLabels, gridColor)
        drawXLabels(geometry, xLabels)
        drawSeries(geometry, points, lineColor, backgroundColor)

        selectedIndex?.let { index ->
            val point = points[index]
            val x = geometry.x(point.date)
            val y = geometry.y(point.value)
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(x, geometry.top),
                end = Offset(x, geometry.bottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawCircle(backgroundColor, radius = 8.dp.toPx(), center = Offset(x, y))
            drawCircle(lineColor, radius = 6.dp.toPx(), center = Offset(x, y))
        }
    }
}

/** Wertebereich und Beschriftungen der y-Achse. */
private class YScale(val min: Double, val max: Double, val ticks: List<Double>) {
    companion object {
        fun from(values: List<Double>): YScale {
            if (values.isEmpty()) return YScale(0.0, 1.0, emptyList())
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < 1e-9) {
                // Nur ein Wert: etwas Luft nach oben und unten lassen.
                val pad = maxOf(1.0, abs(hi) * 0.05)
                lo -= pad
                hi += pad
            }
            val step = niceStep(hi - lo, targetTicks = 3)
            val start = floor(lo / step) * step
            val end = ceil(hi / step) * step
            val ticks = generateSequence(start) { it + step }
                .takeWhile { it <= end + step / 2 }
                .toList()
            return YScale(start, end, ticks)
        }

        /** Wählt eine "schöne" Schrittweite wie 1, 2, 2,5, 5, 10, 20 … */
        private fun niceStep(range: Double, targetTicks: Int): Double {
            val raw = range / targetTicks
            val magnitude = 10.0.pow(floor(log10(raw)))
            val normalized = raw / magnitude
            val nice = when {
                normalized <= 1.0 -> 1.0
                normalized <= 2.0 -> 2.0
                normalized <= 2.5 -> 2.5
                normalized <= 5.0 -> 5.0
                else -> 10.0
            }
            return nice * magnitude
        }
    }
}

/** Rechnet Datum/Wert in Pixel-Koordinaten um. */
private class ChartGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val labelX: Float,
    private val minDay: Long,
    private val maxDay: Long,
    private val minValue: Double,
    private val maxValue: Double,
) {
    fun x(date: LocalDate): Float {
        if (maxDay == minDay) return (left + right) / 2
        return left + (date.toEpochDay() - minDay).toFloat() / (maxDay - minDay) * (right - left)
    }

    fun y(value: Double): Float =
        bottom - ((value - minValue) / (maxValue - minValue)).toFloat() * (bottom - top)

    fun nearestIndex(points: List<ChartPoint>, touchX: Float): Int? =
        points.indices.minByOrNull { abs(x(points[it].date) - touchX) }
}

private fun chartGeometry(
    width: Float,
    height: Float,
    density: Density,
    points: List<ChartPoint>,
    scale: YScale,
    yLabelWidth: Int,
    xLabelHeight: Int,
): ChartGeometry = with(density) {
    val edge = 8.dp.toPx()
    val labelGap = 12.dp.toPx()
    val plotRight = width - yLabelWidth - labelGap
    ChartGeometry(
        left = edge,
        top = 10.dp.toPx(),
        right = plotRight - edge,
        bottom = height - xLabelHeight - 10.dp.toPx(),
        labelX = plotRight + labelGap / 2,
        minDay = points.minOfOrNull { it.date.toEpochDay() } ?: 0,
        maxDay = points.maxOfOrNull { it.date.toEpochDay() } ?: 0,
        minValue = scale.min,
        maxValue = scale.max,
    )
}

private fun DrawScope.drawGrid(
    geometry: ChartGeometry,
    scale: YScale,
    labels: List<TextLayoutResult>,
    gridColor: Color,
) {
    scale.ticks.forEachIndexed { i, tick ->
        val y = geometry.y(tick)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(geometry.labelX - 6.dp.toPx(), y),
            strokeWidth = 1.dp.toPx(),
        )
        val label = labels[i]
        drawText(label, topLeft = Offset(geometry.labelX, y - label.size.height / 2f))
    }
}

private fun DrawScope.drawXLabels(
    geometry: ChartGeometry,
    labels: List<Pair<LocalDate, TextLayoutResult>>,
) {
    val y = size.height - (labels.maxOfOrNull { it.second.size.height } ?: 0)
    labels.forEachIndexed { i, (date, layout) ->
        val anchor = geometry.x(date)
        val width = layout.size.width
        val x = when {
            labels.size == 1 -> anchor - width / 2f
            i == 0 -> 0f
            i == labels.lastIndex -> geometry.right - width + 8.dp.toPx()
            else -> anchor - width / 2f
        }
        drawText(layout, topLeft = Offset(x.coerceAtLeast(0f), y))
    }
}

private fun DrawScope.drawSeries(
    geometry: ChartGeometry,
    points: List<ChartPoint>,
    lineColor: Color,
    backgroundColor: Color,
) {
    val coordinates = points.map { Offset(geometry.x(it.date), geometry.y(it.value)) }

    if (coordinates.size > 1) {
        val line = Path().apply {
            moveTo(coordinates.first().x, coordinates.first().y)
            coordinates.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(coordinates.last().x, geometry.bottom)
            lineTo(coordinates.first().x, geometry.bottom)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
                startY = geometry.top,
                endY = geometry.bottom,
            ),
        )
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // Einzelne Punkte nur zeichnen, solange sie sich nicht gegenseitig überdecken.
    val spacing = (geometry.right - geometry.left) / coordinates.size.coerceAtLeast(1)
    if (coordinates.size == 1 || spacing > 10.dp.toPx()) {
        coordinates.forEach {
            drawCircle(lineColor, radius = 3.5.dp.toPx(), center = it)
            drawCircle(backgroundColor, radius = 1.75.dp.toPx(), center = it)
        }
    }
}

/** Beschriftungen der x-Achse: erstes Datum, letztes Datum und ggf. die Mitte. */
private fun buildXLabels(points: List<ChartPoint>): List<Pair<LocalDate, String>> {
    if (points.isEmpty()) return emptyList()
    val first = points.first().date
    val last = points.last().date
    val spanDays = last.toEpochDay() - first.toEpochDay()
    val label: (LocalDate) -> String = if (spanDays > 300) Format::monthYear else Format::shortDate
    if (spanDays == 0L) return listOf(first to label(first))
    val result = mutableListOf(first to label(first))
    if (spanDays >= 14) {
        val middle = LocalDate.ofEpochDay(first.toEpochDay() + spanDays / 2)
        result += middle to label(middle)
    }
    result += last to label(last)
    return result
}
