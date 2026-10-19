package com.sheuberger.fitness.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Segment-Auswahl für den Zeitraum (1M · 3M · 6M · 1J · Alle), wie man sie aus Börsen- und Health-Apps kennt. */
@Composable
fun RangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    // Im hellen Theme hebt sich Weiß am besten ab, im dunklen ein hellerer Grauton.
    val selectedColor = if (colors.background.luminance() < 0.5f) colors.surfaceContainerHighest else colors.surfaceContainerLowest
    val ranges = TimeRange.entries
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceContainer)
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
                .offset(x = pillOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(selectedColor),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            ranges.forEach { range ->
                val isSelected = range == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "rangeText",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Tab) {
                            if (range != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(range)
                            }
                        },
                ) {
                    Text(
                        text = range.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                    )
                }
            }
        }
    }
}
