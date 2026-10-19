package com.sheuberger.fitness.ui.workouts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.ui.theme.LocalDarkTheme

// Kleine Bausteine, die beide Workout-Screens teilen: das Drei-Punkte-Menü im App-Stil
// und das Ein-/Ausklappen des Floating Action Buttons beim Scrollen.

private val MenuShape = RoundedCornerShape(16.dp)

/**
 * Aufklappmenü im App-Stil: 16 dp runde Ecken, warme Kartenfläche, weicher Schatten statt Farbton.
 * Im hellen Theme mit feiner Kante, damit sich das weiße Menü vom ebenfalls hellen Hintergrund abhebt.
 */
@Composable
internal fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MenuShape,
        // Im Dunkeln eine Stufe heller als die Karten, sonst verschwimmt das Menü mit ihnen.
        containerColor = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = if (dark) null else BorderStroke(1.dp, colors.outlineVariant),
        content = content,
    )
}

/** Eintrag in einem [AppMenu]. [destructive] färbt Text und Icon rot (z. B. "Löschen"). */
@Composable
internal fun AppMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (destructive) colors.error else colors.onSurface
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = tint,
            leadingIconColor = if (destructive) colors.error else colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** Feine Trennlinie im Menü, z. B. vor einem Lösch-Eintrag. Eingerückt, damit sie leicht wirkt. */
@Composable
internal fun AppMenuDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * Merkt sich die Scrollrichtung einer Liste: Beim Herunterscrollen klappt der FAB zum Kreis ein
 * (mehr Platz für den Inhalt), beim Hochscrollen wieder aus. Wird per `Modifier.nestedScroll(...)`
 * über der Liste eingehängt; gezählt wird nur, was die Liste wirklich gescrollt hat. Eine kurze Liste,
 * die gar nicht scrollen kann, lässt den FAB also ausgeklappt.
 */
@Stable
internal class FabScrollState : NestedScrollConnection {
    var expanded by mutableStateOf(true)
        private set

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Negativ = Inhalt wandert nach oben = Nutzer scrollt nach unten.
        if (consumed.y < -1f) expanded = false else if (consumed.y > 1f) expanded = true
        return Offset.Zero
    }
}

@Composable
internal fun rememberFabScrollState(): FabScrollState = remember { FabScrollState() }
