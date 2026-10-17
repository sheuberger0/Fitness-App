package com.sheuberger.fitness.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.ChartPoint
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.LineChart
import com.sheuberger.fitness.ui.components.RangeSelector
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.StatTile
import com.sheuberger.fitness.ui.components.TimeRange
import com.sheuberger.fitness.ui.components.WeightEntrySheet
import com.sheuberger.fitness.ui.components.trendColor
import com.sheuberger.fitness.ui.components.withContent
import java.time.LocalDate

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Fortschritt einer Übung: großer Wert oben, Graph mit Zeitraum-Auswahl, Kennzahlen und der Verlauf.
 * Aufgebaut wie die Übungs-Graphen in Apple Health oder Strong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exercise = state.exercise

    // onBack darf nur einmal ausgelöst werden: Während der Ausblend-Animation ist der Screen
    // noch sichtbar, ein zweites popBackStack() würde sonst auch den Tab darunter schließen.
    var left by remember { mutableStateOf(false) }
    val leave = {
        if (!left) {
            left = true
            onBack()
        }
    }
    // Wurde die Übung gelöscht, gibt es hier nichts mehr zu zeigen.
    LaunchedEffect(state.deleted) { if (state.deleted) leave() }

    var showMenu by remember { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var rejectedName by remember { mutableStateOf<String?>(null) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseLog?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = exercise?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }, enabled = exercise != null) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Bearbeiten") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    rejectedName = null
                                    showEdit = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Übung löschen", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (exercise != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Eintragen") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        if (state.loading || exercise == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.withContent(),
        ) {
            item(key = "progress") {
                ProgressCard(state = state, onRangeChange = viewModel::setRange)
            }
            if (state.logs.isNotEmpty()) {
                item(key = "stats") {
                    StatsCard(state, Modifier.padding(top = 12.dp))
                }
                item(key = "history-label") {
                    SectionLabel(
                        "Verlauf",
                        Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp),
                    )
                }
                items(state.history, key = { it.date.toEpochDay() }) { day ->
                    DayCard(
                        day = day,
                        personalBestId = state.personalBest?.id,
                        onClickLog = { editing = it },
                        modifier = Modifier
                            .animateItem()
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }

    if (adding && exercise != null) {
        val latest = state.latest
        WeightEntrySheet(
            title = exercise.name,
            initialDate = LocalDate.now(),
            initialWeight = latest?.weightKg,
            initialReps = latest?.reps,
            showReps = true,
            step = 2.5,
            onDismiss = { adding = false },
            onSave = { date, weight, reps ->
                viewModel.saveLog(null, date, weight, reps)
                adding = false
            },
        )
    }

    editing?.let { log ->
        WeightEntrySheet(
            title = "Eintrag bearbeiten",
            initialDate = log.date,
            initialWeight = log.weightKg,
            initialReps = log.reps,
            showReps = true,
            step = 2.5,
            onDismiss = { editing = null },
            onSave = { date, weight, reps ->
                viewModel.saveLog(log, date, weight, reps)
                editing = null
            },
            onDelete = {
                viewModel.deleteLog(log)
                editing = null
            },
        )
    }

    if (showEdit && exercise != null) {
        ExerciseDialog(
            title = "Übung bearbeiten",
            confirmLabel = "Speichern",
            existingNames = state.otherNames,
            initialName = exercise.name,
            initialGroup = exercise.muscleGroup?.takeIf { it in MuscleGroups },
            rejectedName = rejectedName,
            onDismiss = { showEdit = false },
            onConfirm = { name, group ->
                viewModel.updateExercise(name, group) { saved ->
                    if (saved) showEdit = false else rejectedName = name
                }
            },
        )
    }

    if (showDelete && exercise != null) {
        val count = state.logs.size
        ConfirmDialog(
            title = "Übung löschen?",
            message = if (count == 0) {
                "„${exercise.name}“ wird endgültig gelöscht und aus allen Workouts entfernt."
            } else {
                "„${exercise.name}“ wird mit ${if (count == 1) "1 Eintrag" else "allen $count Einträgen"} " +
                    "endgültig gelöscht und aus allen Workouts entfernt."
            },
            confirmLabel = "Löschen",
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                // Sobald die Übung weg ist, meldet der State "deleted" und der Screen schließt sich.
                viewModel.deleteExercise()
            },
        )
    }
}

