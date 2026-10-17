package com.sheuberger.fitness.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/** Mini-Graph ohne Achsen, zeigt nur die Tendenz der letzten Einträge. Zeichnet sich beim Erscheinen kurz ein. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val inset = 3.dp.toPx()
        val stepX = (size.width - 2 * inset) / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            val normalized = if (max - min < 1e-9) 0.5f else ((v - min) / range).toFloat()
            Offset(inset + i * stepX, size.height - inset - normalized * (size.height - 2 * inset))
        }
        val path = Path().apply { monotoneCubicThrough(points) }
        val p = progress.value
        clipRect(right = size.width * p) {
            drawPath(path, color, style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Endpunkt erscheint, sobald die Linie fast angekommen ist.
        val dotAlpha = ((p - 0.85f) / 0.15f).coerceIn(0f, 1f)
        if (dotAlpha > 0f) drawCircle(color, radius = 2.5.dp.toPx(), center = points.last(), alpha = dotAlpha)
    }
}
