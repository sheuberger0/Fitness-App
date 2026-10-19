package com.sheuberger.fitness.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.ui.theme.LocalDarkTheme

// Das Drei-Punkte-Menü im App-Stil. Alle Screens nutzen dieselben Bausteine,
// damit jedes Menü in der App gleich aussieht.

private val MenuShape = RoundedCornerShape(16.dp)

/**
 * Aufklappmenü im App-Stil: 16 dp runde Ecken, warme Kartenfläche, weicher Schatten statt Farbton.
 * Im hellen Theme mit feiner Kante, damit sich das weiße Menü vom ebenfalls hellen Hintergrund abhebt.
 */
@Composable
fun AppMenu(
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
        // 3 dp wie beim FAB: Das Menü hebt sich ab, ohne schweren Schatten.
        shadowElevation = 3.dp,
        border = if (dark) null else BorderStroke(1.dp, colors.outlineVariant),
        content = content,
    )
}

/** Eintrag in einem [AppMenu]. [destructive] färbt Text und Icon rot (z. B. "Löschen"). */
@Composable
fun AppMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = if (destructive) colors.error else colors.onSurface,
            leadingIconColor = if (destructive) colors.error else colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** Feine Trennlinie im Menü, z. B. vor einem Lösch-Eintrag. Eingerückt, damit sie leicht wirkt. */
@Composable
fun AppMenuDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
