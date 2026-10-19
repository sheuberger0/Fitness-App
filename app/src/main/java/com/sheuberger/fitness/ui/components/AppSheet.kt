package com.sheuberger.fitness.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================================
// Overlay-Ebene
// Sheets und Dialoge werden nicht in einem eigenen System-Fenster gezeichnet, sondern in einer
// Ebene über der ganzen App. Vorteil: Wir steuern jede Animation selbst (kein Aufblitzen des
// System-Abdunkelns), die Statusleiste wird nur leicht abgedunkelt statt farbig überdeckt,
// und die Tastatur verhält sich wie überall sonst in der App (Edge-to-Edge + WindowInsets.ime).
// =====================================================================================

/** Hintergrund-Abdunkelung hinter Sheets und Dialogen (32 % Schwarz). */
internal const val ScrimMaxAlpha = 0.32f

@Stable
internal class OverlayHost {
    val entries = mutableStateListOf<OverlayEntry>()
}

internal class OverlayEntry(content: @Composable () -> Unit) {
    var content: @Composable () -> Unit by mutableStateOf(content)
}

internal val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

/**
 * Ebene über der ganzen App (auch über der Navigationsleiste), in der [AppSheet] und [AppDialog]
 * erscheinen. Wird einmal ganz außen um den Inhalt der App gelegt (siehe FitnessAppRoot).
 */
@Composable
fun AppOverlayHost(content: @Composable () -> Unit) {
    val host = remember { OverlayHost() }
    CompositionLocalProvider(LocalOverlayHost provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            OverlayEntries(host)
        }
    }
}

// Eigene Funktion, damit beim Öffnen/Schließen nur diese Ebene neu aufgebaut wird, nicht die ganze App.
@Composable
private fun OverlayEntries(host: OverlayHost) {
    // Später geöffnete Ebenen liegen oben (z. B. ein Dialog über einem Sheet).
    host.entries.forEach { entry -> key(entry) { entry.content() } }
}

/**
 * Zeigt [content] bildschirmfüllend über der App an, solange dieser Aufruf Teil der Oberfläche ist.
 * Ohne [AppOverlayHost] (z. B. in einer Vorschau) wird ersatzweise ein Vollbild-Dialog benutzt.
 */
@Composable
internal fun AppOverlay(onBack: () -> Unit, content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    if (host == null) {
        Dialog(
            onDismissRequest = onBack,
            properties = DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
            content = content,
        )
        return
    }
    val entry = remember { OverlayEntry(content) }
    // Nach jedem Neuaufbau den aktuellen Inhalt weitergeben (er kann neue Werte enthalten).
    SideEffect { entry.content = content }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/**
 * Abgedunkelter Hintergrund. [alpha] wird erst beim Zeichnen gelesen, damit Animationen nur neu zeichnen.
 * Fängt immer alle Berührungen ab, damit der Screen darunter nicht scrollt oder reagiert;
 * ein Tipp ruft [onTap] nur auf, wenn [tapToDismiss] true ist.
 */
@Composable
internal fun Scrim(alpha: () -> Float, tapToDismiss: Boolean, onTap: () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = alpha().coerceIn(0f, 1f)) }
            .pointerInput(tapToDismiss) { detectTapGestures { if (tapToDismiss) currentOnTap() } },
    )
}

// =====================================================================================
// AppSheet
// =====================================================================================

// Öffnen: Feder ohne sichtbares Nachschwingen. Schließen: schneller und beschleunigend (ease-in),
// denn was verschwindet, soll nicht trödeln.
private val OpenSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
private const val ScrimInMillis = 220
private const val ContentFadeMillis = 200
private const val ContentFadeDelayMillis = 40
private const val CloseMillis = 200
private const val MaxHeightFraction = 0.92f

/**
 * Zustand eines [AppSheet]. Meist reicht der Standardwert; selbst anlegen ([rememberAppSheetState]) nur,
 * wenn das Sheet von außerhalb seines Inhalts geschlossen werden soll.
 */
