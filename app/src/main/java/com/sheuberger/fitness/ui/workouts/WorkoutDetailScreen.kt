package com.sheuberger.fitness.ui.workouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.Exercise
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.AppFab
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.NameDialog
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.Sparkline
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.WeightEntrySheet
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.components.appTextFieldColors
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.trendColor
import com.sheuberger.fitness.ui.components.withContent
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var logTarget by remember { mutableStateOf<WorkoutExerciseRow?>(null) }
    val entrance = rememberEntrance()

    // Workout gelöscht (hier oder anderswo): zurück zur Liste. Nur einmal, damit nicht doppelt zurück navigiert wird.
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    // Großer Titel, der beim Scrollen zur normalen Leiste zusammenschrumpft (wie in iOS- und Material-Apps).
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Beim Herunterscrollen schrumpft der FAB zum Kreis, beim Hochscrollen bekommt er seinen Text zurück.
    val fabScroll = rememberFabScrollState()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier
            .nestedScroll(fabScroll)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    // Serifenschrift kommt aus dem Theme (headlineMedium groß, titleLarge eingeklappt).
                    Text(
                        text = state.workout?.name.orEmpty(),
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
                        modifier = Modifier.padding(start = 2.dp),
                    )
                },
                actions = {
                    Box(Modifier.padding(end = 6.dp)) {
                        CircleIconButton(
                            icon = Icons.Filled.MoreHoriz,
                            contentDescription = "Optionen",
                            onClick = { menuOpen = true },
                            style = CircleIconButtonStyle.Plain,
                        )
                        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuItem(
                                text = "Umbenennen",
                                icon = Icons.Outlined.Edit,
                                onClick = {
                                    menuOpen = false
                                    showRename = true
                                },
                            )
                            AppMenuItem(
                                text = "Workout löschen",
                                icon = Icons.Outlined.Delete,
                                destructive = true,
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
                // Leiste im selben warmen Ton wie die Seite, auch eingeklappt (kein grauer Balken).
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.background,
                ),
                scrollBehavior = scrollBehavior,
                // Stattdessen erscheint beim Einklappen eine feine Linie unter der Leiste.
                // collapsedFraction wird erst beim Zeichnen gelesen: Scrollen löst nur Neuzeichnen aus.
                modifier = Modifier.drawWithContent {
                    drawContent()
                    val fraction = scrollBehavior.state.collapsedFraction
                    if (fraction > 0f) {
                        val stroke = 1.dp.toPx()
                        val y = size.height - stroke / 2
                        drawLine(
                            color = colors.outlineVariant.copy(alpha = fraction.coerceIn(0f, 1f)),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedFab(visible = !state.loading && !state.deleted && state.exercises.isNotEmpty()) {
                AppFab(
                    text = "Übung hinzufügen",
                    icon = Icons.Filled.Add,
                    onClick = { showPicker = true },
                    expanded = fabScroll.expanded,
                )
            }
        },
    ) { innerPadding ->
        val content = when {
            state.loading || state.deleted -> DetailContent.Loading
            state.exercises.isEmpty() -> DetailContent.Empty
            else -> DetailContent.List
        }
        StateCrossfade(content) { shown ->
            when (shown) {
                DetailContent.Loading -> Unit
                DetailContent.Empty -> EmptyState(
                    title = "Noch keine Übungen",
                    message = "Füge Übungen hinzu und trage bei jedem Training dein Gewicht ein. So siehst du deinen Fortschritt.",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(bottom = 64.dp),
                    action = {
                        PrimaryButton(
                            text = "Übung hinzufügen",
                            onClick = { showPicker = true },
                            leadingIcon = Icons.Filled.Add,
                        )
                    },
                )
                DetailContent.List -> LazyColumn(
                    contentPadding = innerPadding.withContent(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        Column(
                            Modifier
                                .animateItem()
                                .entrance(entrance, 0)
                                .padding(start = 4.dp, bottom = 2.dp),
                        ) {
                            SectionLabel(Format.exerciseCount(state.exercises.size))
                            // Hinweis nur, solange noch nie etwas eingetragen wurde.
                            if (state.exercises.all { it.latest == null }) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tippe auf +, um das Gewicht für eine Übung einzutragen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    itemsIndexed(state.exercises, key = { _, row -> row.exercise.id }) { index, row ->
                        ExerciseRowCard(
                            row = row,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.exercises.lastIndex,
                            onClick = { onOpenExercise(row.exercise.id) },
                            onLog = { logTarget = row },
                            onMoveUp = { viewModel.moveExercise(row.exercise.id, up = true) },
                            onMoveDown = { viewModel.moveExercise(row.exercise.id, up = false) },
                            onRemove = { viewModel.removeExercise(row.exercise.id) },
                            modifier = Modifier
                                .animateItem()
                                .entrance(entrance, index + 1),
                        )
                    }
                }
            }
        }
    }

    logTarget?.let { row ->
        // Vorbelegt mit dem letzten Satz: meist trainiert man mit demselben Gewicht oder etwas mehr.
        // Das Sheet fährt beim Speichern erst weg und ruft dann onSave/onDismiss – beide setzen nur logTarget zurück.
        WeightEntrySheet(
            title = row.exercise.name,
            initialDate = LocalDate.now(),
            initialWeight = row.latest?.weightKg,
            initialReps = row.latest?.reps,
            showReps = true,
            step = 2.5,
            onDismiss = { logTarget = null },
            onSave = { date, weight, reps ->
                viewModel.logWeight(row.exercise.id, date, weight, reps)
                logTarget = null
            },
        )
    }

    if (showPicker) {
        ExercisePickerSheet(
            library = state.library,
            addedIds = remember(state.exercises) { state.exercises.map { it.exercise.id }.toSet() },
            onAdd = viewModel::addExercise,
            onCreate = viewModel::createAndAddExercise,
            onDismiss = { showPicker = false },
        )
    }

    if (showRename) {
        state.workout?.let { workout ->
            NameDialog(
                title = "Workout umbenennen",
                confirmLabel = "Speichern",
                initial = workout.name,
                onDismiss = { showRename = false },
                onConfirm = { name ->
                    viewModel.rename(name)
                    showRename = false
                },
            )
        }
    }

    if (showDelete) {
        state.workout?.let { workout ->
            ConfirmDialog(
                title = "Workout löschen?",
                message = "„${workout.name}“ wird gelöscht. Die Übungen und dein Gewichtsverlauf bleiben erhalten.",
                confirmLabel = "Löschen",
                onDismiss = { showDelete = false },
                onConfirm = {
                    showDelete = false
                    viewModel.delete()
                },
            )
        }
    }
}

private enum class DetailContent { Loading, Empty, List }

/** Eine Übung im Workout: Name, letzter Satz, Mini-Verlauf und ein schneller Weg zum Eintragen. */
@Composable
private fun ExerciseRowCard(
    row: WorkoutExerciseRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onLog: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val bests = row.recentBests
    val colors = MaterialTheme.colorScheme
    val sparkColor = if (bests.size >= 2) trendColor(bests.last() - bests.first()) else colors.primary

    AppCard(
        onClick = onClick,
        onClickLabel = "Verlauf öffnen",
        contentPadding = PaddingValues(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.exercise.muscleGroup ?: "Ohne Muskelgruppe",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val latest = row.latest
                if (latest != null) {
                    Row {
                        Text(
                            text = Format.setLine(latest.weightKg, latest.reps),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            text = "  ·  ${Format.relative(latest.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                } else {
                    Text(
                        text = "Noch kein Gewicht eingetragen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            // Platz immer reservieren, damit die Buttons in allen Zeilen untereinander stehen.
            Sparkline(
                values = bests,
                color = sparkColor,
                modifier = Modifier.size(width = 56.dp, height = 28.dp),
            )
            Spacer(Modifier.width(12.dp))
            // Das "+" ist die wichtigste Aktion der Zeile, deshalb in Akzentfarbe.
            CircleIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Gewicht eintragen",
                onClick = onLog,
                style = CircleIconButtonStyle.Filled,
            )
            Spacer(Modifier.width(2.dp))
            Box {
                CircleIconButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "Optionen",
                    onClick = { menuOpen = true },
                    style = CircleIconButtonStyle.Plain,
                    size = 40.dp,
                )
                AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AppMenuItem(
                        text = "Nach oben",
                        icon = Icons.Outlined.ArrowUpward,
                        enabled = canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    AppMenuItem(
                        text = "Nach unten",
                        icon = Icons.Outlined.ArrowDownward,
                        enabled = canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    AppMenuDivider()
                    AppMenuItem(
                        text = "Aus Workout entfernen",
                        icon = Icons.Outlined.RemoveCircleOutline,
                        destructive = true,
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Auswahl aus der Übungsbibliothek, gruppiert nach Muskelgruppe.
 * Das Sheet bleibt offen, damit man mehrere Übungen nacheinander hinzufügen kann.
 */
@Composable
private fun ExercisePickerSheet(
    library: List<Exercise>,
    addedIds: Set<Long>,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Zustand hier oben (außerhalb des Sheet-Inhalts), damit er Drehen und Neuaufbau übersteht.
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()

    // Collator sortiert deutsch korrekt (Ä bei A statt hinter Z).
    val collator = remember { Collator.getInstance(Locale.GERMANY) }
    val groups = remember(library, trimmed) {
        library
            .filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
            .groupBy { it.muscleGroup ?: "Sonstige" }
            .toList()
            // Reihenfolge wie in MuscleGroups, unbekannte Gruppen ans Ende.
            .sortedBy { (group, _) -> MuscleGroups.indexOf(group).let { if (it < 0) Int.MAX_VALUE else it } }
            .map { (group, exercises) -> group to exercises.sortedWith(compareBy<Exercise, String>(collator) { it.name }) }
    }
    // "Neu anlegen" nur anbieten, wenn es genau diesen Namen noch nicht gibt.
    val canCreate = trimmed.isNotEmpty() && library.none { it.name.equals(trimmed, ignoreCase = true) }

    // fillHeight: Das Sheet ist immer fast bildschirmhoch, damit es beim Tippen in die Suche nicht springt.
    AppSheet(onDismiss = onDismiss, fillHeight = true) {
        AppSheetHeader(title = "Übung hinzufügen") {
            // dismiss() lässt das Sheet erst wegfahren, danach kommt onDismiss.
            QuietButton(text = "Fertig", onClick = { dismiss() })
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Übung suchen") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                // Das Kreuz blendet weich ein, sobald etwas getippt wurde.
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.6f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.6f),
                ) {
                    CircleIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Suche leeren",
                        onClick = { query = "" },
                        style = CircleIconButtonStyle.Plain,
                        size = 36.dp,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            shape = AppFieldShape,
            colors = appTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))

        // weight(1f): Die Liste füllt den Rest des Sheets und scrollt; ganz oben angekommen,
        // zieht weiteres Ziehen nach unten das Sheet mit (das erledigt AppSheet).
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (canCreate) {
                item(key = "create") {
                    CreateExerciseRow(
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 8.dp),
                        name = trimmed,
                        onClick = {
                            onCreate(trimmed)
                            query = ""
                        },
                    )
                }
            }
            if (groups.isEmpty() && !canCreate) {
                item(key = "none") {
                    Text(
                        text = "Keine Übungen vorhanden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            groups.forEach { (group, exercises) ->
                item(key = "group-$group") {
                    SectionLabel(
                        group,
                        Modifier
                            .animateItem()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp),
                    )
                }
                items(exercises, key = { it.id }) { exercise ->
                    PickerRow(
                        name = exercise.name,
                        added = exercise.id in addedIds,
                        onClick = { onAdd(exercise.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PickerRow(name: String, added: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Außen 12 dp + innen 12 dp: Der Text steht bündig mit dem Titel (24 dp),
            // die gedrückte Fläche hat aber rundum etwas Luft und runde Ecken.
            .padding(horizontal = 12.dp)
            // Bereits hinzugefügte Übungen sind nicht mehr antippbar.
            .appClickable(
                onClick = onClick,
                shape = AppFieldShape,
                enabled = !added,
                pressedScale = 0.98f,
                onClickLabel = "Zum Workout hinzufügen",
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (added) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        AddedBadge(added)
    }
}

/**
 * Kleiner Kreis rechts in der Zeile: grau mit "+", nach dem Hinzufügen zart akzentfarben mit Häkchen.
 * Die Fläche wechselt weich die Farbe, das Icon tauscht mit kurzem Zoom und Überblendung.
 */
@Composable
private fun AddedBadge(added: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (added) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(200),
        label = "badgeContainer",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .background(container, CircleShape),
    ) {
        AnimatedContent(
            targetState = added,
            transitionSpec = {
                // Feder ohne Nachschwingen (dampingRatio 1): fühlt sich schnell, aber ruhig an.
                (fadeIn(tween(160, delayMillis = 40)) + scaleIn(spring(dampingRatio = 1f, stiffness = 600f), initialScale = 0.5f))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.5f))
            },
            label = "added",
        ) { isAdded ->
            if (isAdded) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Bereits im Workout",
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Hinzufügen",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CreateExerciseRow(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Neue Übung anlegen", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "„$name“",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
