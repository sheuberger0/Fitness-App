package com.sheuberger.fitness.ui.nutrition

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.components.appTextFieldColors
import com.sheuberger.fitness.ui.components.pressScale
import kotlin.math.max
import kotlin.math.roundToInt

// Kleine Bausteine für den Kalorien-Tracker. Sie sehen aus wie die gemeinsamen Bausteine in ui/components
// und sind nur dort neu, wo es für den Tracker etwas Eigenes braucht.

/**
 * Segment-Auswahl wie [com.sheuberger.fitness.ui.components.PillSegments], aber jedes Segment ist so breit
 * wie sein Text plus gleich viel Luft. Grund: "Lebensmittel" oder "Frühstück" passen in vier GLEICH breite
 * Segmente auf einem 360-dp-Handy nicht ganz hinein. Aussehen, Farben und Bewegung sind dieselben.
 */
@Composable
internal fun <T> FitSegments(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // Mit der kräftigeren Schrift der Auswahl gemessen, damit beim Wechsel nichts zu eng wird.
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val labels = options.map(label)
    val textWidths = remember(labels, style, density) {
        labels.map { with(density) { measurer.measure(it, style, maxLines = 1).size.width.toDp() } }
    }
    BoxWithConstraints(
        modifier = modifier
            // Sichtbar 36 dp wie PillSegments, als Tippfläche aber 48 dp hoch.
            .minimumInteractiveComponentSize()
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        val free = maxWidth - textWidths.fold(0.dp) { sum, width -> sum + width }
        // Reicht der Platz nicht einmal für die Texte (sehr große Schrift), werden alle Segmente gleich breit.
        val widths = if (free >= 0.dp) {
            textWidths.map { it + free / options.size }
        } else {
            List(options.size) { maxWidth / options.size }
        }
        val index = options.indexOf(selected).coerceAtLeast(0)
        val pillSpec = spring<Dp>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
        val pillOffset by animateDpAsState(
            targetValue = widths.take(index).fold(0.dp) { sum, width -> sum + width },
            animationSpec = pillSpec,
            label = "segmentPill",
        )
        val pillWidth by animateDpAsState(widths[index], pillSpec, label = "segmentPillWidth")
        Box(
            Modifier
                .fillMaxSize()
                // Lage und Breite der Pille werden erst beim Layout gelesen: Die Animation baut nichts neu auf.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(pillWidth.roundToPx(), constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(pillOffset.roundToPx(), 0) }
                }
                .clip(CircleShape)
                .background(colors.primaryContainer),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "segmentText",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(widths[i])
                        .fillMaxHeight()
                        .semantics { this.selected = isSelected }
                        .pressScale(interaction, 0.94f)
                        .clip(CircleShape)
                        // Kein Ripple: Die gleitende Pille ist schon die Rückmeldung.
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(option)
                            }
                        },
                ) {
                    Text(
                        text = labels[i],
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Großes Zahlenfeld mit − und + daneben, wie im Gewichts-Sheet. [suffix] steht rechts im Feld (z. B. "g");
 * ein unsichtbares Gegenstück links hält die Zahl in der Mitte.
 */
@Composable
internal fun AmountStepper(
    text: String,
    onTextChange: (String) -> Unit,
    isError: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        CircleIconButton(icon = Icons.Filled.Remove, contentDescription = "Weniger", onClick = onDecrement, size = 56.dp)
        TextField(
            value = text,
            onValueChange = { onTextChange(NutritionFormat.decimalInput(it)) },
            singleLine = true,
            isError = isError,
            // Zeilenhöhe 24 sp: So ist das Feld genau 56 dp hoch wie die runden Buttons daneben.
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, lineHeight = 24.sp),
            prefix = suffix?.let { { Text(it, color = Color.Transparent, modifier = Modifier.clearAndSetSemantics {}) } },
            suffix = suffix?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            shape = AppFieldShape,
            colors = appTextFieldColors(),
            modifier = Modifier.weight(1f),
        )
        CircleIconButton(icon = Icons.Filled.Add, contentDescription = "Mehr", onClick = onIncrement, size = 56.dp)
    }
}

/** Zahl mit − und + auf einer warmen Fläche, wie das Budget in den Mensa-Einstellungen. */
@Composable
internal fun GoalStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    decrementLabel: String,
    incrementLabel: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppFieldShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(6.dp),
    ) {
        CircleIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = decrementLabel,
            onClick = onDecrement,
            enabled = decrementEnabled,
            size = 48.dp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        CircleIconButton(
            icon = Icons.Filled.Add,
            contentDescription = incrementLabel,
            onClick = onIncrement,
            enabled = incrementEnabled,
            size = 48.dp,
        )
    }
}

