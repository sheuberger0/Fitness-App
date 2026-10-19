package com.sheuberger.fitness.ui.workouts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.pressScale
import com.sheuberger.fitness.ui.components.rememberEntrance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.Workout
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.NameDialog
import com.sheuberger.fitness.ui.components.withContent

@Composable
fun WorkoutsScreen(
    onOpenWorkout: (Long) -> Unit,
    viewModel: WorkoutsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Workout?>(null) }
    var deleteTarget by remember { mutableStateOf<Workout?>(null) }
    val entrance = rememberEntrance()

    Scaffold(
        floatingActionButton = {
            // Bei leerer Liste übernimmt der Button im leeren Zustand diese Aufgabe.
            AnimatedFab(visible = !state.loading && state.workouts.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Workout") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        val content = when {
            state.loading -> ScreenContent.Loading
            state.workouts.isEmpty() -> ScreenContent.Empty
            else -> ScreenContent.List
        }
        StateCrossfade(content) { shown ->
        when (shown) {
            ScreenContent.Loading -> Unit
            ScreenContent.Empty -> Column(Modifier.padding(innerPadding)) {
                ScreenTitle(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                EmptyState(
                    title = "Noch keine Workouts",
                    message = "Lege ein Workout an, z. B. „Push“ oder „Oberkörper A“, und füge deine Übungen hinzu.",
                    // Etwas nach oben versetzt, damit der Inhalt optisch mittig wirkt.
                    modifier = Modifier.padding(bottom = 64.dp),
                    action = {
                        Button(onClick = { showCreate = true }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Workout erstellen")
                        }
                    },
                )
            }
            ScreenContent.List -> LazyColumn(
                contentPadding = innerPadding.withContent(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "title") {
                    Column(Modifier.entrance(entrance, 0)) {
                        ScreenTitle()
                        Text(
                            text = if (state.workouts.size == 1) "1 Workout" else "${state.workouts.size} Workouts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                itemsIndexed(state.workouts, key = { _, it -> it.workout.id }) { index, summary ->
                    WorkoutCard(
                        summary = summary,
                        onClick = { onOpenWorkout(summary.workout.id) },
                        onRename = { renameTarget = summary.workout },
                        onDelete = { deleteTarget = summary.workout },
                        modifier = Modifier
                            .animateItem()
                            .entrance(entrance, index + 1),
                    )
                }
            }
        }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Neues Workout",
            confirmLabel = "Erstellen",
            placeholder = "z. B. Push, Oberkörper A",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                // Nach dem Anlegen direkt ins Workout springen, um Übungen hinzuzufügen.
                viewModel.createWorkout(name, onOpenWorkout)
            },
        )
    }

    renameTarget?.let { workout ->
        NameDialog(
            title = "Workout umbenennen",
            confirmLabel = "Speichern",
            initial = workout.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameWorkout(workout, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { workout ->
        ConfirmDialog(
            title = "Workout löschen?",
            message = "„${workout.name}“ wird gelöscht. Die Übungen und dein Gewichtsverlauf bleiben erhalten.",
            confirmLabel = "Löschen",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteWorkout(workout)
                deleteTarget = null
            },
        )
    }
}

private enum class ScreenContent { Loading, Empty, List }

@Composable
private fun ScreenTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Workouts",
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier.padding(top = 8.dp),
    )
}

/** Karte im Stil der Routinen-Liste von Hevy: Name, Anzahl, Vorschau der Übungen, zuletzt trainiert. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    summary: WorkoutSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            // Langes Drücken öffnet dasselbe Menü wie die drei Punkte.
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 16.dp, end = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = summary.workout.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Format.exerciseCount(summary.exerciseNames.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary.exerciseNames.joinToString(" · ").ifEmpty { "Noch keine Übungen hinzugefügt" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = summary.lastTrained
                            ?.let { "Zuletzt trainiert: ${Format.relative(it)}" }
                            ?: "Noch nicht trainiert",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
