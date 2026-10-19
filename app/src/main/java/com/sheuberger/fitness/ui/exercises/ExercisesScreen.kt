package com.sheuberger.fitness.ui.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.AppCardShape
import com.sheuberger.fitness.ui.components.AppFab
import com.sheuberger.fitness.ui.components.AppFilterChip
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.SearchField
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.Sparkline
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.rememberAppDialogState
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.rememberFabScrollState
import com.sheuberger.fitness.ui.components.trendColor
import com.sheuberger.fitness.ui.theme.LocalDarkTheme

/** Die Übungsbibliothek: Suche, Filter nach Muskelgruppe und eine nach Gruppen sortierte Liste. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExercisesScreen(
    onOpenExercise: (Long) -> Unit,
    viewModel: ExercisesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Der Suchtext lebt im Screen, damit das Textfeld beim schnellen Tippen keine Zeichen verliert.
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val entrance = rememberEntrance()
    val fab = rememberFabScrollState()

    Scaffold(
        // Hört mit, in welche Richtung die Liste scrollt, damit der FAB ein- und ausklappen kann.
        modifier = Modifier.nestedScroll(fab),
        topBar = {
            ExercisesHeader(
                query = query,
                onQueryChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                selectedGroup = state.selectedGroup,
                onSelectGroup = viewModel::selectGroup,
            )
        },
        floatingActionButton = {
            // Erscheint mit leichtem Zoom, sobald die Liste geladen ist. Ohne Übungen übernimmt der Button
            // im leeren Zustand, damit nicht zwei gleiche Aktionen auf dem Bildschirm stehen.
            AnimatedFab(visible = !state.loading && state.hasExercises) {
                AppFab(
                    text = "Übung",
                    icon = Icons.Filled.Add,
                    onClick = { showCreate = true },
                    expanded = fab.expanded,
                )
            }
        },
    ) { innerPadding ->
        val content = when {
            state.loading -> ListContent.Loading
            !state.hasExercises -> ListContent.NoExercises
            state.sections.isEmpty() -> ListContent.NoMatches
            else -> ListContent.List
        }
        StateCrossfade(content) { shown ->
            when (shown) {
                ListContent.Loading -> Unit
                ListContent.NoExercises -> EmptyState(
                    title = "Noch keine Übungen",
                    message = "Lege deine erste Übung an, um dein Gewicht und deinen Fortschritt festzuhalten.",
                    // Etwas nach oben versetzt, damit der Inhalt optisch mittig wirkt.
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(bottom = 64.dp),
                    action = {
                        PrimaryButton(
                            text = "Übung anlegen",
                            onClick = { showCreate = true },
                            leadingIcon = Icons.Filled.Add,
                        )
                    },
                )
                ListContent.NoMatches -> EmptyState(
                    title = "Keine Treffer",
                    message = if (query.isBlank()) {
                        "In dieser Muskelgruppe gibt es noch keine Übungen."
                    } else {
                        "Keine Übung passt zu „${query.trim()}“."
                    },
                    modifier = Modifier.padding(innerPadding),
                )
                ListContent.List -> {
                    // Die Liste beginnt unter dem Kopfbereich, damit die Abschnittsüberschriften
                    // dort "kleben" bleiben und nicht hinter Suche und Filtern verschwinden.
                    val direction = LocalLayoutDirection.current
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding()),
                        contentPadding = PaddingValues(
                            start = innerPadding.calculateStartPadding(direction) + 16.dp,
                            end = innerPadding.calculateEndPadding(direction) + 16.dp,
                            // Unten Platz, damit der Floating Action Button die letzte Zeile nicht verdeckt.
                            bottom = innerPadding.calculateBottomPadding() + 104.dp,
                        ),
                    ) {
                        // Fortlaufende Position für die versetzte Eingangsanimation der ersten Elemente.
                        var position = 0
                        state.sections.forEach { section ->
                            val headerPosition = position
                            position += section.rows.size + 1
                            stickyHeader(key = "header-${section.group}") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .entrance(entrance, headerPosition)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp),
                                ) {
                                    SectionLabel(section.group, Modifier.weight(1f))
                                    Text(
                                        text = section.rows.size.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            itemsIndexed(section.rows, key = { _, row -> row.exercise.id }) { index, row ->
                                ExerciseRow(
                                    row = row,
                                    isFirst = index == 0,
                                    isLast = index == section.rows.lastIndex,
                                    onClick = { onOpenExercise(row.exercise.id) },
                                    modifier = Modifier
                                        .animateItem()
                                        .entrance(entrance, headerPosition + 1 + index),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        // Eigener Dialog-Zustand pro Öffnen: Damit kann "Anlegen" den Dialog erst animiert schließen.
        val createDialog = rememberAppDialogState()
        ExerciseDialog(
            title = "Neue Übung",
            confirmLabel = "Anlegen",
            existingNames = state.existingNames,
            // Ist gerade eine Muskelgruppe gefiltert, ist sie vermutlich auch gemeint.
            initialGroup = state.selectedGroup,
            state = createDialog,
            onDismiss = { showCreate = false },
            onConfirm = { name, group ->
                // Erst ausblenden, dann anlegen: Die neue Zeile gleitet in die Liste, sobald der Dialog weg ist.
                // Ein zweites schnelles Tippen legt nichts doppelt an, weil dismiss() dann nichts mehr tut.
                createDialog.dismiss { viewModel.createExercise(name, group) }
            },
        )
    }
}

private enum class ListContent { Loading, NoExercises, NoMatches, List }

/** Großer Titel (Serif), Suchfeld und die Filter-Chips. Bleibt beim Scrollen stehen. */
@Composable
private fun ExercisesHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedGroup: String?,
    onSelectGroup: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = "Übungen",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        )
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        ) {
            item(key = "all") {
                AppFilterChip(
                    label = "Alle",
                    selected = selectedGroup == null,
                    onClick = { onSelectGroup(null) },
                )
            }
            items(MuscleGroups, key = { it }) { group ->
                AppFilterChip(
                    label = group,
                    selected = selectedGroup == group,
                    // Erneutes Antippen der aktiven Gruppe zeigt wieder alle Übungen.
                    onClick = { onSelectGroup(if (selectedGroup == group) null else group) },
                )
            }
        }
    }
}