/** Fortschrittsbalken mit runden Enden. Der Wert gleitet weich zum neuen Stand. */
@Composable
internal fun GoalBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, tween(420, easing = FastOutSlowInEasing), label = "goalBar")
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .progressSemantics(target)
            // Der animierte Wert wird erst beim Zeichnen gelesen: Die Animation zeichnet nur neu.
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                if (animated > 0f) {
                    drawRoundRect(
                        color = color,
                        // Mindestens ein Kreis, damit ein kleiner Anfang nicht als Strich erscheint.
                        size = Size(max(size.width * animated, size.height), size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

/**
 * Ganze Zahl, die beim Wechsel weich hoch- oder runterzählt, wie [com.sheuberger.fitness.ui.components.AnimatedNumber].
 * Eine eigene Variante, weil Kalorien im Tracker ohne Tausenderpunkt stehen ("3800 kcal", siehe [NutritionFormat]).
 * AnimatedNumber würde "3.800" schreiben, und dann stünden in derselben Karte zwei Schreibweisen.
 */
@Composable
internal fun AnimatedCount(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val animated = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) { animated.animateTo(value.toFloat(), tween(420, easing = FastOutSlowInEasing)) }
    Text(text = animated.value.roundToInt().toString(), style = style, color = color, modifier = modifier, maxLines = 1)
}

/** Kleine Pille "passt": Eine Portion liegt unter dem, was heute noch bis zum Ziel fehlt. */
@Composable
internal fun FitsBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = "passt",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = colors.onPrimaryContainer,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Zahlenfeld mit Beschriftung, z. B. "Protein … g". Komma und Punkt sind erlaubt. */
@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    TextField(
        value = value,
        onValueChange = { onValueChange(NutritionFormat.decimalInput(it)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        shape = AppFieldShape,
        colors = appTextFieldColors(),
        modifier = modifier,
    )
}

/** Textfeld mit Beschriftung, z. B. für Name und Marke. */
@Composable
internal fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
) {
    TextField(
        value = value,
        onValueChange = { onValueChange(it.take(80)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        shape = AppFieldShape,
        colors = appTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Antippbare Zeile in einer Liste im Sheet: Titel und Untertitel links, Werte rechts.
 * Außen 12 dp + innen 12 dp, damit der Text bündig mit dem Sheet-Titel (24 dp) steht, wie in der Übungsauswahl.
 */
@Composable
internal fun SheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    trailingSub: String? = null,
    titleIcon: ImageVector? = null,
    onClickLabel: String = "Eintragen",
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .appClickable(onClick = onClick, shape = AppFieldShape, pressedScale = 0.98f, onClickLabel = onClickLabel)
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (titleIcon != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(titleIcon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(trailing, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1)
                if (trailingSub != null) {
                    Text(trailingSub, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Zeile "Neues Lebensmittel" mit gefülltem Akzent-Kreis, wie "Neue Übung anlegen" in der Übungsauswahl.
 * [icon] steht im Kreis, z. B. ein Scanner-Symbol für "Barcode scannen".
 */
@Composable
internal fun CreateRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
) {
    val fill = com.sheuberger.fitness.ui.theme.LocalAccentFill.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .appClickable(onClick = onClick, shape = AppFieldShape, pressedScale = 0.98f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(fill.container, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = fill.content, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Ruhiger Hinweis mitten in einer Liste, optional mit einer Aktion darunter. */
@Composable
internal fun ListMessage(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(8.dp))
            QuietButton(actionLabel, onClick = onAction)
        }
    }
}

/** Ruhige Platzhalter, die leicht pulsieren, solange etwas lädt (wie in der Mensa-Rangliste). */
@Composable
internal fun LoadingRows(count: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(AppFieldShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}

private val SnackbarShape = RoundedCornerShape(16.dp)

/**
 * Snackbar im App-Stil, genau wie im Tab "Körpergewicht": warme "umgekehrte" Fläche und die Aktion als
 * Pillen-Button. Sie gleitet beim Erscheinen ein kleines Stück nach oben.
 */
@Composable
internal fun NutritionSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    val rise = remember(data) { Animatable(0f) }
    LaunchedEffect(data) { rise.animateTo(1f, spring(dampingRatio = 1f, stiffness = 500f)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .graphicsLayer { translationY = (1f - rise.value) * 16.dp.toPx() }
            .shadow(
                elevation = 3.dp,
                shape = SnackbarShape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .clip(SnackbarShape)
            .background(colors.inverseSurface)
            .heightIn(min = 52.dp)
            .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inverseOnSurface,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        val actionLabel = data.visuals.actionLabel
        if (actionLabel != null) {
            // Auf der umgekehrten Fläche braucht der Akzent die "inverse" Variante, sonst ist er zu blass.
            MaterialTheme(colorScheme = colors.copy(primary = colors.inversePrimary)) {
                QuietButton(text = actionLabel, onClick = { data.performAction() }, compact = true)
            }
        }
    }
}