@Stable
class AppSheetState internal constructor(private val scope: CoroutineScope) {
    // 1 = ganz unten versteckt, 0 = offen. Als Bruchteil der Höhe, weil die Höhe beim Start noch unbekannt ist.
    internal val hidden = Animatable(1f, visibilityThreshold = 0.0005f)
    internal val scrim = Animatable(0f)
    internal val contentAlpha = Animatable(0f)

    // Wie weit der Finger das Sheet gerade nach unten zieht (in Pixeln).
    internal var dragPx by mutableFloatStateOf(0f)
    internal var heightPx = 0
    internal var velocityThresholdPx = Float.MAX_VALUE
    internal var distanceThresholdPx = Float.MAX_VALUE
    internal var onDismissed: () -> Unit = {}

    /** true, sobald das Sheet zugeht (z. B. um Buttons zu sperren). */
    var isClosing by mutableStateOf(false)
        private set

    /**
     * Schließt das Sheet animiert. Erst wenn es ganz weg ist, läuft [then] (z. B. Speichern),
     * danach das onDismiss des Sheets. So ruckelt nichts, weil der Screen sich erst nach der Animation ändert.
     */
    fun dismiss(then: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        // invokeOnCompletion läuft auch, wenn die Animation abgebrochen wird – das Speichern geht also nie verloren.
        scope.launch { animateOut() }.invokeOnCompletion {
            then()
            onDismissed()
        }
    }

    internal suspend fun animateIn() {
        isClosing = false
        if (hidden.value >= 1f) contentAlpha.snapTo(0f)
        coroutineScope {
            launch { scrim.animateTo(1f, tween(ScrimInMillis, easing = LinearOutSlowInEasing)) }
            launch { contentAlpha.animateTo(1f, tween(ContentFadeMillis, delayMillis = ContentFadeDelayMillis)) }
            hidden.animateTo(0f, OpenSpring)
        }
    }

    private suspend fun animateOut() {
        foldDrag()
        // Ist das Sheet schon halb heruntergezogen, ist auch der Rest kürzer.
        val remaining = (1f - hidden.value).coerceIn(0f, 1f)
        val millis = (CloseMillis * remaining).roundToInt().coerceAtLeast(120)
        coroutineScope {
            launch { scrim.animateTo(0f, tween(millis, easing = LinearEasing)) }
            hidden.animateTo(1f, tween(millis, easing = FastOutLinearInEasing))
        }
    }

    /** Fingerbewegung anwenden; gibt zurück, wie viel davon verbraucht wurde. Nach oben geht es nicht über "offen" hinaus. */
    internal fun dragBy(delta: Float): Float {
        if (isClosing) return 0f
        val old = dragPx
        val new = (old + delta).coerceAtLeast(0f)
        dragPx = new
        return new - old
    }

    internal suspend fun stopAnimation() {
        if (!isClosing) hidden.stop()
    }

    /** Nach dem Loslassen: weit genug oder schnell genug gezogen = schließen, sonst zurückfedern. */
    internal suspend fun settle(velocity: Float) {
        if (isClosing) return
        foldDrag()
        val height = heightPx.coerceAtLeast(1)
        val draggedPx = hidden.value * height
        if (velocity > velocityThresholdPx || draggedPx > min(height * 0.3f, distanceThresholdPx)) {
            dismiss()
        } else {
            hidden.animateTo(0f, OpenSpring, initialVelocity = velocity / height)
        }
    }

    // Den Finger-Versatz in die Animation übernehmen, damit es ab hier nahtlos animiert weitergeht.
    private suspend fun foldDrag() {
        val height = heightPx
        if (dragPx != 0f && height > 0) {
            val target = hidden.value + dragPx / height
            hidden.snapTo(target)
            dragPx = 0f
        }
    }

    internal fun offsetPx(height: Int): Float =
        (hidden.value.coerceAtLeast(0f) * height + dragPx).coerceAtLeast(0f)

