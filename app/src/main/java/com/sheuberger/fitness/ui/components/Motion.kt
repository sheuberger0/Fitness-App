package com.sheuberger.fitness.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

// Gemeinsame Bewegungen: kurz und ruhig (150–350 ms), wie in Apple Health oder Hevy.

private const val PressInMillis = 100
private const val EntranceMillis = 250
private const val EntranceStaggerMillis = 40
private const val EntranceMaxItems = 5
// Nach diesem Zeitfenster laufen keine Eingangsanimationen mehr (z. B. beim Zurückscrollen).
private const val EntranceWindowMillis = EntranceMaxItems * EntranceStaggerMillis + EntranceMillis + 150

/**
 * Karte gibt beim Drücken leicht nach (wie in iOS). Dieselbe [interactionSource] muss an
 * clickable/Surface gehen, damit Ripple und Skalierung zusammen laufen.
 * Auch bei einem kurzen Tippen ist das Nachgeben sichtbar: Erst nach dem Eindrücken federt die Karte zurück.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        var pressJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressJob?.cancel()
                    pressJob = launch {
                        scale.animateTo(pressedScale, tween(PressInMillis, easing = FastOutSlowInEasing))
                    }
                }
                is PressInteraction.Release -> {
                    val job = pressJob
                    launch {
                        job?.join()
                        scale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 600f))
                    }
                }
                is PressInteraction.Cancel -> {
                    pressJob?.cancel()
                    launch { scale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 600f)) }
                }
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Große Zahl, die beim Wechsel zum neuen Wert hoch- oder runterzählt.
 * [instant] = true (beim Scrubben im Graphen): sofort springen, damit nichts nachhinkt.
 */
@Composable
fun AnimatedNumber(
    value: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    instant: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val animated = remember { Animatable(value.toFloat()) }
    // Nachkommastellen während des Zählens: so viele wie der genauere der beiden Werte, damit nichts flackert.
    var decimals by remember { mutableIntStateOf(decimalsOf(value)) }

    LaunchedEffect(value) {
        if (abs(animated.value - value.toFloat()) < 0.001f) return@LaunchedEffect
        if (instant) {
            animated.snapTo(value.toFloat())
        } else {
            decimals = max(decimalsOf(value), decimalsOf(animated.value.toDouble()))
            animated.animateTo(value.toFloat(), tween(420, easing = FastOutSlowInEasing))
        }
    }

    val text = if (animated.isRunning) fixed(animated.value.toDouble(), decimals) else Format.number(value)
    androidx.compose.material3.Text(text = text, style = style, color = color, modifier = modifier, maxLines = 1)
}

private fun decimalsOf(value: Double): Int {
    val hundredths = (value * 100).roundToLong()
    return when {
        hundredths % 100 == 0L -> 0
        hundredths % 10 == 0L -> 1
        else -> 2
    }
}

private val fixedFormats = (0..2).map { d ->
    DecimalFormat(if (d == 0) "#,##0" else "#,##0." + "0".repeat(d), DecimalFormatSymbols(Locale.GERMANY))
}

private fun fixed(value: Double, decimals: Int): String = fixedFormats[decimals.coerceIn(0, 2)].format(value)

/**
 * Text-Zeilen, die beim Wechsel des Modus (z. B. Auswahl im Graphen, anderer Zeitraum) überblenden.
 * Ändert sich nur der Wert bei gleichem [contentKey], wird ohne Überblendung aktualisiert.
 */
@Composable
fun <T> FadeSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.TopStart,
        contentKey = contentKey,
        label = "fadeSwap",
    ) { content(it) }
}

/** Weicher Wechsel zwischen Laden, leerem Zustand und Inhalt statt hartem Aufploppen. */
@Composable
fun <T> StateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Crossfade(targetState = targetState, modifier = modifier, animationSpec = tween(250), label = "state") {
        content(it)
    }
}

/** Floating Action Button erscheint und verschwindet mit leichtem Zoom statt zu springen. */
@Composable
fun AnimatedFab(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.8f) + fadeIn(tween(180)),
        exit = scaleOut(tween(160), targetScale = 0.8f) + fadeOut(tween(140)),
    ) { content() }
}

/**
 * Merkt sich, ob die Eingangsanimation eines Screens schon gelaufen ist.
 * Überlebt Tab-Wechsel und Zurück-Navigation, damit sie nur beim ersten Erscheinen läuft.
 */
@Stable
class Entrance internal constructor(private val done: Boolean) {
    private var startedAt = -1L

    internal fun shouldAnimate(index: Int): Boolean {
        if (done || index >= EntranceMaxItems) return false
        val now = SystemClock.uptimeMillis()
        if (startedAt < 0) startedAt = now
        return now - startedAt < EntranceWindowMillis
    }

    internal companion object {
        val Saver = Saver<Entrance, Boolean>(
            save = { it.done || it.startedAt >= 0 },
            restore = { Entrance(done = it) },
        )
    }
}

@Composable
fun rememberEntrance(): Entrance = rememberSaveable(saver = Entrance.Saver) { Entrance(done = false) }

/** Inhalt gleitet beim ersten Erscheinen leicht nach oben und blendet ein, leicht versetzt pro Element. */
fun Modifier.entrance(entrance: Entrance, index: Int): Modifier = composed {
    val animate = remember { entrance.shouldAnimate(index) }
    if (!animate) return@composed Modifier
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * EntranceStaggerMillis.toLong())
        progress.animateTo(1f, tween(EntranceMillis, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 10.dp.toPx()
    }
}
