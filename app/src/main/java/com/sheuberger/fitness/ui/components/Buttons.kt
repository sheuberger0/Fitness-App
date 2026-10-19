package com.sheuberger.fitness.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ein gemeinsames Button-System für die ganze App. Alle Buttons sind "Pillen" (ganz runde Enden),
// geben beim Drücken leicht nach, färben sich minimal dunkler und lösen ein feines Haptik-Tick aus.
// So fühlt sich jeder Button gleich an, egal auf welchem Screen er sitzt.

private val RegularHeight = 52.dp
private val CompactHeight = 48.dp
private val QuietHeight = 44.dp
private val QuietCompactHeight = 36.dp
private const val ColorMillis = 120

/** Farbton für [QuietButton]: Akzent (Standard), neutral (z. B. "Abbrechen") oder destruktiv ("Löschen"). */
enum class ButtonTone { Accent, Neutral, Destructive }

/** Aussehen von [CircleIconButton]. */
enum class CircleIconButtonStyle {
    /** Warme graue Fläche, z. B. für −/+ oder Schließen. */
    Tonal,

    /** Akzentfarbe, für die wichtigste Aktion (z. B. das "+" zum schnellen Eintragen). */
    Filled,

    /** Ohne Fläche, z. B. für "Zurück" in der Titelleiste. */
    Plain,
}

/**
 * Macht eine Fläche klickbar mit der App-typischen Rückmeldung: Sie gibt beim Drücken nach
 * ([pressedScale]), zeigt einen Ripple innerhalb von [shape] und löst ein leichtes Haptik-Tick aus.
 *
 * Wichtig: Als ERSTES Element nach dem Größen-Modifier verwenden und Hintergründe DANACH anhängen,
 * damit Skalierung und Zuschnitt die ganze Fläche betreffen, z. B.
 * `Modifier.fillMaxWidth().appClickable(onClick, shape = AppCardShape).background(farbe).padding(16.dp)`.
 */
fun Modifier.appClickable(
    onClick: () -> Unit,
    shape: Shape,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
    rippleColor: Color = Color.Unspecified,
    haptic: Boolean = true,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val indication: Indication = ripple(color = rippleColor)
    this
        .pressScale(source, pressedScale)
        .clip(shape)
        .clickable(
            interactionSource = source,
            indication = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
        ) {
            // TextHandleMove ist das feinste Haptik-Signal in dieser Compose-Version (LongPress wäre zu stark).
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * Hauptaktion eines Screens oder Sheets ("Speichern", "Erstellen"): gefüllte Pille in Akzentfarbe.
 * Pro Ansicht möglichst nur einmal verwenden. Für volle Breite `Modifier.fillMaxWidth()` übergeben.
 *
 * @param compact niedrigere Variante (48 statt 52 dp), z. B. in Dialogen.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        compact = compact,
        container = colors.primary,
        pressedContainer = lerp(colors.primary, Color.Black, 0.12f),
        content = colors.onPrimary,
        disabledContainer = colors.surfaceContainerHigh,
        disabledContent = colors.onSurface.copy(alpha = 0.38f),
    )
}

/**
 * Zweite Aktion neben einer [PrimaryButton] oder alleinstehend ("Abbrechen", "Übung hinzufügen" im leeren Zustand):
 * Pille mit warmer grauer Fläche und normaler Schriftfarbe.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        compact = compact,
        container = colors.surfaceContainerHigh,
        pressedContainer = lerp(colors.surfaceContainerHigh, colors.onSurface, 0.08f),
        content = colors.onSurface,
        disabledContainer = colors.surfaceContainer,
        disabledContent = colors.onSurface.copy(alpha = 0.38f),
    )
}

/**
 * Bestätigt eine endgültige Aktion ("Löschen") in einem Dialog: gefüllte Pille in der Fehlerfarbe.
 * Für einen dezenten Lösch-Link (z. B. "Eintrag löschen" unten im Sheet) stattdessen
 * [QuietButton] mit `tone = ButtonTone.Destructive` verwenden.
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        compact = compact,
        container = colors.error,
        pressedContainer = lerp(colors.error, Color.Black, 0.12f),
        content = colors.onError,
        disabledContainer = colors.surfaceContainerHigh,
        disabledContent = colors.onSurface.copy(alpha = 0.38f),
    )
}

/**
 * Text-Button ohne Fläche für Nebenaktionen ("Fertig" in einer Kopfzeile, "Eintrag löschen", "Abbrechen").
 * Beim Drücken erscheint eine zarte Pille in der Textfarbe.
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.Accent,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val content = when (tone) {
        ButtonTone.Accent -> colors.primary
        ButtonTone.Neutral -> colors.onSurfaceVariant
        ButtonTone.Destructive -> colors.error
    }
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        compact = compact,
        container = Color.Transparent,
        pressedContainer = content.copy(alpha = 0.08f),
        content = content,
        disabledContainer = Color.Transparent,
        disabledContent = colors.onSurface.copy(alpha = 0.38f),
        height = if (compact) QuietCompactHeight else QuietHeight,
        horizontalPadding = if (compact) 12.dp else 16.dp,
        pressedScale = 0.97f,
    )
}

/**
 * Runder Icon-Button (44 dp) für −/+, Schließen, Zurück oder das "+" zum schnellen Eintragen.
 * Gibt beim Drücken deutlich nach (0,9), damit sich auch kleine Buttons lebendig anfühlen.
 *
 * @param contentDescription Text für TalkBack, z. B. "Mehr" – nur null, wenn daneben schon ein Text steht.
 * @param size Durchmesser; z. B. 52.dp, wenn der Button neben einem Eingabefeld sitzt.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CircleIconButtonStyle = CircleIconButtonStyle.Tonal,
    enabled: Boolean = true,
    size: Dp = 44.dp,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (style) {
        CircleIconButtonStyle.Tonal -> colors.surfaceContainerHigh to colors.onSurface
        CircleIconButtonStyle.Filled -> colors.primary to colors.onPrimary
        CircleIconButtonStyle.Plain -> Color.Transparent to colors.onSurface
    }
    val pressed = when (style) {
        CircleIconButtonStyle.Filled -> lerp(container, Color.Black, 0.12f)
        CircleIconButtonStyle.Tonal -> lerp(container, colors.onSurface, 0.08f)
        CircleIconButtonStyle.Plain -> colors.onSurface.copy(alpha = 0.08f)
    }
    val disabledContainer = if (style == CircleIconButtonStyle.Plain) Color.Transparent else colors.surfaceContainer
    val interaction = remember { MutableInteractionSource() }
    val background = animatedFill(interaction, enabled, container, pressed, disabledContainer)
    val tint by animateColorAsState(
        targetValue = if (enabled) content else colors.onSurface.copy(alpha = 0.38f),
        animationSpec = tween(ColorMillis),
        label = "circleTint",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .appClickable(
                onClick = onClick,
                shape = CircleShape,
                enabled = enabled,
                pressedScale = 0.9f,
                rippleColor = content,
                interactionSource = interaction,
            )
            .drawBehind { drawRect(background.value) },
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Schwebender Haupt-Button eines Screens: Pille in Akzentfarbe mit Icon und Text und weichem Schatten.
 * Mit `expanded = false` schrumpft er animiert zu einem Kreis (z. B. beim Scrollen).
 * Für sanftes Ein-/Ausblenden in [AnimatedFab] einpacken.
 */
