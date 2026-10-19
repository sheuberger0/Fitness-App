package com.sheuberger.fitness.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Selbst gezeichneter Liniengraph (ohne externe Bibliothek).
 *
 * - Die x-Achse ist eine echte Zeitachse: Abstände zwischen Punkten entsprechen den Tagen dazwischen.
 * - Die y-Achse bekommt "runde" Beschriftungen (z. B. 60, 65, 70 kg).
 * - Die Linie ist monoton geglättet (Fritsch–Carlson): sie schießt nie über die echten Werte hinaus.
 * - Beim ersten Erscheinen zeichnet sich die Linie von links nach rechts; bei einem Wechsel des
 *   Zeitraums gleiten Achsen und Linie zum neuen Bereich, statt zu springen.
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
    val scope = rememberCoroutineScope()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val tooltipColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow

    val scale = remember(points) { YScale.from(points.map { it.value }) }
    val grid = remember(scale, labelStyle) {
        Grid(scale.ticks, scale.ticks.map { textMeasurer.measure(Format.number(it), labelStyle) })
    }
    val xLabels = remember(points, labelStyle) { buildXLabels(points).map { (date, text) -> date to textMeasurer.measure(text, labelStyle) } }
    val domain = remember(points) { DayDomain.from(points) }

    // Animierte Achsen: Wertebereich (y) und Zeitbereich (x) gleiten bei Datenwechsel zum neuen Ziel.
    val anim = remember { ChartAnimation() }
    LaunchedEffect(points) {
        if (points.isEmpty()) return@LaunchedEffect
        anim.previousGrid = anim.currentGrid
        anim.currentGrid = grid
        if (!anim.started) {
            anim.started = true
            anim.previousGrid = null
            anim.snapTo(scale, domain)
            anim.progress.snapTo(0f)
            anim.progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        } else {
            anim.morphTo(scale, domain)
        }
    }

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    // Zuletzt gewählter Punkt bleibt stehen, während die Auswahl ausblendet.
    var shownIndex by remember(points) { mutableStateOf<Int?>(null) }
    val selection = remember(points) { Animatable(0f) }

    fun select(index: Int?) {
        if (index != selectedIndex) {
            if (index != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedIndex = index
            if (index != null) shownIndex = index
            scope.launch { selection.animateTo(if (index != null) 1f else 0f, tween(120)) }
            onSelect(index?.let(points::get))
        }
    }

    val yLabelWidth = grid.labels.maxOfOrNull { it.size.width } ?: 0
    val xLabelHeight = xLabels.maxOfOrNull { it.second.size.height } ?: 0

    fun Density.geometry(width: Float, height: Float) =
        chartGeometry(width, height, this, anim, yLabelWidth, xLabelHeight)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val index = geometry(size.width.toFloat(), size.height.toFloat()).nearestIndex(points, offset.x)
                    select(if (index == selectedIndex) null else index)
                }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        select(geometry(size.width.toFloat(), size.height.toFloat()).nearestIndex(points, offset.x))
                    },
                    onDragEnd = { select(null) },
                    onDragCancel = { select(null) },
                    onHorizontalDrag = { change, _ ->
                        select(geometry(size.width.toFloat(), size.height.toFloat()).nearestIndex(points, change.position.x))
                        change.consume()
                    },
                )
            },
    ) {
        if (points.isEmpty() || !anim.started) return@Canvas
        val geometry = geometry(size.width, size.height)
        val fade = anim.fade.value

        val current = anim.currentGrid
        if (current != null && current !== grid) {
            // Neue Daten, Animation startet gleich: bis dahin das alte Raster zeigen.
            drawGrid(geometry, current, gridColor, 1f)
        } else {
            anim.previousGrid?.let { if (fade < 1f) drawGrid(geometry, it, gridColor, 1f - fade) }
            drawGrid(geometry, grid, gridColor, fade)
            drawXLabels(geometry, xLabels, fade)
        }

        // Waagerecht auf die Zeichenfläche begrenzen (beim Zoomen liegen Punkte kurz außerhalb)
        // und beim ersten Erscheinen nur bis zum aktuellen Fortschritt zeichnen.
        val pad = 6.dp.toPx()
        val revealRight = geometry.left - pad + (geometry.right - geometry.left + 2 * pad) * anim.progress.value
        clipRect(left = geometry.left - pad, top = 0f, right = revealRight, bottom = size.height) {
            drawSeries(geometry, points, lineColor, backgroundColor)
        }

        val alpha = selection.value
        val index = shownIndex
        if (index != null && alpha > 0f && index in points.indices) {
            drawSelection(geometry, points[index], alpha, lineColor, backgroundColor, tooltipColor, textMeasurer, tooltipStyle)
        }
    }
}

/** Hält die animierten Achsenwerte, damit nur die Zeichenphase neu läuft. */
private class ChartAnimation {
    var started = false
    val progress = Animatable(0f)
    val fade = Animatable(1f)
    val minValue = Animatable(0f)
    val maxValue = Animatable(1f)
    val minDay = Animatable(0f)
    val maxDay = Animatable(1f)
    var currentGrid: Grid? = null
    var previousGrid: Grid? = null

    suspend fun snapTo(scale: YScale, domain: DayDomain) {
        minValue.snapTo(scale.min.toFloat())
        maxValue.snapTo(scale.max.toFloat())
        minDay.snapTo(domain.min)
        maxDay.snapTo(domain.max)
    }

    suspend fun morphTo(scale: YScale, domain: DayDomain) = coroutineScope {
        val spec = tween<Float>(350, easing = FastOutSlowInEasing)
        launch { progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { minValue.animateTo(scale.min.toFloat(), spec) }
        launch { maxValue.animateTo(scale.max.toFloat(), spec) }
        launch { minDay.animateTo(domain.min, spec) }
        launch { maxDay.animateTo(domain.max, spec) }
        launch {
            fade.snapTo(0f)
            fade.animateTo(1f, spec)
        }
    }
}

/** Beschriftete Linien der y-Achse. */
private class Grid(val ticks: List<Double>, val labels: List<TextLayoutResult>)

/** Zeitbereich in Epochentagen; ein einzelner Tag bekommt etwas Breite, damit er mittig liegt. */
private class DayDomain(val min: Float, val max: Float) {
    companion object {
        fun from(points: List<ChartPoint>): DayDomain {
            if (points.isEmpty()) return DayDomain(0f, 1f)
            val lo = points.minOf { it.date.toEpochDay() }.toFloat()
            val hi = points.maxOf { it.date.toEpochDay() }.toFloat()
            return if (hi - lo < 0.5f) DayDomain(lo - 0.5f, hi + 0.5f) else DayDomain(lo, hi)
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
    private val minDay: Float,
    private val maxDay: Float,
    private val minValue: Float,
    private val maxValue: Float,
) {
    fun x(date: LocalDate): Float {
        val span = maxDay - minDay
        if (span < 1e-3f) return (left + right) / 2
        return left + (date.toEpochDay() - minDay) / span * (right - left)
    }

    fun y(value: Double): Float {
        val span = (maxValue - minValue).takeIf { it > 1e-6f } ?: 1f
        return bottom - ((value.toFloat() - minValue) / span) * (bottom - top)
    }

    fun nearestIndex(points: List<ChartPoint>, touchX: Float): Int? =
        points.indices.minByOrNull { abs(x(points[it].date) - touchX) }
}

private fun chartGeometry(
    width: Float,
    height: Float,
    density: Density,
    anim: ChartAnimation,
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
        minDay = anim.minDay.value,
        maxDay = anim.maxDay.value,
        minValue = anim.minValue.value,
        maxValue = anim.maxValue.value,
    )
}

private fun DrawScope.drawGrid(
    geometry: ChartGeometry,
    grid: Grid,
    gridColor: Color,
    alpha: Float,
) {
    if (alpha <= 0f) return
    val slack = 4.dp.toPx()
    grid.ticks.forEachIndexed { i, tick ->
        val y = geometry.y(tick)
        // Während des Gleitens liegen manche Linien kurz außerhalb – die werden nicht gezeichnet.
        if (y < geometry.top - slack || y > geometry.bottom + slack) return@forEachIndexed
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(geometry.labelX - 6.dp.toPx(), y),
            strokeWidth = 1.dp.toPx(),
            alpha = alpha,
        )
        val label = grid.labels[i]
        drawText(label, topLeft = Offset(geometry.labelX, y - label.size.height / 2f), alpha = alpha)
    }
}

private fun DrawScope.drawXLabels(
    geometry: ChartGeometry,
    labels: List<Pair<LocalDate, TextLayoutResult>>,
    alpha: Float,
) {
    if (alpha <= 0f) return
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
        drawText(layout, topLeft = Offset(x.coerceAtLeast(0f), y), alpha = alpha)
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
        val line = Path().apply { monotoneCubicThrough(coordinates) }
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

/** Hilfslinie, Punkt und Wert-Pille über dem gewählten Punkt; [alpha] blendet alles ein/aus. */
private fun DrawScope.drawSelection(
    geometry: ChartGeometry,
    point: ChartPoint,
    alpha: Float,
    lineColor: Color,
    backgroundColor: Color,
    tooltipColor: Color,
    textMeasurer: TextMeasurer,
    tooltipStyle: TextStyle,
) {
    val x = geometry.x(point.date)
    val y = geometry.y(point.value)
    val grow = 0.6f + 0.4f * alpha
    drawLine(
        color = lineColor.copy(alpha = 0.5f),
        start = Offset(x, geometry.top),
        end = Offset(x, geometry.bottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        alpha = alpha,
    )
    drawCircle(backgroundColor, radius = 8.dp.toPx() * grow, center = Offset(x, y), alpha = alpha)
    drawCircle(lineColor, radius = 6.dp.toPx() * grow, center = Offset(x, y), alpha = alpha)

    // Pille mit Wert und Datum, innerhalb der Zeichenfläche gehalten.
    val text = textMeasurer.measure("${Format.number(point.value)} · ${Format.shortDate(point.date)}", tooltipStyle)
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val gap = 12.dp.toPx()
    val pillW = text.size.width + 2 * padH
    val pillH = text.size.height + 2 * padV
    val pillX = (x - pillW / 2).coerceIn(0f, (size.width - pillW).coerceAtLeast(0f))
    val above = y - gap - pillH
    val pillY = (if (above >= 0f) above else y + gap).coerceIn(0f, (size.height - pillH).coerceAtLeast(0f))
    // Kleiner Versatz von unten nach oben beim Einblenden.
    val lift = (1f - alpha) * 4.dp.toPx()
    drawRoundRect(
        color = tooltipColor,
        topLeft = Offset(pillX, pillY + lift),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2),
        alpha = alpha,
    )
    drawText(text, topLeft = Offset(pillX + padH, pillY + padV + lift), alpha = alpha)
}

/**
 * Monotone kubische Interpolation (Fritsch–Carlson) durch die Punkte.
 * Zwischen zwei Punkten bleibt die Kurve immer zwischen deren y-Werten – keine erfundenen Spitzen.
 */
internal fun Path.monotoneCubicThrough(points: List<Offset>) {
    if (points.isEmpty()) return
    moveTo(points[0].x, points[0].y)
    val n = points.size
    if (n == 1) return
    if (n == 2) {
        lineTo(points[1].x, points[1].y)
        return
    }
    // Sekantensteigungen
    val slopes = FloatArray(n - 1) { i ->
        val dx = points[i + 1].x - points[i].x
        if (dx > 1e-6f) (points[i + 1].y - points[i].y) / dx else 0f
    }
    // Starttangenten: Mittel der Nachbarsteigungen, 0 an Extremstellen
    val tangents = FloatArray(n) { i ->
        when (i) {
            0 -> slopes[0]
            n - 1 -> slopes[n - 2]
            else -> if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        }
    }
    // Tangenten begrenzen, damit die Kurve monoton bleibt
    for (i in 0 until n - 1) {
        val m = slopes[i]
        if (m == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / m
            val b = tangents[i + 1] / m
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / sqrt(s)
                tangents[i] = tau * a * m
                tangents[i + 1] = tau * b * m
            }
        }
    }
    for (i in 0 until n - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val dx = p1.x - p0.x
        if (dx <= 1e-6f) {
            lineTo(p1.x, p1.y)
            continue
        }
        val h = dx / 3f
        cubicTo(p0.x + h, p0.y + tangents[i] * h, p1.x - h, p1.y - tangents[i + 1] * h, p1.x, p1.y)
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
