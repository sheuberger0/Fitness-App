package com.sheuberger.fitness.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Segment-Auswahl für den Zeitraum (1M · 3M · 6M · 1J · Alle), wie man sie aus Börsen- und Health-Apps kennt.
 * Aussehen und Animation kommen aus [PillSegments].
 */
@Composable
fun RangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    PillSegments(
        options = TimeRange.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label },
        modifier = modifier,
    )
}
