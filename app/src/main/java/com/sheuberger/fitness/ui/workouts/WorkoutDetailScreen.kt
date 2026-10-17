package com.sheuberger.fitness.ui.workouts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.pressScale
import com.sheuberger.fitness.ui.components.rememberEntrance
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.NameDialog
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.Sparkline
import com.sheuberger.fitness.ui.components.WeightEntrySheet
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = state.workout?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Optionen")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Umbenennen") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Workout löschen", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedFab(visible = !state.loading && !state.deleted && state.exercises.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showPicker = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Übung hinzufügen") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
                    Button(onClick = { showPicker = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Übung hinzufügen")
                    }
                },
            )
            DetailContent.List -> LazyColumn(
                contentPadding = innerPadding.withContent(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Column(
                        Modifier
                            .animateItem()
                            .entrance(entrance, 0)
                            .padding(bottom = 2.dp),
                    ) {
                        SectionLabel(Format.exerciseCount(state.exercises.size))
                        // Hinweis nur, solange noch nie etwas eingetragen wurde.
                        if (state.exercises.all { it.latest == null }) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tippe auf +, um das Gewicht für eine Übung einzutragen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val sparkColor = if (bests.size >= 2) trendColor(bests.last() - bests.first()) else MaterialTheme.colorScheme.primary

    val interaction = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 0.dp),
        ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                } else {
                    Text(
                        text = "Noch kein Gewicht eingetragen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Platz immer reservieren, damit die Buttons in allen Zeilen untereinander stehen.
            Sparkline(
                values = bests,
                color = sparkColor,
                modifier = Modifier.size(width = 56.dp, height = 28.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onLog) {
                Icon(Icons.Filled.Add, contentDescription = "Gewicht eintragen")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Optionen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Nach oben") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                        enabled = canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Nach unten") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                        enabled = canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Aus Workout entfernen", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.RemoveCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    library: List<Exercise>,
    addedIds: Set<Long>,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, end = 12.dp),
            ) {
                Text("Übung hinzufügen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Fertig") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Übung suchen") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Suche leeren")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (canCreate) {
                    item(key = "create") {
                        CreateExerciseRow(
                            modifier = Modifier.animateItem(),
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
                                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
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
                item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PickerRow(name: String, added: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Bereits hinzugefügte Übungen sind nicht mehr antippbar.
            .clickable(enabled = !added, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (added) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        // Plus wird beim Hinzufügen weich zum Häkchen.
        Crossfade(targetState = added, animationSpec = tween(200), label = "added") { isAdded ->
            if (isAdded) {
                Icon(Icons.Filled.Check, contentDescription = "Bereits im Workout", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.Add, contentDescription = "Hinzufügen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