@Composable
fun AppFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val background = animatedFill(
        interaction = interaction,
        enabled = true,
        container = colors.primary,
        pressed = lerp(colors.primary, Color.Black, 0.12f),
        disabled = colors.primary,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // Reihenfolge: erst skalieren (inkl. Schatten), dann Schatten, dann zuschneiden und füllen.
            .pressScale(interaction, 0.95f)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .drawBehind { drawRect(background.value) }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .height(56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            // Eingeklappt fehlt der Text, dann muss das Icon selbst beschrieben sein.
            contentDescription = if (expanded) null else text,
            tint = colors.onPrimary,
            modifier = Modifier.size(24.dp),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160, delayMillis = 60)) +
                expandHorizontally(spring(dampingRatio = 1f, stiffness = 500f), expandFrom = Alignment.Start),
            exit = fadeOut(tween(90)) +
                shrinkHorizontally(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
        ) {
            Row {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = text,
                    style = buttonTextStyle(compact = false),
                    color = colors.onPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/**
 * Filter-Chip als Pille: warme graue Fläche, ausgewählt in zartem Akzent. Passt optisch zur [RangeSelector].
 */
@Composable
fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = tween(180),
        label = "chipContent",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .semantics { this.selected = selected }
            .appClickable(onClick = onClick, shape = CircleShape, pressedScale = 0.95f, role = Role.Checkbox)
            .drawBehind { drawRect(container) }
            .height(36.dp)
            .padding(horizontal = 14.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

// --- intern ---

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: ImageVector?,
    compact: Boolean,
    container: Color,
    pressedContainer: Color,
    content: Color,
    disabledContainer: Color,
    disabledContent: Color,
    height: Dp = if (compact) CompactHeight else RegularHeight,
    horizontalPadding: Dp = if (compact) 20.dp else 24.dp,
    pressedScale: Float = 0.96f,
) {
    val interaction = remember { MutableInteractionSource() }
    val background = animatedFill(interaction, enabled, container, pressedContainer, disabledContainer)
    val contentColor by animateColorAsState(
        targetValue = if (enabled) content else disabledContent,
        animationSpec = tween(ColorMillis),
        label = "buttonContent",
    )
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .appClickable(
                onClick = onClick,
                shape = CircleShape,
                enabled = enabled,
                pressedScale = pressedScale,
                rippleColor = content,
                interactionSource = interaction,
            )
            .drawBehind { drawRect(background.value) }
            .heightIn(min = height)
            .padding(horizontal = horizontalPadding),
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = buttonTextStyle(compact),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Hintergrundfarbe, die beim Drücken kurz dunkler wird. Als State zurückgegeben und erst beim Zeichnen
 * gelesen (drawBehind): So muss Compose während der Farb-Animation nichts neu aufbauen, nur neu zeichnen.
 */
@Composable
private fun animatedFill(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    container: Color,
    pressed: Color,
    disabled: Color,
): State<Color> {
    val isPressed by interaction.collectIsPressedAsState()
    return animateColorAsState(
        targetValue = when {
            !enabled -> disabled
            isPressed -> pressed
            else -> container
        },
        animationSpec = tween(ColorMillis),
        label = "buttonFill",
    )
}

@Composable
private fun buttonTextStyle(compact: Boolean): TextStyle =
    MaterialTheme.typography.labelLarge.copy(
        fontSize = if (compact) 15.sp else 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )
