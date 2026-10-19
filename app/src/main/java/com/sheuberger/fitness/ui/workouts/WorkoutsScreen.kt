package com.sheuberger.fitness.ui.workouts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.Workout
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.AppCardShape
import com.sheuberger.fitness.ui.components.AppFab
import com.sheuberger.fitness.ui.components.AppMenu
import com.sheuberger.fitness.ui.components.AppMenuItem
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.NameDialog
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.pressScale
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.rememberFabScrollState
import com.sheuberger.fitness.ui.components.withContent
import com.sheuberger.fitness.ui.theme.LocalDarkTheme

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
    // Beim Herunterscrollen schrumpft der FAB zum Kreis, beim Hochscrollen bekommt er seinen Text zurück.
    val fabScroll = rememberFabScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(fabScroll),
        floatingActionButton = {
            // Bei leerer Liste übernimmt der Button im leeren Zustand diese Aufgabe.
            AnimatedFab(visible = !state.loading && state.workouts.isNotEmpty()) {
                AppFab(
                    text = "Workout",
                    icon = Icons.Filled.Add,
                    onClick = { showCreate = true },
                    expanded = fabScroll.expanded,
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
                    ScreenTitle(Modifier.padding(start = 20.dp, top = 8.dp))
                    EmptyState(
                        title = "Noch keine Workouts",
                        message = "Lege ein Workout an, z. B. „Push“ oder „Oberkörper A“, und füge deine Übungen hinzu.",
                        // Etwas nach oben versetzt, damit der Inhalt optisch mittig wirkt.
                        modifier = Modifier.padding(bottom = 64.dp),
                        action = {
                            PrimaryButton(
                                text = "Workout erstellen",
                                onClick = { showCreate = true },
                                leadingIcon = Icons.Filled.Add,
                            )
                        },
                    )
                }
                ScreenContent.List -> LazyColumn(
                    contentPadding = innerPadding.withContent(),
                    // Viel Luft zwischen den Karten statt Trennlinien – wirkt ruhiger.
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "title") {
                        // 4 dp Einzug zusätzlich zum Listenrand: Titel und Untertitel stehen wie bei den
                        // anderen Tabs 20 dp vom Rand, damit beim Tab-Wechsel nichts springt.
                        Column(Modifier.entrance(entrance, 0).padding(start = 4.dp)) {
                            ScreenTitle()
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (state.workouts.size == 1) "1 Workout" else "${state.workouts.size} Workouts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
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

    // Dialoge schließen sich animiert; onConfirm läuft erst danach (siehe AppAlertDialog).
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

/** Großer Bildschirm-Titel in der Serifenschrift (kommt aus headlineLarge im Theme). */
@Composable
private fun ScreenTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Workouts",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 12.dp),
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
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    // Feine Kante nur im hellen Theme: Weiße Karte auf warmem Hintergrund braucht sie, im Dunkeln reicht der Farbunterschied.
    val hairline = !LocalDarkTheme.current

    // Wie AppCard, aber mit langem Drücken. Reihenfolge: erst skalieren und zuschneiden, dann klicken,
    // dann Hintergrund – so liegt der Ripple über der Fläche und alles gibt gemeinsam nach.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(AppCardShape)
            // Langes Drücken öffnet dasselbe Menü wie die drei Punkte.
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClickLabel = "Optionen",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            )
            .background(colors.surfaceContainerLow)
            .then(if (hairline) Modifier.border(1.dp, colors.outlineVariant, AppCardShape) else Modifier)
            .padding(start = 20.dp, top = 10.dp, bottom = 18.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.workout.name,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
                        text = "Umbenennen",
                        icon = Icons.Outlined.Edit,
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    AppMenuItem(
                        text = "Löschen",
                        icon = Icons.Outlined.Delete,
                        destructive = true,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Column(Modifier.padding(end = 12.dp)) {
            Text(
                text = Format.exerciseCount(summary.exerciseNames.size),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary.exerciseNames.joinToString(" · ").ifEmpty { "Noch keine Übungen hinzugefügt" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = summary.lastTrained
                        ?.let { "Zuletzt trainiert: ${Format.relative(it)}" }
                        ?: "Noch nicht trainiert",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
