package com.sheuberger.fitness.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.theme.AccentFill
import com.sheuberger.fitness.ui.theme.AccentLight
import com.sheuberger.fitness.ui.theme.AppColorScheme
import com.sheuberger.fitness.ui.theme.LightBackground
import com.sheuberger.fitness.ui.theme.LightCard
import com.sheuberger.fitness.ui.theme.LightContainerHighest
import com.sheuberger.fitness.ui.theme.LightOnSurface

/**
 * Einstellungen (Zahnrad oben rechts im Workouts-Screen). Im Moment nur die Wahl des Farbschemas.
 *
 * Ein Tipp auf eine Kachel wechselt sofort: die ganze App, dieses Sheet eingeschlossen, gleitet zu den neuen
 * Farben. So kann man mehrere Schemata nacheinander ausprobieren, ohne das Sheet zu schließen.
 */
@Composable
fun SettingsSheet(
    current: AppColorScheme,
    onSelect: (AppColorScheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = "Einstellungen",
            subtitle = current.title,
            action = { QuietButton("Fertig", onClick = { dismiss() }) },
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            SectionLabel("Farbschema")
            Spacer(Modifier.height(12.dp))
            ColorSchemeGrid(current = current, onSelect = onSelect)
            Spacer(Modifier.height(16.dp))
            Text(
                "Terrakotta ist das Original und wird hell oder dunkel wie dein Handy. " +
                    "Alle anderen Farbschemata sind immer dunkel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val Columns = 3

@Composable
private fun ColorSchemeGrid(current: AppColorScheme, onSelect: (AppColorScheme) -> Unit) {
    // selectableGroup: TalkBack liest die Kacheln als zusammengehörige Auswahl vor ("1 von 18").
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.selectableGroup()) {
        AppColorScheme.entries.chunked(Columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { scheme ->
                    SchemeTile(
                        scheme = scheme,
                        selected = scheme == current,
                        onClick = { onSelect(scheme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(Columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(18.dp)
private val PreviewShape = RoundedCornerShape(14.dp)

/** Kachel mit einer Mini-Vorschau des Schemas: Hintergrund, Karte mit Verlaufslinie und Button. */
@Composable
private fun SchemeTile(
    scheme: AppColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ring by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "Auswahlring",
    )
    val check by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(200),
        label = "Haken",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .appClickable(onClick = onClick, shape = TileShape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .border(2.dp, ring, TileShape)
                .padding(4.dp)
                .clip(PreviewShape)
                .drawBehind { drawMiniPreview(scheme) },
        ) {
            if (check > 0f) {
                val p = scheme.dark
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .scale(0.6f + 0.4f * check)
                        .clip(CircleShape)
                        .background(p.accentFill.copy(alpha = check)),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = p.onAccentFill.copy(alpha = check),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = scheme.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            // Abstand zum Rand: Die runden Ecken der Tippfläche würden sonst den ersten oder letzten Buchstaben abschneiden.
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** Die wenigen Farben, die die Mini-Vorschau braucht. */
private class PreviewColors(
    val background: Color,
    val card: Color,
    val title: Color,
    val accent: Color,
    val fill: Color,
)

private fun AppColorScheme.previewDark() = PreviewColors(
    background = dark.background,
    card = dark.containerHigh,
    title = dark.onSurface,
    accent = dark.accent,
    fill = dark.accentFill,
)

// Helle Variante des Originals (Werte aus Color.kt).
private val TerrakottaLight = PreviewColors(
    background = LightBackground,
    card = LightContainerHighest,
    title = LightOnSurface,
    accent = AccentLight,
    fill = AccentFill,
)

/**
 * Zeichnet eine winzige App: Titelzeile, darunter eine Karte mit Verlaufslinie im Akzent und einem Button in der
 * Füllfarbe. Beim Original ist die Kachel schräg geteilt: oben links hell, unten rechts dunkel.
 */
private fun DrawScope.drawMiniPreview(scheme: AppColorScheme) {
    drawPreview(scheme.previewDark())
    if (scheme.followsSystem) {
        val split = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(0f, size.height)
            close()
        }
        clipPath(split) { drawPreview(TerrakottaLight) }
    }
}

private fun DrawScope.drawPreview(c: PreviewColors) {
    drawRect(c.background)
    val unit = 1.dp.toPx()
    // Titelzeile
    drawRoundRect(
        color = c.title.copy(alpha = 0.85f),
        topLeft = Offset(9 * unit, 9 * unit),
        size = Size(size.width * 0.4f, 5 * unit),
        cornerRadius = CornerRadius(2.5f * unit),
    )
    // Karte
    val cardTop = 21 * unit
    val cardLeft = 6 * unit
    val cardRight = size.width - 6 * unit
    val cardBottom = size.height - 6 * unit
    drawRoundRect(
        color = c.card,
        topLeft = Offset(cardLeft, cardTop),
        size = Size(cardRight - cardLeft, cardBottom - cardTop),
        cornerRadius = CornerRadius(8 * unit),
    )
    // Verlaufslinie: steigt mit kleinen Wellen nach rechts oben
    val lineLeft = cardLeft + 7 * unit
    val lineRight = cardLeft + (cardRight - cardLeft) * 0.58f
    val low = cardBottom - 8 * unit
    val high = cardTop + 8 * unit
    val steps = listOf(0f, 0.35f, 0.25f, 0.6f, 0.5f, 0.9f)
    val line = Path()
    steps.forEachIndexed { i, rise ->
        val x = lineLeft + (lineRight - lineLeft) * i / (steps.size - 1)
        val y = low - (low - high) * rise
        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    drawPath(line, c.accent, style = Stroke(width = 2 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Button
    val pillW = 20 * unit
    val pillH = 11 * unit
    drawRoundRect(
        color = c.fill,
        topLeft = Offset(cardRight - 7 * unit - pillW, cardBottom - 7 * unit - pillH),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2),
    )
}
