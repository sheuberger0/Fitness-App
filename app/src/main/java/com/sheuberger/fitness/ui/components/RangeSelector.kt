package com.sheuberger.fitness.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Segment-Auswahl für den Zeitraum (1M · 3M · 6M · 1J · Alle), wie man sie aus Börsen- und Health-Apps kennt.
 * Warme Pillen-Leiste; die Auswahl ist eine zart eingefärbte Akzent-Pille, die zum gewählten Segment gleitet.
 */
@Composable
fun RangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val ranges = TimeRange.entries
    BoxWithConstraints(
        modifier = modifier
            // Sichtbar 36 dp (wie die Filter-Chips), reserviert aber 48 dp Höhe als Tippfläche.
            .minimumInteractiveComponentSize()
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / ranges.size
        // Die Pille gleitet zum gewählten Segment – gedämpfte Feder, kein Nachschwingen.
        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * ranges.indexOf(selected),
            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            label = "rangePill",
        )
        Box(
            modifier = Modifier
                // Lambda-Variante: Der Wert wird erst beim Layout gelesen, die Animation baut nichts neu auf.
                .offset { IntOffset(pillOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.primaryContainer),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            ranges.forEach { range ->
                val isSelected = range == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "rangeText",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { this.selected = isSelected }
                        .pressScale(interaction, 0.94f)
                        .clip(CircleShape)
                        // Kein Ripple: Die gleitende Pille ist schon die Rückmeldung.
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                            if (range != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(range)
                            }
                        },
                ) {
                    Text(
                        text = range.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = textColor,
                    )
                }
            }
        }
    }
}