    internal fun scrimAlpha(): Float {
        val height = heightPx
        val visible = if (height > 0) 1f - offsetPx(height) / height else 1f - hidden.value
        return ScrimMaxAlpha * min(scrim.value, visible.coerceIn(0f, 1f))
    }
}

@Composable
fun rememberAppSheetState(): AppSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppSheetState(scope) }
}

/** Was der Inhalt eines [AppSheet] zusätzlich kann. */
@Stable
interface AppSheetScope {
    /** Schließt das Sheet animiert und führt danach [then] aus (z. B. `dismiss { onSave(wert) }`). */
    fun dismiss(then: () -> Unit = {})

    /**
     * Wie `ColumnScope.weight`: lässt z. B. eine LazyColumn den Rest des Sheets füllen.
     * Nur zusammen mit `fillHeight = true` sinnvoll.
     */
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

private class AppSheetScopeImpl(
    private val state: AppSheetState,
    private val column: ColumnScope,
) : AppSheetScope {
    override fun dismiss(then: () -> Unit) = state.dismiss(then)

    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
        val base = this
        return with(column) { base.weight(weight, fill) }
    }
}

/**
 * Eigenes Bottom Sheet mit ruhiger, flüssiger Bewegung (ersetzt ModalBottomSheet).
 *
 * - Öffnet mit einer Feder von unten, der Hintergrund dunkelt sanft ab, der Inhalt blendet kurz danach ein.
 * - Schließt per Zurück-Taste, Tipp auf den Hintergrund, Herunterziehen (am Griff, an freien Flächen oder
 *   wenn die Liste oben ist) oder per [AppSheetScope.dismiss] aus dem Inhalt.
 * - [onDismiss] wird immer erst NACH der Schließ-Animation aufgerufen. Das übliche Muster
 *   `if (show) AppSheet(onDismiss = { show = false }) { ... }` funktioniert also mit Animation.
 * - Der Inhalt bleibt über der Tastatur und der Navigationsleiste; maximal 92 % der Bildschirmhöhe.
 *
 * Der Inhalt liegt in einer Spalte und bringt seine eigenen seitlichen Abstände mit (meist 24 dp).
 * Effekte, die Elemente im Inhalt betreffen (z. B. FocusRequester.requestFocus), gehören IN den Inhalt,
 * weil er erst einen Frame später gezeichnet wird.
 *
 * @param fillHeight false: Höhe passt sich dem Inhalt an, Inhalt scrollt bei Bedarf automatisch.
 *   true: Sheet ist immer maximal hoch, ohne automatisches Scrollen – für Listen (LazyColumn mit
 *   `Modifier.weight(1f)`) oder Suche.
 * @param dismissOnScrimTap false, wenn Eingaben nicht durch einen versehentlichen Tipp verloren gehen sollen.
 */
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: AppSheetState = rememberAppSheetState(),
    fillHeight: Boolean = false,
    dismissOnScrimTap: Boolean = true,
    showHandle: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable AppSheetScope.() -> Unit,
) {
    SideEffect { state.onDismissed = onDismiss }
    AppOverlay(onBack = { state.dismiss() }) {
        SheetFrame(
            state = state,
            modifier = modifier,
            fillHeight = fillHeight,
            dismissOnScrimTap = dismissOnScrimTap,
            showHandle = showHandle,
            containerColor = containerColor,
            content = content,
        )
    }
}

/**
 * Kopfzeile für ein [AppSheet]: Titel in der Serifenschrift, optional Untertitel und eine Aktion rechts
 * (z. B. `QuietButton("Fertig", onClick = { dismiss() })`).
 */
@Composable
fun AppSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = if (action != null) 12.dp else 24.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke(this)
    }
}

