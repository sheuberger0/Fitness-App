package com.sheuberger.fitness.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Dialog: Hintergrund blendet ab, die Karte wächst von 92 % auf 100 % und blendet dabei ein.
// Feder mit dampingRatio 1 = kein Nachschwingen, wirkt ruhig und hochwertig.
private val DialogSpring = spring<Float>(dampingRatio = 1f, stiffness = 520f)
private const val DialogScrimInMillis = 220
private const val DialogCloseMillis = 150
private const val DialogStartScale = 0.92f

/** Zustand eines [AppDialog]. Nur selbst anlegen ([rememberAppDialogState]), wenn von außen geschlossen werden soll. */
@Stable
class AppDialogState internal constructor(private val scope: CoroutineScope) {
    // 0 = unsichtbar, 1 = ganz da.
    internal val progress = Animatable(0f)
    internal val scrim = Animatable(0f)
    internal var onDismissed: () -> Unit = {}

    /** true, sobald der Dialog zugeht. */
    var isClosing by mutableStateOf(false)
        private set

    /** Schließt den Dialog animiert; danach läuft [then] und anschließend das onDismiss des Dialogs. */
    fun dismiss(then: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            coroutineScope {
                launch { scrim.animateTo(0f, tween(DialogCloseMillis + 30, easing = LinearEasing)) }
                progress.animateTo(0f, tween(DialogCloseMillis, easing = FastOutLinearInEasing))
            }
        }.invokeOnCompletion {
            then()
            onDismissed()
        }
    }

    internal suspend fun animateIn() {
        isClosing = false
        coroutineScope {
            launch { scrim.animateTo(1f, tween(DialogScrimInMillis, easing = LinearOutSlowInEasing)) }
            progress.animateTo(1f, DialogSpring)
        }
    }
}

@Composable
fun rememberAppDialogState(): AppDialogState {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppDialogState(scope) }
}

/** Was der Inhalt eines [AppDialog] zusätzlich kann. */
@Stable
interface AppDialogScope {
    /** Schließt den Dialog animiert und führt danach [then] aus (z. B. `dismiss { onConfirm(name) }`). */
    fun dismiss(then: () -> Unit = {})
}

private class AppDialogScopeImpl(private val state: AppDialogState) : AppDialogScope {
    override fun dismiss(then: () -> Unit) = state.dismiss(then)
}

/**
 * Animierter Dialog-Rahmen (ersetzt AlertDialog): 28 dp runde Karte auf warmer Fläche, mittig über
 * abgedunkeltem Hintergrund, bleibt über der Tastatur. Schließt per Zurück-Taste, Tipp daneben oder
 * [AppDialogScope.dismiss]; [onDismiss] kommt erst nach der Schließ-Animation.
 *
 * Der Inhalt liegt in einer Spalte mit 24 dp Innenabstand und scrollt, falls er zu hoch wird.
 * Für den Standardfall (Titel, Text, zwei Buttons) gibt es [AppAlertDialog]; Buttons unten mit [AppDialogButtons].
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: AppDialogState = rememberAppDialogState(),
    dismissOnScrimTap: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable AppDialogScope.() -> Unit,
) {
    SideEffect { state.onDismissed = onDismiss }
    AppOverlay(onBack = { state.dismiss() }) {
        DialogFrame(state, modifier, dismissOnScrimTap, containerColor, content)
    }
}

/**
 * Standard-Dialog: Titel (Serif), optionaler Text, optionaler eigener Inhalt (z. B. ein Textfeld) und
 * unten "Abbrechen" + Bestätigen als gleich breite Pillen. [onConfirm] läuft nach der Schließ-Animation.
 *
 * @param destructive true: Bestätigen-Button in der Fehlerfarbe (für "Löschen").
 */
@Composable
fun AppAlertDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String = "Abbrechen",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable AppDialogScope.() -> Unit)? = null,
) {
    AppDialog(onDismiss = onDismiss, modifier = modifier) {
        AppDialogTitle(title)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content != null) {
            Spacer(Modifier.height(20.dp))
            content()
        }
        Spacer(Modifier.height(24.dp))
        AppDialogButtons(
            confirmLabel = confirmLabel,
            onConfirm = { dismiss(onConfirm) },
            onCancel = { dismiss() },
            cancelLabel = dismissLabel,
            confirmEnabled = confirmEnabled,
            destructive = destructive,
        )
    }
}

/** Titel eines Dialogs in der Serifenschrift. */
@Composable
fun AppDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * Button-Zeile am Ende eines Dialogs: links "Abbrechen" (grau), rechts die Bestätigung (Akzent oder Rot),
 * beide gleich breit. [onConfirm]/[onCancel] werden direkt aufgerufen – im Dialog meist mit `dismiss { ... }`.
 */
@Composable
fun AppDialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Abbrechen",
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        SecondaryButton(text = cancelLabel, onClick = onCancel, compact = true, modifier = Modifier.weight(1f))
        if (destructive) {
            DestructiveButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            PrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DialogFrame(
    state: AppDialogState,
    modifier: Modifier,
    dismissOnScrimTap: Boolean,
    containerColor: Color,
    content: @Composable AppDialogScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state) { state.animateIn() }
    LaunchedEffect(state.isClosing) { if (state.isClosing) focusManager.clearFocus(force = true) }
    BackHandler(enabled = !state.isClosing) { state.dismiss() }

    Box(Modifier.fillMaxSize()) {
        Scrim(
            alpha = { ScrimMaxAlpha * state.scrim.value },
            tapToDismiss = dismissOnScrimTap,
            onTap = { state.dismiss() },
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                // Die Karte bleibt mittig im freien Bereich, also auch über der Tastatur.
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .graphicsLayer {
                        val p = state.progress.value
                        val scale = DialogStartScale + (1f - DialogStartScale) * p
                        scaleX = scale
                        scaleY = scale
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .then(modifier)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    val scope = remember(state) { AppDialogScopeImpl(state) }
                    scope.content()
                }
            }
        }
    }
}
