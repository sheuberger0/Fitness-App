package com.sheuberger.fitness.ui.bodyweight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.ChartPoint
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.LineChart
import com.sheuberger.fitness.ui.components.RangeSelector
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.StatTile
import com.sheuberger.fitness.ui.components.TimeRange
import com.sheuberger.fitness.ui.components.WeightEntrySheet
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

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightScreen(
    viewModel: BodyWeightViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    // Beim Wischen über den Graphen gewählter Punkt. Neue Punkte (anderer Zeitraum) setzen ihn zurück.
    var selected by remember(state.points) { mutableStateOf<ChartPoint?>(null) }
    // Der FAB zeigt seinen Text nur ganz oben, beim Scrollen schrumpft er zum runden Knopf.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Körpergewicht") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (!state.loading && state.entries.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Eintragen") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { sheet = SheetTarget.New },
                    expanded = fabExpanded,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            // Während des Ladens nichts zeigen, damit der leere Zustand nicht kurz aufblitzt.
            state.loading -> Unit
            state.entries.isEmpty() -> EmptyState(
                title = "Noch kein Gewicht eingetragen",
                message = "Wiege dich regelmäßig, am besten morgens vor dem Frühstück. " +
                    "Hier siehst du dann deinen Verlauf.",
                modifier = Modifier.padding(innerPadding),
                action = {
                    Button(onClick = { sheet = SheetTarget.New }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Gewicht eintragen")
                    }
                },
            )
            else -> BodyWeightContent(
                state = state,
                selected = selected,
                onSelect = { selected = it },
                onSelectRange = viewModel::selectRange,
                onOpenEntry = { sheet = SheetTarget.Edit(it) },
                listState = listState,
                contentPadding = innerPadding.withContent(),
            )
        }
    }

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
        is SheetTarget.Edit -> WeightEntrySheet(
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

@Composable
private fun BodyWeightContent(
    state: BodyWeightUiState,
    selected: ChartPoint?,
    onSelect: (ChartPoint?) -> Unit,
    onSelectRange: (TimeRange) -> Unit,
    onOpenEntry: (BodyWeightEntry) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            Header(state = state, selected = selected)
        }
        item(key = "range") {
            RangeSelector(
                selected = state.range,
                onSelect = onSelectRange,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
        }
        item(key = "chart") {
            ChartCard(state = state, onSelect = onSelect)
        }
        item(key = "stats") {
            Column(Modifier.padding(top = 24.dp)) {
                SectionLabel("Statistik", Modifier.padding(start = 4.dp, bottom = 8.dp))
                StatsCard(state)
            }
        }
        item(key = "history-label") {
            SectionLabel("Verlauf", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
        }
        itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
            // Die Liste ist absteigend sortiert, der vorherige (ältere) Eintrag steht also eins weiter unten.
            val previous = state.entries.getOrNull(index + 1)
            HistoryRow(
                entry = entry,
                delta = previous?.let { entry.weightKg - it.weightKg },
                isFirst = index == 0,
                isLast = index == state.entries.lastIndex,
                onClick = { onOpenEntry(entry) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Große Zahl oben wie in Apple Health: aktueller Wert oder der gerade im Graphen gewählte Punkt. */
@Composable
private fun Header(state: BodyWeightUiState, selected: ChartPoint?) {
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

    Column(Modifier.padding(horizontal = 4.dp)) {
        SectionLabel(if (selected != null) "Ausgewählt" else "Aktuell")
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                text = Format.number(value),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " kg",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = changeText,
            style = MaterialTheme.typography.bodyMedium,
            color = trendColor(delta, neutral = true),
        )
    }
}

@Composable
private fun ChartCard(state: BodyWeightUiState, onSelect: (ChartPoint?) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 8.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
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
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Start", stats?.start?.value.kgOrDash(), Modifier.weight(1f))
            StatTile("Tiefstwert", stats?.min.kgOrDash(), Modifier.weight(1f))
            StatTile("Höchstwert", stats?.max.kgOrDash(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Ø Zeitraum", stats?.average.kgOrDash(), Modifier.weight(1f))
            StatTile("Ø 7 Tage", state.weekAverage.kgOrDash(), Modifier.weight(1f))
            StatTile("Einträge", state.points.size.toString(), Modifier.weight(1f))
        }
    }
}

/** Eine Zeile der Verlaufsliste. Alle Zeilen zusammen sehen aus wie eine einzige Karte. */
@Composable
private fun HistoryRow(
    entry: BodyWeightEntry,
    delta: Double?,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = when {
        isFirst && isLast -> CardShape
        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RectangleShape
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (!isFirst) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = Format.weekdayDate(entry.date),
                    style = MaterialTheme.typography.bodyLarge,
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
}

/** Durchschnitte auf eine Nachkommastelle runden, sonst steht dort z. B. "81,37 kg". */
private fun Double?.kgOrDash(): String = this?.let { Format.kg(round(it * 10) / 10) } ?: "–"
