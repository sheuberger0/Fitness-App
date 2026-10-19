package com.sheuberger.fitness.ui.bodyweight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.AnimatedNumber
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.AppFab
import com.sheuberger.fitness.ui.components.ChartPoint
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Entrance
import com.sheuberger.fitness.ui.components.FadeSwap
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.LineChart
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.RangeSelector
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.StatTile
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.TimeRange
import com.sheuberger.fitness.ui.components.WeightEntrySheet
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.trendColor
import com.sheuberger.fitness.ui.components.withContent
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.round

/** Welches Bottom Sheet gerade offen ist: neuer Eintrag oder Bearbeiten eines vorhandenen. */
private sealed interface SheetTarget {
    data object New : SheetTarget
    data class Edit(val entry: BodyWeightEntry) : SheetTarget
}

private val SnackbarShape = RoundedCornerShape(16.dp)

private enum class WeightContent { Loading, Empty, List }

@Composable
fun BodyWeightScreen(
    viewModel: BodyWeightViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    // Beim Wischen über den Graphen gewählter Punkt. Neue Punkte (anderer Zeitraum) setzen ihn zurück.
    var selected by remember(state.points) { mutableStateOf<ChartPoint?>(null) }
    // Der FAB zeigt seinen Text nur ganz oben, beim Scrollen schrumpft er (animiert) zum runden Knopf.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val entrance = rememberEntrance()

    fun deleteWithUndo(entry: BodyWeightEntry) {
        viewModel.delete(entry)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Eintrag gelöscht",
                actionLabel = "Rückgängig",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    // Keine Top-App-Bar: Der Titel steht wie bei "Workouts" und "Übungen" als erste Zeile im Inhalt,
    // damit er beim Wechsel zwischen den Tabs an derselben Stelle und in derselben Größe bleibt.
    Scaffold(
        floatingActionButton = {
            AnimatedFab(visible = !state.loading && state.entries.isNotEmpty()) {
                AppFab(
                    text = "Eintragen",
                    icon = Icons.Filled.Add,
                    onClick = { sheet = SheetTarget.New },
                    expanded = fabExpanded,
                )
            }
        },
        // Eigene Snackbar im App-Stil statt der Material-Standardoptik.
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> AppSnackbar(data) } },
    ) { innerPadding ->
        val content = when {
            state.loading -> WeightContent.Loading
            state.entries.isEmpty() -> WeightContent.Empty
            else -> WeightContent.List
        }
        StateCrossfade(content) { shown ->
            when (shown) {
                // Während des Ladens nichts zeigen, damit der leere Zustand nicht kurz aufblitzt.
                WeightContent.Loading -> Unit
                WeightContent.Empty -> Column(Modifier.padding(innerPadding)) {
                    // Gleiche Lage wie in der Liste: 20 dp vom linken Rand, 20 dp unter der Statusleiste.
                    ScreenHeadline(Modifier.padding(start = 20.dp, top = 20.dp))
                    EmptyState(
                        title = "Noch kein Gewicht eingetragen",
                        message = "Wiege dich regelmäßig, am besten morgens vor dem Frühstück. " +
                            "Hier siehst du dann deinen Verlauf.",
                        // Etwas nach oben versetzt, damit der Inhalt optisch mittig wirkt.
                        modifier = Modifier.padding(bottom = 64.dp),
                        action = {
                            PrimaryButton(
                                text = "Gewicht eintragen",
                                onClick = { sheet = SheetTarget.New },
                                leadingIcon = Icons.Filled.Add,
                            )
                        },
                    )
                }
                WeightContent.List -> BodyWeightContent(
                    state = state,
                    selected = selected,
                    onSelect = { selected = it },
                    onSelectRange = viewModel::selectRange,
                    onOpenEntry = { sheet = SheetTarget.Edit(it) },
                    listState = listState,
                    contentPadding = innerPadding.withContent(),
                    entrance = entrance,
                )
            }
        }
    }

    // Das Sheet fährt beim Speichern/Löschen erst animiert weg und ruft danach onSave/onDelete
    // und zum Schluss onDismiss auf. "sheet = null" darf deshalb ruhig zweimal laufen.
    when (val target = sheet) {
        null -> Unit
        SheetTarget.New -> WeightEntrySheet(
            title = "Gewicht eintragen",
            initialDate = LocalDate.now(),
            initialWeight = state.latest?.weightKg,
            step = 0.1,
            onDismiss = { sheet = null },
            onSave = { date, weightKg, _ ->
                sheet = null
                viewModel.save(BodyWeightEntry(date = date, weightKg = weightKg))
            },
        )
        // key: Für einen anderen Eintrag entsteht ein frisches Sheet mit dessen Werten.
        is SheetTarget.Edit -> key(target.entry.id) {
            WeightEntrySheet(
                title = "Eintrag bearbeiten",
                initialDate = target.entry.date,
                initialWeight = target.entry.weightKg,
                step = 0.1,
                onDismiss = { sheet = null },
                onSave = { date, weightKg, _ ->
                    sheet = null
                    viewModel.save(target.entry.copy(date = date, weightKg = weightKg))
                },
                onDelete = {
                    sheet = null
                    deleteWithUndo(target.entry)
                },
            )
        }
    }
}