/** Kopfbereich mit großem Wert, Veränderung, Zeitraum-Auswahl und Graph. */
@Composable
private fun ProgressCard(
    state: ExerciseDetailUiState,
    onRangeChange: (TimeRange) -> Unit,
) {
    // Der Punkt, den man gerade im Graphen antippt oder überstreicht. Neue Punkte = keine Auswahl.
    var selected by remember(state.rangePoints) { mutableStateOf<ChartPoint?>(null) }
    val last = state.points.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        SectionLabel("Bestes Gewicht pro Tag")
        Spacer(Modifier.height(6.dp))

        if (last == null) {
            Text("Noch keine Einträge", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tippe auf „Eintragen“, um dein erstes Gewicht festzuhalten. " +
                    "Ab zwei Trainingstagen siehst du hier deinen Fortschritt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val shown = selected ?: last
        Row {
            Text(
                text = Format.number(shown.value),
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
        Text(
            text = selected?.let { Format.weekdayDate(it.date) }
                ?: "Zuletzt ${Format.relative(last.date).replaceFirstChar { it.lowercase() }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChangeLine(selected = selected, range = state.range, rangePoints = state.rangePoints)

        if (state.points.size < 2) {
            Spacer(Modifier.height(16.dp))
            ChartHint("Der Graph erscheint, sobald du an zwei Tagen ein Gewicht eingetragen hast.")
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        RangeSelector(selected = state.range, onSelect = onRangeChange)
        Spacer(Modifier.height(16.dp))
        if (state.rangePoints.isEmpty()) {
            ChartHint("Keine Einträge in diesem Zeitraum.")
        } else {
            LineChart(points = state.rangePoints, onSelect = { selected = it })
        }
    }
}

/**
 * Veränderung im Zeitraum, z. B. "+2,5 kg in den letzten 3 Monaten".
 * Beim Scrubben: Veränderung vom Anfang des Zeitraums bis zum gewählten Punkt.
 * Die Zeile ist immer da, damit nichts springt, wenn man den Finger auf den Graphen legt.
 */
@Composable
private fun ChangeLine(selected: ChartPoint?, range: TimeRange, rangePoints: List<ChartPoint>) {
    val first = rangePoints.firstOrNull()
    val (text, color) = when {
        selected != null && first != null && selected.date != first.date -> {
            val delta = selected.value - first.value
            "${Format.signedKg(delta)} seit ${Format.shortDate(first.date)}" to trendColor(delta)
        }
        selected != null -> "Erster Eintrag im Zeitraum" to MaterialTheme.colorScheme.onSurfaceVariant
        first != null && rangePoints.size >= 2 -> {
            val delta = rangePoints.last().value - first.value
            "${Format.signedKg(delta)} ${range.description}" to trendColor(delta)
        }
        else -> "Keine Veränderung ${range.description}" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

/** Platzhalter in der Größe des Graphen, damit die Karte nicht zusammenschrumpft. */
@Composable
private fun ChartHint(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Kennzahlen: Bestwert, Anzahl Einträge und Fortschritt seit dem ersten Eintrag. */
@Composable
private fun StatsCard(state: ExerciseDetailUiState, modifier: Modifier = Modifier) {
    val best = state.personalBest ?: return
    val first = state.points.first()
    val last = state.points.last()
    val delta = last.value - first.value
    val days = state.points.size

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        StatColumn(
            label = "Bestwert",
            value = Format.kg(best.weightKg),
            detail = Format.relative(best.date),
            modifier = Modifier.weight(1f),
        )
        StatColumn(
            label = "Einträge",
            value = state.logs.size.toString(),
            detail = if (days == 1) "an 1 Tag" else "an $days Tagen",
            modifier = Modifier.weight(1f),
        )
        StatColumn(
            label = "Fortschritt",
            value = Format.signedKg(delta),
            valueColor = trendColor(delta),
            detail = "Start ${Format.kg(first.value)}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        StatTile(label = label, value = value, valueColor = valueColor)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ein Trainingstag im Verlauf: Datum und darunter die Sätze, wie in Strong. */
@Composable
private fun DayCard(
    day: LogDay,
    personalBestId: Long?,
    onClickLog: (ExerciseLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = Format.weekdayDate(day.date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        day.logs.forEachIndexed { index, log ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClickLog(log) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // Satznummer, gedämpft wie die Spalte "Set" in Strong.
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = Format.setLine(log.weightKg, log.reps),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (log.id == personalBestId) PrBadge()
            }
        }
    }
}

/** Kleine Markierung für den persönlichen Rekord. */
@Composable
private fun PrBadge() {
    Text(
        text = "PR",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