@Composable
private fun SheetFrame(
    state: AppSheetState,
    modifier: Modifier,
    fillHeight: Boolean,
    dismissOnScrimTap: Boolean,
    showHandle: Boolean,
    containerColor: Color,
    content: @Composable AppSheetScope.() -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    with(density) {
        state.velocityThresholdPx = 400.dp.toPx()
        state.distanceThresholdPx = 160.dp.toPx()
    }

    LaunchedEffect(state) { state.animateIn() }
    // Beim Schließen die Tastatur gleich mit wegschicken, statt sie bis zum Ende stehen zu lassen.
    LaunchedEffect(state.isClosing) { if (state.isClosing) focusManager.clearFocus(force = true) }
    BackHandler(enabled = !state.isClosing) { state.dismiss() }

    val nestedScroll = remember(state) { SheetNestedScroll(state) }
    val dragState = rememberDraggableState { delta -> state.dragBy(delta) }

    Box(Modifier.fillMaxSize()) {
        Scrim(alpha = state::scrimAlpha, tapToDismiss = dismissOnScrimTap, onTap = { state.dismiss() })
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .sheetLayout(state, fillHeight)
                    .then(modifier)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .nestedScroll(nestedScroll)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { state.stopAnimation() },
                        onDragStopped = { velocity -> state.settle(velocity) },
                    ),
            ) {
                // Die Fläche reicht bis zum unteren Rand, der Inhalt hält Abstand zu Tastatur und Navigationsleiste.
                Column(Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
                    if (showHandle) DragHandle(onDismiss = { state.dismiss() }) else Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .then(
                                if (fillHeight) {
                                    Modifier.weight(1f)
                                } else {
                                    // fill = false: nur so hoch wie nötig, höchstens bis zur Maximalhöhe, dann scrollen.
                                    Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                },
                            )
                            .graphicsLayer { alpha = state.contentAlpha.value }
                            .padding(bottom = 12.dp),
                    ) {
                        val scope = remember(state, this) { AppSheetScopeImpl(state, this) }
                        scope.content()
                    }
                }
            }
        }
    }
}

/**
 * Misst das Sheet (höchstens 92 % der Höhe) und verschiebt es per Grafik-Ebene nach unten.
 * Die Verschiebung wird nur beim Zeichnen gelesen: Während der Animation wird nichts neu vermessen.
 */
private fun Modifier.sheetLayout(state: AppSheetState, fillHeight: Boolean): Modifier =
    layout { measurable, constraints ->
        val maxHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight * MaxHeightFraction).roundToInt()
        } else {
            constraints.maxHeight
        }
        val placeable = measurable.measure(
            constraints.copy(minHeight = if (fillHeight && constraints.hasBoundedHeight) maxHeight else 0, maxHeight = maxHeight),
        )
        state.heightPx = placeable.height
        layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) { translationY = state.offsetPx(placeable.height) }
        }
    }

@Composable
private fun DragHandle(onDismiss: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                contentDescription = "Ziehpunkt"
                onClick(label = "Schließen") {
                    onDismiss()
                    true
                }
            },
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
        )
    }
}

/**
 * Verbindet Scrollen im Inhalt mit dem Herunterziehen des Sheets:
 * Ist die Liste ganz oben und der Finger zieht weiter nach unten, folgt das Sheet dem Finger.
 */
private class SheetNestedScroll(private val state: AppSheetState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        // Sheet ist heruntergezogen und der Finger geht wieder hoch: erst das Sheet zurückschieben.
        if (available.y < 0f && source == NestedScrollSource.UserInput && state.dragPx > 0f) {
            Offset(0f, state.dragBy(available.y))
        } else {
            Offset.Zero
        }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y > 0f && source == NestedScrollSource.UserInput) {
            Offset(0f, state.dragBy(available.y))
        } else {
            Offset.Zero
        }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (state.dragPx > 0f) {
            state.settle(available.y)
            available
        } else {
            Velocity.Zero
        }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (state.dragPx > 0f) {
            state.settle(available.y)
            available
        } else {
            Velocity.Zero
        }
}