/**
 * Eine Übung als Zeile einer gruppierten Karte: Nur die erste und letzte Zeile eines Abschnitts haben
 * runde Ecken, so wirken alle Zeilen einer Muskelgruppe wie eine gemeinsame Karte. Keine Trennlinien
 * dazwischen, der großzügige Abstand reicht zum Unterscheiden.
 */
@Composable
private fun ExerciseRow(
    row: ExerciseRowState,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corner = AppCardCorner
    val shape = RoundedCornerShape(
        topStart = if (isFirst) corner else 0.dp,
        topEnd = if (isFirst) corner else 0.dp,
        bottomEnd = if (isLast) corner else 0.dp,
        bottomStart = if (isLast) corner else 0.dp,
    )
    // Im hellen Theme bekommt die Karte eine feine Kante wie AppCard, damit Weiß sich vom Papierton abhebt.
    val hairline = !LocalDarkTheme.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Zeilen einer gruppierten Karte geben nur ganz leicht nach.
            .appClickable(onClick = onClick, shape = shape, pressedScale = 0.98f, haptic = false)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (hairline) Modifier.groupOutline(isFirst, isLast, outline, corner) else Modifier)
            // 20 dp seitlich wie in allen Karten (AppCard), damit Texte über die Screens hinweg fluchten.
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isFirst) 16.dp else 12.dp,
                bottom = if (isLast) 16.dp else 12.dp,
            ),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.latest?.let { latest ->
                Text(
                    text = Format.relative(latest.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val latest = row.latest
        if (latest == null) {
            Text(
                text = "Noch keine Einträge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (row.trend.size >= 2) {
                // Grün/Rot je nach Tendenz statt Akzentfarbe: Farbe nur dort, wo sie etwas bedeutet.
                Sparkline(
                    values = row.trend,
                    color = trendColor(row.trend.last() - row.trend.first()),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(width = 56.dp, height = 28.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = Format.kg(latest.weightKg),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

/** Eckenradius der App-Karten (passt zu [AppCardShape]). */
private val AppCardCorner: Dp = 20.dp

/**
 * Zeichnet das passende Stück der Kartenkante für eine Zeile einer gruppierten Karte.
 * Trick: Wir zeichnen ein abgerundetes Rechteck, das bei mittleren Zeilen oben und unten über die Zeile
 * hinausragt. Der Zuschnitt der Zeile schneidet diese Teile ab, übrig bleiben nur die Seitenkanten –
 * so entsteht über alle Zeilen hinweg eine durchgehende Kante ohne Linien dazwischen.
 */
private fun Modifier.groupOutline(isFirst: Boolean, isLast: Boolean, color: Color, corner: Dp): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        val half = stroke / 2
        val overhang = corner.toPx() * 2
        val top = if (isFirst) half else -overhang
        val bottom = if (isLast) size.height - half else size.height + overhang
        drawRoundRect(
            color = color,
            topLeft = Offset(half, top),
            size = Size(size.width - stroke, bottom - top),
            cornerRadius = CornerRadius(corner.toPx() - half),
            style = Stroke(width = stroke),
        )
    }
