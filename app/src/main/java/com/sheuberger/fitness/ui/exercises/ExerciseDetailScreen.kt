package com.sheuberger.fitness.ui.exercises

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.AnimatedNumber
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.AppFab
import com.sheuberger.fitness.ui.components.ChartPoint
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.FadeSwap
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.LineChart
import com.sheuberger.fitness.ui.components.RangeSelector
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.StatTile
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.TimeRange
import com.sheuberger.fitness.ui.components.WeightEntrySheet
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.rememberAppDialogState
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.trendColor
import com.sheuberger.fitness.ui.components.withContent
import java.time.LocalDate

/** Ecken der gedrückten Fläche einer Satz-Zeile innerhalb einer Karte. */
private val RowShape = RoundedCornerShape(14.dp)

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
    val entrance = rememberEntrance()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fab = rememberFabScrollState()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // Klappt "Eintragen" beim Runterscrollen zum Kreis ein, beim Hochscrollen wieder aus.
            .nestedScroll(fab),
        topBar = {
            DetailTopBar(
                title = exercise?.name.orEmpty(),
                menuEnabled = exercise != null,
                showMenu = showMenu,
                onShowMenu = { showMenu = it },
                onBack = leave,
                onEdit = {
                    rejectedName = null
                    showEdit = true
                },
                onDelete = { showDelete = true },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedFab(visible = !state.loading && exercise != null) {
                AppFab(
                    text = "Eintragen",
                    icon = Icons.Filled.Add,
                    onClick = { adding = true },
                    expanded = fab.expanded,
                )
            }
        },
    ) { innerPadding ->
        // Weich vom Laden zum Inhalt überblenden statt hart aufzuploppen.
        StateCrossfade(targetState = !state.loading && exercise != null) { ready ->
            if (!ready) return@StateCrossfade

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding.withContent(),
            ) {
                item(key = "progress") {
                    ProgressCard(
                        state = state,
                        onRangeChange = viewModel::setRange,
                        modifier = Modifier.entrance(entrance, 0),
                    )
                }
                if (state.logs.isNotEmpty()) {
                    item(key = "stats") {
                        StatsCard(
                            state,
                            Modifier
                                .animateItem()
                                .entrance(entrance, 1)
                                .padding(top = 16.dp),
                        )
                    }
                    item(key = "history-label") {
                        SectionLabel(
                            "Verlauf",
                            Modifier
                                .animateItem()
                                .entrance(entrance, 2)
                                .padding(start = 6.dp, top = 32.dp, bottom = 10.dp),
                        )
                    }
                    itemsIndexed(state.history, key = { _, day -> day.date.toEpochDay() }) { index, day ->
                        DayCard(
                            day = day,
                            personalBestId = state.personalBest?.id,
                            onClickLog = { editing = it },
                            modifier = Modifier
                                .animateItem()
                                .entrance(entrance, 3 + index)
                                .padding(bottom = 12.dp),
                        )
                    }
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
        // Eigener Dialog-Zustand pro Öffnen, damit "Speichern" den Dialog nach Erfolg animiert schließen kann.
        val editDialog = rememberAppDialogState()
        ExerciseDialog(
            title = "Übung bearbeiten",
            confirmLabel = "Speichern",
            existingNames = state.otherNames,
            initialName = exercise.name,
            initialGroup = exercise.muscleGroup?.takeIf { it in MuscleGroups },
            rejectedName = rejectedName,
            state = editDialog,
            onDismiss = { showEdit = false },
            onConfirm = { name, group ->
                viewModel.updateExercise(name, group) { saved ->
                    // Erst wenn der Name angenommen wurde, gleitet der Dialog weg; sonst bleibt er mit Hinweis offen.
                    if (saved) editDialog.dismiss() else rejectedName = name
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

/**
 * Titelleiste: Zurück, Name der Übung (Serif) und das Menü. Sie bleibt in der Hintergrundfarbe;
 * erst wenn Inhalt darunter scrollt, blendet eine feine Linie ein (statt einer grauen Fläche).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    menuEnabled: Boolean,
    showMenu: Boolean,
    onShowMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    onClick = onBack,
                    style = CircleIconButtonStyle.Plain,
                )
            },
            actions = {
                // Box, damit sich das Menü am Button ausrichtet.
                Box(Modifier.padding(end = 4.dp)) {
                    CircleIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "Mehr",
                        onClick = { onShowMenu(true) },
                        style = CircleIconButtonStyle.Plain,
                        enabled = menuEnabled,
                    )
                    ExerciseMenu(
                        expanded = showMenu,
                        onDismiss = { onShowMenu(false) },
                        onEdit = {
                            onShowMenu(false)
                            onEdit()
                        },
                        onDelete = {
                            onShowMenu(false)
                            onDelete()
                        },
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            scrollBehavior = scrollBehavior,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            // Wert erst beim Zeichnen lesen: Beim Scrollen wird nur neu gezeichnet, nicht neu aufgebaut.
            modifier = Modifier.graphicsLayer { alpha = scrollBehavior.state.overlappedFraction },
        )
    }
}

/** Überlaufmenü im App-Stil: weiße Karte mit 16 dp Ecken, feiner Kante und Zeilen mit runder Druckfläche. */
@Composable
private fun ExerciseMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Keine Akzent-Tönung, nur ein weicher Schatten und eine Haarlinie zur Abgrenzung.
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(min = 200.dp),
    ) {
        MenuRow(text = "Bearbeiten", icon = Icons.Outlined.Edit, onClick = onEdit)
        MenuRow(
            text = "Übung löschen",
            icon = Icons.Outlined.Delete,
            color = MaterialTheme.colorScheme.error,
            onClick = onDelete,
        )
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp),
        // Etwas eingerückt und abgerundet: Beim Drücken erscheint eine weiche Pille statt eines Balkens.
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .clip(RowShape),
    )
}

/** Kopfbereich mit großem Wert, Veränderung, Zeitraum-Auswahl und Graph. */
@Composable
private fun ProgressCard(
    state: ExerciseDetailUiState,
    onRangeChange: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Der Punkt, den man gerade im Graphen antippt oder überstreicht. Neue Punkte = keine Auswahl.
    var selected by remember(state.rangePoints) { mutableStateOf<ChartPoint?>(null) }
    val last = state.points.lastOrNull()

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            // Wenn der erste Eintrag oder der Graph dazukommt, wächst die Karte weich mit.
            .animateContentSize(tween(250, easing = FastOutSlowInEasing)),
        contentPadding = PaddingValues(20.dp),
    ) {
        SectionLabel("Bestes Gewicht pro Tag")
        Spacer(Modifier.height(8.dp))

        if (last == null) {
            Text("Noch keine Einträge", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tippe auf „Eintragen“, um dein erstes Gewicht festzuhalten. " +
                    "Ab zwei Trainingstagen siehst du hier deinen Fortschritt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }

        val shown = selected ?: last
        Row {
            // Zählt beim neuen Wert hoch/runter, beim Scrubben springt die Zahl sofort mit.
            AnimatedNumber(
                value = shown.value,
                instant = selected != null,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " kg",
                // Einheit bleibt serifenlos wie die Zahl, nur Überschriften sind Serif.
                style = UnitStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(2.dp))
        // Überblendet nur beim Wechsel zwischen Auswahl und Übersicht oder beim Zeitraum,
        // beim Scrubben selbst wird der Text direkt aktualisiert.
        FadeSwap(
            targetState = HeaderLines(selected, last, state.range, state.rangePoints),
            contentKey = { (it.selected != null) to it.range },
        ) { lines ->
            Column {
                Text(
                    text = lines.selected?.let { Format.weekdayDate(it.date) }
                        ?: "Zuletzt ${Format.relative(lines.last.date).replaceFirstChar { it.lowercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChangeLine(selected = lines.selected, range = lines.range, rangePoints = lines.rangePoints)
            }
        }

        if (state.points.size < 2) {
            Spacer(Modifier.height(16.dp))
            ChartHint("Der Graph erscheint, sobald du an zwei Tagen ein Gewicht eingetragen hast.")
            return@AppCard
        }

        Spacer(Modifier.height(20.dp))
        RangeSelector(selected = state.range, onSelect = onRangeChange)
        Spacer(Modifier.height(16.dp))
        if (state.rangePoints.isEmpty()) {
            ChartHint("Keine Einträge in diesem Zeitraum.")
        } else {
            LineChart(points = state.rangePoints, onSelect = { selected = it })
        }
    }
}

/** Einheit neben der großen Zahl: serifenlos und normal, damit sie ruhig neben der Zahl steht. */
private val UnitStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
    )

/** Was unter der großen Zahl steht, als ein Zustand für die Überblendung. */
private data class HeaderLines(
    val selected: ChartPoint?,
    val last: ChartPoint,
    val range: TimeRange,
    val rangePoints: List<ChartPoint>,
)

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

    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
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

/**
 * Ein Trainingstag im Verlauf: Datum und darunter die Sätze, wie in Strong.
 * Ohne Trennlinien; jede Satz-Zeile zeigt beim Drücken eine weiche, runde Fläche.
 */
@Composable
private fun DayCard(
    day: LogDay,
    personalBestId: Long?,
    onClickLog: (ExerciseLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        // Wenig Rand, damit die Druckfläche der Zeilen fast bis an die Kartenkante reicht.
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            text = Format.weekdayDate(day.date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
        )
        day.logs.forEachIndexed { index, log ->
            // key: Jede Zeile behält ihren Druck-Zustand, auch wenn sich die Reihenfolge ändert.
            key(log.id) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .appClickable(
                            onClick = { onClickLog(log) },
                            shape = RowShape,
                            pressedScale = 0.98f,
                            haptic = false,
                            onClickLabel = "Eintrag bearbeiten",
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
}

/** Kleine Markierung für den persönlichen Rekord: Pille in der Akzentfarbe. */
@Composable
private fun PrBadge() {
    Text(
        text = "PR",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
