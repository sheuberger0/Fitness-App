package com.sheuberger.fitness.ui.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import com.sheuberger.fitness.ui.components.StateCrossfade
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.pressScale
import com.sheuberger.fitness.ui.components.rememberEntrance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.EmptyState
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.Sparkline

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

    Scaffold(
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
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Übung") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
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
                modifier = Modifier.padding(innerPadding),
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
                                    .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
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
        ExerciseDialog(
            title = "Neue Übung",
            confirmLabel = "Anlegen",
            existingNames = state.existingNames,
            // Ist gerade eine Muskelgruppe gefiltert, ist sie vermutlich auch gemeint.
            initialGroup = state.selectedGroup,
            onDismiss = { showCreate = false },
            onConfirm = { name, group ->
                viewModel.createExercise(name, group)
                showCreate = false
            },
        )
    }
}

private enum class ListContent { Loading, NoExercises, NoMatches, List }

/** Großer Titel, Suchfeld und die Filter-Chips. Bleibt beim Scrollen stehen, wie bei Strong. */
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
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        ) {
            item(key = "all") {
                MuscleGroupChip(
                    label = "Alle",
                    selected = selectedGroup == null,
                    onClick = { onSelectGroup(null) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            }
            items(MuscleGroups, key = { it }) { group ->
                MuscleGroupChip(
                    label = group,
                    selected = selectedGroup == group,
                    // Erneutes Antippen der aktiven Gruppe zeigt wieder alle Übungen.
                    onClick = { onSelectGroup(if (selectedGroup == group) null else group) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        // BasicTextField statt OutlinedTextField: schlank, ohne Rahmen, wie ein iOS-Suchfeld.
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Übung suchen",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Suche löschen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * Eine Übung als Zeile einer gruppierten Karte (wie in den iOS-Einstellungen):
 * Nur die erste und letzte Zeile eines Abschnitts haben runde Ecken.
 */
@Composable
private fun ExerciseRow(
    row: ExerciseRowState,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corner = 16.dp
    val shape = RoundedCornerShape(
        topStart = if (isFirst) corner else 0.dp,
        topEnd = if (isFirst) corner else 0.dp,
        bottomEnd = if (isLast) corner else 0.dp,
        bottomStart = if (isLast) corner else 0.dp,
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Zeilen einer gruppierten Karte geben nur ganz leicht nach.
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Sparkline(
                        values = row.trend,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(width = 52.dp, height = 24.dp),
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
        if (!isLast) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
