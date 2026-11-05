package com.sheuberger.fitness.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EggAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.nutrition.TargetSuggestion
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AnimatedFab
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.AppCardShape
import com.sheuberger.fitness.ui.components.AppFab
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppMenu
import com.sheuberger.fitness.ui.components.AppMenuItem
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.FadeSwap
import com.sheuberger.fitness.ui.components.NameDialog
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SecondaryButton
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.rememberFabScrollState
import com.sheuberger.fitness.ui.components.withContent
import com.sheuberger.fitness.ui.theme.LocalTrendColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Der Tab "Essen": das Ernährungstagebuch eines Tages. Oben steht, wie viel schon gegessen ist und was bis zum
 * Ziel fehlt, darunter die vier Mahlzeiten. Eingetragen wird über das Sheet "Eintragen" ([AddFoodSheet]).
 */
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel(factory = AppViewModelProvider.Factory),
    addFood: AddFoodViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Schon hier gesammelt und nicht erst im Sheet: So stehen Vorlagen und "Zuletzt" beim Öffnen sofort da,
    // statt einen Moment leer zu sein.
    val addState by addFood.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val fabScroll = rememberFabScrollState()
    val entrance = rememberEntrance()

    // Welche Mahlzeit das Sheet "Eintragen" vorschlägt; null = Sheet zu. Enums lassen sich speichern, so übersteht
    // das offene Sheet auch das Drehen des Bildschirms.
    var addSlot by rememberSaveable { mutableStateOf<MealSlot?>(null) }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var goalsOpen by rememberSaveable { mutableStateOf(false) }
    var templateSection by remember { mutableStateOf<MealSection?>(null) }

    // Android lässt die App oft über Nacht im Hintergrund weiterlaufen. Bei jedem Öffnen prüfen, ob "Heute" noch stimmt.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }

    fun showMessage(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            // Eine neue Meldung ersetzt die alte, statt sich hinten anzustellen.
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun deleteWithUndo(entry: FoodEntry) {
        viewModel.deleteEntry(entry)
        showMessage("${NutritionFormat.quoted(entry.name)} gelöscht", "Rückgängig") { viewModel.restoreEntry(entry) }
    }

    fun openAdd() {
        addSlot = state.suggestedSlot(LocalTime.now())
    }

    fun acceptSuggestion(suggestion: TargetSuggestion) {
        viewModel.acceptSuggestion(suggestion)
        showMessage("Neues Ziel: ${suggestion.newTargetKcal} kcal")
    }

    fun postponeSuggestion() {
        viewModel.postponeSuggestion()
        showMessage("Nächster Vorschlag frühestens in 14 Tagen")
    }

    val trend = state.trend
    // Der Trend gehört zu "jetzt" und nicht zu einem vergangenen Tag. Ganz ohne Gewicht und Einträge gibt es nichts zu zeigen.
    val showTrend = !state.loading && state.isToday && trend != null &&
        (trend.avgWeightKg != null || trend.avgIntakeKcal != null)
    // Ein Zielvorschlag will eine Antwort und steht deshalb oben. Sonst steht der Trend unter den Mahlzeiten:
    // Er ändert sich im Lauf des Tages nicht und soll beim Eintragen nicht im Weg sein.
    val trendOnTop = showTrend && trend?.suggestion != null
    val welcome = !state.loading && !state.hasHistory && state.isToday

    Scaffold(
        // Hört mit, in welche Richtung die Liste scrollt, damit der FAB ein- und ausklappen kann.
        modifier = Modifier.nestedScroll(fabScroll),
        floatingActionButton = {
            // Bei der Begrüßung übernimmt deren Button diese Aufgabe, damit nicht zwei gleiche Aktionen dastehen.
            AnimatedFab(visible = !state.loading && !welcome) {
                AppFab(text = "Eintragen", icon = Icons.Filled.Add, onClick = ::openAdd, expanded = fabScroll.expanded)
            }
        },
        // Eigene Snackbar im App-Stil, wie im Tab "Körpergewicht".
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> NutritionSnackbar(data) } },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = innerPadding.withContent(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Header(
                    state = state,
                    onPrevious = viewModel::previousDay,
                    onNext = viewModel::nextDay,
                    onToday = viewModel::showToday,
                    onGoals = { goalsOpen = true },
                    modifier = Modifier.entrance(entrance, 0),
                )
            }
            if (state.loading) {
                item(key = "loading") { LoadingPlaceholder() }
                return@LazyColumn
            }
            item(key = "summary") {
                SummaryCard(state, Modifier.animateItem().entrance(entrance, 1))
            }
            if (welcome) {
                item(key = "welcome") {
                    WelcomeCard(
                        goals = state.goals,
                        onAdd = ::openAdd,
                        onGoals = { goalsOpen = true },
                        modifier = Modifier.animateItem().entrance(entrance, 2),
                    )
                }
            }
            if (trendOnTop && trend != null) {
                item(key = "trend") {
                    TrendCard(
                        trend = trend,
                        goals = state.goals,
                        today = state.today,
                        onAccept = ::acceptSuggestion,
                        onPostpone = ::postponeSuggestion,
                        modifier = Modifier.animateItem().entrance(entrance, 2),
                    )
                }
            }
            if (state.canCopyYesterday) {
                item(key = "copy-yesterday") {
                    CopyYesterdayCard(
                        yesterdayKcal = state.yesterdayKcal,
                        onCopy = {
                            viewModel.copyDay(state.today.minusDays(1), state.today) { count ->
                                showMessage("${NutritionFormat.entries(count)} übernommen")
                            }
                        },
                        modifier = Modifier.animateItem().entrance(entrance, 2),
                    )
                }
            }
            state.sections.forEachIndexed { index, section ->
                item(key = "section-${section.slot.name}") {
                    MealSectionCard(
                        section = section,
                        date = state.date,
                        onAdd = { addSlot = section.slot },
                        onOpen = { editing = it },
                        onSaveTemplate = { templateSection = section },
                        modifier = Modifier.animateItem().entrance(entrance, 2 + index),
                    )
                }
            }
            if (state.canCopyToToday) {
                item(key = "copy-to-today") {
                    SecondaryButton(
                        text = "Auf heute kopieren",
                        leadingIcon = Icons.Outlined.ContentCopy,
                        onClick = {
                            viewModel.copyDay(state.date, state.today) { count ->
                                showMessage("${NutritionFormat.entries(count)} auf heute kopiert", "Zu heute") {
                                    viewModel.showToday()
                                }
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
            if (showTrend && !trendOnTop && trend != null) {
                item(key = "trend") {
                    TrendCard(
                        trend = trend,
                        goals = state.goals,
                        today = state.today,
                        onAccept = ::acceptSuggestion,
                        onPostpone = ::postponeSuggestion,
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 12.dp),
                    )
                }
            }
        }
    }

    // Sheets fahren beim Speichern erst weg und rufen danach ihre Callbacks und zum Schluss onDismiss auf.
    // Das Zurücksetzen auf null darf deshalb ruhig zweimal laufen.
    addSlot?.let { slot ->
        AddFoodSheet(
            addFood = addFood,
            state = addState,
            date = state.date,
            today = state.today,
            initialSlot = slot,
            remainingKcal = state.remainingKcal,
            onLogged = ::showMessage,
            onDismiss = { addSlot = null },
        )
    }

    editing?.let { entry ->
        // key: Für einen anderen Eintrag entsteht ein frisches Sheet mit dessen Werten.
        key(entry.id) {
            EntryEditSheet(
                entry = entry,
                onSave = {
                    editing = null
                    viewModel.updateEntry(it)
                },
                onDelete = {
                    editing = null
                    deleteWithUndo(it)
                },
                onDismiss = { editing = null },
                remainingKcal = state.remainingKcal,
            )
        }
    }

    if (goalsOpen) {
        NutritionGoalsSheet(
            goals = state.goals,
            onChange = viewModel::updateGoals,
            onDismiss = { goalsOpen = false },
        )
    }

    templateSection?.let { section ->
        NameDialog(
            title = "Als Vorlage speichern",
            confirmLabel = "Speichern",
            // Vorschlag wie "Frühstück Standard": Meist isst man zu einer Mahlzeit immer wieder dasselbe.
            initial = "${section.slot.label} Standard",
            placeholder = "z. B. Frühstück Standard",
            validate = { name ->
                if (addState.templates.any { it.template.name.equals(name, ignoreCase = true) }) {
                    "Eine Vorlage mit diesem Namen gibt es schon."
                } else {
                    null
                }
            },
            onDismiss = { templateSection = null },
            onConfirm = { name ->
                templateSection = null
                viewModel.saveTemplate(name, section) {
                    showMessage("Vorlage ${NutritionFormat.quoted(name)} gespeichert")
                }
            },
        )
    }
}

// --- Kopf ---

/** Titel, Tag und Tageswechsel. Gleiche Lage wie in den anderen Tabs: 20 dp vom Rand. */
@Composable
private fun Header(
    state: NutritionUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onGoals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(top = 8.dp),
        ) {
            Text(
                text = "Essen",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            FadeSwap(targetState = NutritionFormat.day(state.date, state.today)) { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // "Heute" nur, wenn ein anderer Tag gewählt ist: ein Tipp zurück statt vieler Pfeil-Tipps.
        AnimatedVisibility(
            visible = !state.isToday,
            enter = fadeIn(tween(160, delayMillis = 60)) + expandHorizontally(spring(dampingRatio = 1f, stiffness = 500f)),
            exit = fadeOut(tween(90)) + shrinkHorizontally(tween(200)),
        ) {
            QuietButton(text = "Heute", onClick = onToday, compact = true)
        }
        DaySwitcher(canGoForward = state.canGoForward, onPrevious = onPrevious, onNext = onNext)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(
            icon = Icons.Outlined.Tune,
            contentDescription = "Ziele",
            onClick = onGoals,
            style = CircleIconButtonStyle.Tonal,
        )
    }
}

/** ‹ › als eine Pille: Die beiden Pfeile gehören zusammen. Vorwärts geht es nur bis heute. */
@Composable
private fun DaySwitcher(canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        CircleIconButton(
            icon = Icons.Filled.ChevronLeft,
            contentDescription = "Vorheriger Tag",
            onClick = onPrevious,
            style = CircleIconButtonStyle.Plain,
        )
        CircleIconButton(
            icon = Icons.Filled.ChevronRight,
            contentDescription = "Nächster Tag",
            onClick = onNext,
            enabled = canGoForward,
            style = CircleIconButtonStyle.Plain,
        )
    }
}

// --- Tagessumme ---

/** Gegessen gegen Ziel, wie die großen Zahlen im Tab "Körpergewicht", dazu Protein und die übrigen Makros. */
@Composable
private fun SummaryCard(state: NutritionUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val totals = state.totals
    val goals = state.goals
    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SectionLabel(if (state.isToday) "Heute gegessen" else "Gegessen")
                Spacer(Modifier.height(4.dp))
                Row {
                    AnimatedCount(
                        value = totals.kcal.roundToInt(),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = " kcal",
                        // Die Einheit gehört zur Zahl und bleibt serifenlos und ruhig, wie "kg" im Gewichts-Tab.
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("Ziel", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(NutritionFormat.kcal(goals.kcalTarget.toDouble()), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        GoalBar(progress = (totals.kcal / goals.kcalTarget).toFloat(), height = 10.dp)
        Spacer(Modifier.height(10.dp))
        RemainingLine(state.remainingKcal)

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Protein",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${totals.proteinG.roundToInt()} / ${goals.proteinTargetG} g",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        GoalBar(progress = (totals.proteinG / goals.proteinTargetG).toFloat(), height = 6.dp)

        // Kohlenhydrate und Fett nur, wenn etwas davon bekannt ist: Viele Einträge (Schnell, Mensa) haben keine Angaben.
        val macros = listOfNotNull(
            totals.carbsG.takeIf { it >= 0.5 }?.let { "Kohlenhydrate ${NutritionFormat.grams(it)}" },
            totals.fatG.takeIf { it >= 0.5 }?.let { "Fett ${NutritionFormat.grams(it)}" },
        )
        if (macros.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = macros.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** "Noch 1450 kcal", "Ziel erreicht" (grün, mit Häkchen) oder "320 kcal drüber". */
@Composable
private fun RemainingLine(remainingKcal: Double) {
    val reached = NutritionFormat.goalReached(remainingKcal)
    val over = !reached && remainingKcal < 0
    val color = when {
        reached -> LocalTrendColors.current.positive
        // Drüber ist beim Aufbauen kein Drama: neutral statt rot.
        over -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (reached) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = NutritionFormat.remaining(remainingKcal),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = color,
        )
    }
}

// --- Hinweise über den Mahlzeiten ---

/** Begrüßung, solange noch nie etwas eingetragen wurde. */
@Composable
private fun WelcomeCard(goals: NutritionGoals, onAdd: () -> Unit, onGoals: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(colors.primaryContainer, CircleShape),
        ) {
            Icon(Icons.Outlined.EggAlt, contentDescription = null, tint = colors.onPrimaryContainer)
        }
        Spacer(Modifier.height(14.dp))
        Text("Dein Ernährungstagebuch", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Trag ein, was du isst: das Mensa-Gericht mit einem Tipp, Verpacktes per Barcode oder schnell " +
                "nur die Kalorien. Dein Ziel: ${goals.kcalTarget} kcal und ${goals.proteinTargetG} g Protein am Tag.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Erstes Essen eintragen",
            onClick = onAdd,
            leadingIcon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        QuietButton(text = "Ziele anpassen", onClick = onGoals, modifier = Modifier.fillMaxWidth())
    }
}

/** Heute ist noch leer, gestern nicht: Wer oft dasselbe isst, übernimmt den ganzen Tag mit einem Tipp. */
@Composable
private fun CopyYesterdayCard(yesterdayKcal: Double?, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Text("Wie gestern?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = yesterdayKcal
                ?.let { "Gestern hast du ${NutritionFormat.kcal(it)} eingetragen. Isst du heute dasselbe, übernimm alles auf einmal." }
                ?: "Isst du heute dasselbe wie gestern, übernimm alles auf einmal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            text = "Gestern übernehmen",
            onClick = onCopy,
            leadingIcon = Icons.Outlined.ContentCopy,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- Mahlzeiten ---

/**
 * Eine Mahlzeit als Karte: Name, Summe, "+" und darunter die Einträge.
 * Eine Karte pro Mahlzeit (statt einer Karte pro Eintrag): So sieht man auf einen Blick, was zusammengehört.
 * Die Höhe ändert sich weich, wenn ein Eintrag dazukommt oder gelöscht wird.
 */
@Composable
private fun MealSectionCard(
    section: MealSection,
    date: LocalDate,
    onAdd: () -> Unit,
    onOpen: (FoodEntry) -> Unit,
    onSaveTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    // Was beim Anzeigen des Tages schon da war, steht sofort da. Nur neue Einträge blenden ein.
    val initialIds = remember(date) { section.entries.map { it.id }.toSet() }
    val estimated = section.entries.any { it.estimated }
    val subtitle = if (section.entries.isEmpty()) {
        "Noch nichts eingetragen"
    } else {
        listOfNotNull(
            NutritionFormat.kcal(section.totals.kcal, estimated),
            section.totals.proteinG.takeIf { it >= 0.5 }?.let { NutritionFormat.protein(it) },
        ).joinToString(" · ")
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        // Innen nur 8 dp: Die Zeilen bringen 12 dp mit, so steht der Text wie in allen Karten 20 dp vom Rand.
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // animateContentSize innen statt am Kartenrand: So bleiben die runden Ecken auch während der Bewegung rund.
        Column(Modifier.animateContentSize(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                ) {
                    Text(section.slot.label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (section.entries.isNotEmpty()) {
                    Box {
                        CircleIconButton(
                            icon = Icons.Filled.MoreHoriz,
                            contentDescription = "Optionen für ${section.slot.label}",
                            onClick = { menuOpen = true },
                            style = CircleIconButtonStyle.Plain,
                            size = 40.dp,
                        )
                        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppMenuItem(
                                text = "Als Vorlage speichern",
                                icon = Icons.Outlined.BookmarkAdd,
                                onClick = {
                                    menuOpen = false
                                    onSaveTemplate()
                                },
                            )
                        }
                    }
                }
                CircleIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Zu ${section.slot.label} hinzufügen",
                    onClick = onAdd,
                    size = 40.dp,
                )
            }
            if (section.entries.isNotEmpty()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.outlineVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                )
            }
            section.entries.forEach { entry ->
                key(entry.id) {
                    EntryRow(entry = entry, appear = entry.id !in initialIds, onClick = { onOpen(entry) })
                }
            }
        }
    }
}

/** Ein Eintrag: Name und Menge links, Kalorien und Protein rechts. Tippen öffnet das Bearbeiten. */
@Composable
private fun EntryRow(entry: FoodEntry, appear: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Neue Einträge blenden ein, während die Karte weich wächst.
    val alpha = remember { Animatable(if (appear) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear) alpha.animateTo(1f, tween(260, delayMillis = 80)) }
    val detail = NutritionFormat.entryDetail(entry)
    val protein = entry.proteinG?.let { NutritionFormat.protein(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .appClickable(onClick = onClick, shape = AppFieldShape, pressedScale = 0.98f, onClickLabel = "Bearbeiten")
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = NutritionFormat.kcal(entry.kcal, entry.estimated),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
            )
            if (protein != null) {
                Text(protein, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// --- Laden ---

/**
 * Ruhige Platzhalter, solange die Datenbank antwortet. Erst nach einem kurzen Moment: Meist sind die Daten
 * schneller da, und dann soll nichts aufblitzen.
 */
@Composable
private fun LoadingPlaceholder() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        show = true
    }
    AnimatedVisibility(visible = show, enter = fadeIn(tween(200))) {
        val transition = rememberInfiniteTransition(label = "loading")
        val pulse by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse",
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.graphicsLayer { alpha = pulse },
        ) {
            Placeholder(Modifier.height(188.dp))
            repeat(MealSlot.entries.size) { Placeholder(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(AppCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}