@Composable
private fun BodyWeightContent(
    state: BodyWeightUiState,
    selected: ChartPoint?,
    onSelect: (ChartPoint?) -> Unit,
    onSelectRange: (TimeRange) -> Unit,
    onOpenEntry: (BodyWeightEntry) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    entrance: Entrance,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
    ) {
        item(key = "title") {
            // Die Liste rückt schon 16 dp (seitlich) und 8 dp (oben) ein. Zusammen ergibt das
            // 20 dp vom Rand und 20 dp unter der Statusleiste, wie bei den anderen Tabs.
            ScreenHeadline(
                Modifier
                    .entrance(entrance, 0)
                    .padding(start = 4.dp, top = 12.dp, bottom = 16.dp),
            )
        }
        item(key = "header") {
            Header(state = state, selected = selected, modifier = Modifier.entrance(entrance, 1))
        }
        item(key = "range") {
            RangeSelector(
                selected = state.range,
                onSelect = onSelectRange,
                modifier = Modifier
                    .entrance(entrance, 2)
                    .padding(top = 24.dp, bottom = 16.dp),
            )
        }
        item(key = "chart") {
            ChartCard(state = state, onSelect = onSelect, modifier = Modifier.entrance(entrance, 3))
        }
        item(key = "stats") {
            Column(
                Modifier
                    .entrance(entrance, 4)
                    .padding(top = 32.dp),
            ) {
                SectionLabel("Statistik", Modifier.padding(start = 4.dp, bottom = 10.dp))
                StatsCard(state)
            }
        }
        item(key = "history-label") {
            SectionLabel(
                "Verlauf",
                Modifier
                    .entrance(entrance, 4)
                    .padding(start = 4.dp, top = 32.dp, bottom = 10.dp),
            )
        }
        itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
            // Die Liste ist absteigend sortiert, der vorherige (ältere) Eintrag steht also eins weiter unten.
            val previous = state.entries.getOrNull(index + 1)
            HistoryRow(
                entry = entry,
                delta = previous?.let { entry.weightKg - it.weightKg },
                onClick = { onOpenEntry(entry) },
                // Jede Zeile ist eine eigene Karte mit Luft dazwischen statt Trennlinien. So können Einträge
                // beim Löschen/Rückgängig sauber ein- und ausblenden, ohne dass Nachbarn ihre Ecken wechseln.
                modifier = Modifier
                    .animateItem()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** Großer Bildschirm-Titel in der Serifenschrift (headlineLarge aus dem Theme), wie auf den anderen Tabs. */
@Composable
private fun ScreenHeadline(modifier: Modifier = Modifier) {
    Text(
        text = "Körpergewicht",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** Große Zahl oben wie in Apple Health: aktueller Wert oder der gerade im Graphen gewählte Punkt. */
@Composable
private fun Header(state: BodyWeightUiState, selected: ChartPoint?, modifier: Modifier = Modifier) {
    val latest = state.latest ?: return
    val stats = state.stats
    val value = selected?.value ?: latest.weightKg
    val dateText = if (selected != null) Format.weekdayDate(selected.date) else Format.relative(latest.date)

    // Beim Körpergewicht ist weder Zu- noch Abnahme an sich gut oder schlecht, daher neutrale Farbe.
    val (changeText, delta) = when {
        selected != null && stats != null ->
            if (selected.date == stats.start.date) {
                "Start des Zeitraums" to 0.0
            } else {
                val diff = selected.value - stats.start.value
                "${Format.signedKg(diff)} seit ${Format.shortDate(stats.start.date)}" to diff
            }
        stats != null && state.points.size >= 2 ->
            "${Format.signedKg(stats.change)} ${state.range.description}" to stats.change
        state.points.size == 1 -> "Ein Eintrag ${state.range.description}" to 0.0
        else -> "Keine Einträge ${state.range.description}" to 0.0
    }

    Column(modifier.padding(horizontal = 4.dp)) {
        FadeSwap(targetState = if (selected != null) "Ausgewählt" else "Aktuell") { SectionLabel(it) }
        Spacer(Modifier.height(8.dp))
        Row {
            // Zählt beim neuen Wert hoch/runter, beim Scrubben springt die Zahl sofort mit.
            AnimatedNumber(
                value = value,
                instant = selected != null,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " kg",
                // titleLarge ist eine Serifenschrift (für Titel). Die Einheit gehört zur Zahl, bleibt
                // deshalb serifenlos und normal, damit sie ruhig daneben steht (wie bei den Übungen).
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(4.dp))
        // Überblendet nur beim Wechsel zwischen Auswahl und Übersicht oder beim Zeitraum,
        // beim Scrubben selbst wird der Text direkt aktualisiert.
        FadeSwap(
            targetState = HeaderLines(dateText, changeText, delta, isSelection = selected != null, range = state.range),
            contentKey = { it.isSelection to it.range },
        ) { (date, change, diff) ->
            Column {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Etwas kräftiger als das Datum, damit die Veränderung ins Auge fällt.
                Text(
                    text = change,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = trendColor(diff, neutral = true),
                )
            }
        }
    }
}

/** Was unter der großen Zahl steht, als ein Zustand für die Überblendung. */
private data class HeaderLines(
    val date: String,
    val change: String,
    val delta: Double,
    val isSelection: Boolean,
    val range: TimeRange,
)

@Composable
private fun ChartCard(state: BodyWeightUiState, onSelect: (ChartPoint?) -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        // Links weniger Rand, weil der Graph dort selbst Platz für die Achsenbeschriftung lässt.
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
    ) {
        if (state.points.size >= 2) {
            LineChart(points = state.points, onSelect = onSelect)
        } else {
            // Mit weniger als zwei Punkten gibt es keine Linie, also lieber einen Hinweis zeigen.
            val hint = when {
                state.entries.size >= 2 -> "Zu wenige Einträge in diesem Zeitraum.\nWähle einen längeren Zeitraum."
                else -> "Ab zwei Einträgen siehst du hier deinen Verlauf."
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(state: BodyWeightUiState) {
    val stats = state.stats
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Start", stats?.start?.value.kgOrDash(), Modifier.weight(1f))
            StatTile("Tiefstwert", stats?.min.kgOrDash(), Modifier.weight(1f))
            StatTile("Höchstwert", stats?.max.kgOrDash(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Ø Zeitraum", stats?.average.kgOrDash(), Modifier.weight(1f))
            StatTile("Ø 7 Tage", state.weekAverage.kgOrDash(), Modifier.weight(1f))
            StatTile("Einträge", state.points.size.toString(), Modifier.weight(1f))
        }
    }
}

/** Ein Eintrag im Verlauf als eigene kleine Karte. Tippen öffnet das Sheet zum Bearbeiten. */
@Composable
private fun HistoryRow(
    entry: BodyWeightEntry,
    delta: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        onClick = onClick,
        onClickLabel = "Bearbeiten",
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Mindesthöhe für zwei Zeilen, damit der älteste Eintrag (ohne Veränderung) gleich hoch ist.
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Text(
                text = Format.weekdayDate(entry.date),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.kg(entry.weightKg), style = MaterialTheme.typography.titleMedium)
                if (delta != null) {
                    Text(
                        text = Format.signedKg(delta),
                        style = MaterialTheme.typography.labelMedium,
                        color = trendColor(delta, neutral = true),
                    )
                }
            }
        }
    }
}

/**
 * Snackbar im App-Stil: abgerundete, warme "umgekehrte" Fläche (im hellen Theme dunkel, im dunklen hell)
 * und die Aktion als Pillen-Button in Akzentfarbe. Ein- und Ausblenden übernimmt der SnackbarHost,
 * dazu gleitet sie beim Erscheinen ein kleines Stück nach oben.
 */
@Composable
private fun AppSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    val rise = remember(data) { Animatable(0f) }
    LaunchedEffect(data) { rise.animateTo(1f, spring(dampingRatio = 1f, stiffness = 500f)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // Auf Tablets nicht über die ganze Breite ziehen.
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
            modifier = Modifier.weight(1f),
        )
        val actionLabel = data.visuals.actionLabel
        if (actionLabel != null) {
            // QuietButton nimmt die Akzentfarbe aus dem Theme. Auf der umgekehrten Fläche braucht es die
            // "inverse" Akzentvariante, sonst wäre der Kontrast zu schwach.
            MaterialTheme(colorScheme = colors.copy(primary = colors.inversePrimary)) {
                QuietButton(
                    text = actionLabel,
                    onClick = { data.performAction() },
                    compact = true,
                )
            }
        }
    }
}

/** Durchschnitte auf eine Nachkommastelle runden, sonst steht dort z. B. "81,37 kg". */
private fun Double?.kgOrDash(): String = this?.let { Format.kg(round(it * 10) / 10) } ?: "–"
