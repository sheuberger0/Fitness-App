package com.sheuberger.fitness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.ui.theme.LocalDarkTheme
import com.sheuberger.fitness.ui.theme.LocalTrendColors
import com.sheuberger.fitness.ui.theme.SectionLabelStyle

/** Innenabstand des Scaffolds plus eigener Rand, unten Platz für den Floating Action Button. */
@Composable
fun PaddingValues.withContent(
    horizontal: Dp = 16.dp,
    top: Dp = 8.dp,
    bottom: Dp = 104.dp,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + horizontal,
        end = calculateEndPadding(direction) + horizontal,
        top = calculateTopPadding() + top,
        bottom = calculateBottomPadding() + bottom,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Kleine Kennzahl, z. B. "Bestwert  100 kg". */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1)
    }
}

/** Farbe für eine Veränderung: grün bei Steigerung, rot bei Rückgang. */
@Composable
fun trendColor(delta: Double, neutral: Boolean = false): Color {
    val trend = LocalTrendColors.current
    return when {
        neutral || kotlin.math.abs(delta) < 0.005 -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0 -> trend.positive
        else -> trend.negative
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

// --- Karten und Eingabefelder im gemeinsamen Stil ---

/** Ecken aller Karten (20 dp, weich wie in der Claude-App). */
val AppCardShape = RoundedCornerShape(20.dp)

/** Ecken von Eingabefeldern und Flächen innerhalb von Karten/Sheets. */
val AppFieldShape = RoundedCornerShape(16.dp)

/**
 * Karte im App-Stil: Fläche `surfaceContainerLow`, 20 dp Ecken, kein Schatten. Im hellen Theme mit
 * feiner Kante, damit sich Weiß vom warmen Hintergrund abhebt. Mit [onClick] gibt sie beim Drücken leicht nach.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hairline = !LocalDarkTheme.current
    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.appClickable(
                        onClick = onClick,
                        shape = AppCardShape,
                        pressedScale = 0.98f,
                        haptic = false,
                        onClickLabel = onClickLabel,
                    )
                } else {
                    Modifier.clip(AppCardShape)
                },
            )
            .background(colors.surfaceContainerLow)
            .then(if (hairline) Modifier.border(1.dp, colors.outlineVariant, AppCardShape) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Farben für Material-[TextField]s im App-Stil: warme Fläche, keine Unterstreichung, Cursor in Akzentfarbe.
 * Zusammen mit `shape = AppFieldShape` verwenden.
 */
@Composable
fun appTextFieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = c.surfaceContainerHigh,
        unfocusedContainerColor = c.surfaceContainerHigh,
        disabledContainerColor = c.surfaceContainer,
        errorContainerColor = c.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = c.primary,
        errorCursorColor = c.error,
        focusedPlaceholderColor = c.onSurfaceVariant,
        unfocusedPlaceholderColor = c.onSurfaceVariant,
        focusedLeadingIconColor = c.onSurfaceVariant,
        unfocusedLeadingIconColor = c.onSurfaceVariant,
        focusedTrailingIconColor = c.onSurfaceVariant,
        unfocusedTrailingIconColor = c.onSurfaceVariant,
        focusedSuffixColor = c.onSurfaceVariant,
        unfocusedSuffixColor = c.onSurfaceVariant,
    )
}

// --- Dialoge (Signaturen unverändert, intern jetzt mit dem animierten AppDialog) ---

/** Dialog mit einem Textfeld, z. B. zum Benennen eines Workouts. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    placeholder: String = "",
    validate: (String) -> String? = { null },
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val trimmed = value.text.trim()
    val error = if (trimmed.isEmpty()) null else validate(trimmed)
    val valid = trimmed.isNotEmpty() && error == null

    AppAlertDialog(
        title = title,
        confirmLabel = confirmLabel,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(trimmed) },
        confirmEnabled = valid,
    ) {
        val focusRequester = remember { FocusRequester() }
        TextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            // "Fertig" auf der Tastatur bestätigt direkt.
            keyboardActions = KeyboardActions(onDone = { if (valid) dismiss { onConfirm(trimmed) } }),
            shape = AppFieldShape,
            colors = appTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        // Hier im Inhalt (nicht außerhalb), weil der Dialog-Inhalt erst einen Frame später erscheint.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

/** Sicherheitsabfrage vor dem Löschen. [onConfirm] läuft, nachdem der Dialog animiert geschlossen ist. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlertDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        destructive = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
