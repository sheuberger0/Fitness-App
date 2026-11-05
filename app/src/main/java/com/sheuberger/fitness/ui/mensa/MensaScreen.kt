package com.sheuberger.fitness.ui.mensa

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.mensa.Mensa
import com.sheuberger.fitness.mensa.RankBy
import com.sheuberger.fitness.mensa.RankedMeal
import com.sheuberger.fitness.mensa.Ranking
import com.sheuberger.fitness.mensa.University
import com.sheuberger.fitness.nutrition.toSlot
import com.sheuberger.fitness.ui.AppViewModelProvider
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.AppCardShape
import com.sheuberger.fitness.ui.components.AppFilterChip
import com.sheuberger.fitness.ui.components.AppMenu
import com.sheuberger.fitness.ui.components.AppMenuItem
import com.sheuberger.fitness.ui.components.ButtonTone
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.Entrance
import com.sheuberger.fitness.ui.components.FadeSwap
import com.sheuberger.fitness.ui.components.PillSegments
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.entrance
import com.sheuberger.fitness.ui.components.rememberEntrance
import com.sheuberger.fitness.ui.components.withContent
import com.sheuberger.fitness.ui.nutrition.FitsBadge
import com.sheuberger.fitness.ui.nutrition.MensaLogSheet
import com.sheuberger.fitness.ui.nutrition.NutritionFormat
import com.sheuberger.fitness.ui.nutrition.NutritionSnackbar
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mensa-Rangliste: Welche Gerichte in Polymensa und UZH-Mensen bringen heute am meisten Protein
 * (oder Kalorien) für höchstens das eigene Budget? Die Menüs kommen live von den Mensa-Webseiten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaScreen(
    viewModel: MensaViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val entrance = rememberEntrance()
    var detail by remember { mutableStateOf<RankedMeal?>(null) }
    // Das Gericht, das gerade ins Ernährungstagebuch eingetragen wird.
    var logging by remember { mutableStateOf<Meal?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Bestätigung unten am Rand, z. B. "Eingetragen: Mittag · 850 kcal", mit "Rückgängig" für einen Fehlgriff.
    fun confirmLogged(entry: FoodEntry) {
        scope.launch {
            // Für einen anderen Tag als heute steht der Tag dabei, damit klar ist, wo der Eintrag gelandet ist.
            val day = if (entry.date == state.today) "" else "${NutritionFormat.day(entry.date, state.today)} · "
            val result = snackbarHostState.showSnackbar(
                message = "Eingetragen: $day${entry.meal.label} · ${NutritionFormat.kcal(entry.kcal, entry.estimated)}",
                actionLabel = "Rückgängig",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLog(entry)
        }
    }

    Scaffold(
        // Eigene Snackbar im App-Stil, dieselbe wie im Essen-Tab.
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> NutritionSnackbar(data) } },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding()),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding.withContent(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                header(state, entrance, onOpenSettings = { settingsOpen = true }, viewModel = viewModel)
                when (val load = state.load) {
                    MenuLoad.Loading -> loadingPlaceholders()
                    MenuLoad.Offline -> item(key = "offline") {
                        Message(
                            title = "Keine Verbindung",
                            text = "Die Menüs kommen live von den Webseiten der Mensen. " +
                                "Prüfe deine Internetverbindung.",
                            action = { PrimaryButton("Erneut versuchen", onClick = viewModel::refresh) },
                        )
                    }
                    is MenuLoad.Loaded -> {
                        val failed = load.menu.failed intersect state.prefs.mensas
                        if (failed.isNotEmpty()) {
                            item(key = "failed") { FailedBanner(failed, onRetry = viewModel::refresh) }
                        }
                        results(state, onOpen = { detail = it }, onOpenSettings = { settingsOpen = true })
                    }
                }
            }
        }
    }

    detail?.let { meal ->
        MealDetailSheet(
            meal = meal,
            group = state.prefs.group,
            onDismiss = { detail = null },
            // Läuft, wenn das Detail-Sheet ganz zu ist: Dann öffnet sich gleich das Sheet zum Eintragen.
            onLog = { logging = meal.meal },
        )
    }
    logging?.let { meal ->
        MensaLogSheet(
            meal = meal,
            // Der gewählte Tag: So lässt sich ein Gericht auch für einen anderen Tag der Woche eintragen.
            date = state.date,
            initialSlot = meal.time.toSlot(),
            loadPortion = viewModel::mensaPortion,
            onLog = { entry -> viewModel.log(entry, onLogged = ::confirmLogged) },
            onDismiss = { logging = null },
            // Nur für heute (sonst null): Was heute noch fehlt, sagt über einen anderen Tag nichts.
            remainingKcal = state.remainingKcal,
        )
    }
    if (settingsOpen) {
        MensaSettingsSheet(
            prefs = state.prefs,
            onChange = viewModel::updatePrefs,
            onDismiss = { settingsOpen = false },
        )
    }
}

// --- Kopf: Titel, Tag, Mittag/Abend, Sortierung ---

private fun LazyListScope.header(
    state: MensaUiState,
    entrance: Entrance,
    onOpenSettings: () -> Unit,
    viewModel: MensaViewModel,
) {
    item(key = "title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .entrance(entrance, 0)
                .padding(start = 4.dp, top = 4.dp),
        ) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    text = "Mensa",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${MensaFormat.longDay(state.date)} · bis ${MensaFormat.chf(state.prefs.budget)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RemainingTodayHint(state.remainingKcal)
            }
            CircleIconButton(
                icon = Icons.Outlined.Tune,
                contentDescription = "Einstellungen",
                onClick = onOpenSettings,
                style = CircleIconButtonStyle.Tonal,
            )
        }
    }
    item(key = "days") {
        PillSegments(
            options = state.days,
            selected = state.date,
            onSelect = viewModel::selectDate,
            label = { if (it == state.today) "Heute" else MensaFormat.shortWeekday(it) },
            modifier = Modifier
                .entrance(entrance, 1)
                .padding(top = 20.dp),
        )
    }
    item(key = "filters") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .entrance(entrance, 2)
                .padding(top = 4.dp, bottom = 12.dp),
        ) {
            MealTime.entries.forEach { time ->
                AppFilterChip(
                    label = time.label,
                    selected = state.time == time,
                    onClick = { viewModel.selectTime(time) },
                )
            }
            Spacer(Modifier.weight(1f))
            SortButton(selected = state.prefs.rankBy, onSelect = { by -> viewModel.updatePrefs { it.copy(rankBy = by) } })
        }
    }
}

/**
 * "Heute noch 1450 kcal", "Ziel erreicht" oder "320 kcal über dem Ziel", nur wenn heute gewählt ist.
 * Blendet weich ein, aus und über (z. B. nach dem Eintragen oder beim Wechsel auf einen anderen Tag).
 */
@Composable
private fun RemainingTodayHint(remainingKcal: Double?) {
    // Gerundet: Ein Eintrag mit Nachkommastellen soll nicht für jede Zehntel-Kalorie neu überblenden.
    FadeSwap(targetState = remainingKcal?.roundToInt()) { remaining ->
        if (remaining != null) {
            val colors = MaterialTheme.colorScheme
            val reached = abs(remaining) <= MensaFormat.GOAL_TOLERANCE_KCAL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                    .padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            ) {
                Icon(
                    imageVector = if (reached) Icons.Outlined.TaskAlt else Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = MensaFormat.remainingToday(remaining.toDouble()),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SortButton(selected: RankBy, onSelect: (RankBy) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuietButton(
            text = selected.label,
            onClick = { open = true },
            leadingIcon = Icons.AutoMirrored.Outlined.Sort,
            tone = ButtonTone.Neutral,
            compact = true,
        )
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            RankBy.entries.forEach { by ->
                AppMenuItem(
                    text = "Nach ${by.label}",
                    icon = by.icon,
                    onClick = {
                        open = false
                        onSelect(by)
                    },
                )
            }
        }
    }
}

private val RankBy.icon
    get() = when (this) {
        RankBy.Protein -> Icons.Outlined.FitnessCenter
        RankBy.Calories -> Icons.Outlined.LocalFireDepartment
        RankBy.Value -> Icons.Outlined.Savings
    }

// --- Ergebnis ---

private fun LazyListScope.results(
    state: MensaUiState,
    onOpen: (RankedMeal) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ranking = state.ranking ?: return
    if (!state.hasMenu) {
        item(key = "closed") {
            Message(
                title = if (state.time == MealTime.Dinner) "Kein Abendessen" else "Kein Menü",
                text = if (state.time == MealTime.Dinner) {
                    "An diesem Tag gibt es in deinen Mensen kein Abendangebot."
                } else {
                    "Für diesen Tag ist noch kein Menü veröffentlicht, oder die Mensen haben geschlossen."
                },
            )
        }
        return
    }
    if (ranking.ranked.isEmpty() && ranking.unrated.isEmpty()) {
        item(key = "nothing") {
            Message(
                title = "Nichts bis ${MensaFormat.chf(state.prefs.budget)}",
                text = "Heute liegen alle Hauptgerichte über deinem Budget. Du kannst es in den Einstellungen anpassen.",
                action = { PrimaryButton("Budget anpassen", onClick = onOpenSettings) },
            )
        }
        return
    }

    val best = ranking.ranked.firstOrNull()
    if (best != null) {
        item(key = "best:${best.meal.id}") {
            BestMealCard(
                meal = best,
                by = state.prefs.rankBy,
                fits = best.meal.id in state.fitting,
                onClick = { onOpen(best) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (ranking.ranked.size > 1) {
        item(key = "ranking-label") {
            SectionLabel("Rangliste", Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp))
        }
        val top = ranking.ranked.first().metric(state.prefs.rankBy)
        itemsIndexed(ranking.ranked.drop(1), key = { _, it -> "rank:${it.meal.id}" }) { index, meal ->
            RankRow(
                rank = index + 2,
                meal = meal,
                by = state.prefs.rankBy,
                fits = meal.meal.id in state.fitting,
                share = if (top > 0) (meal.metric(state.prefs.rankBy) / top).toFloat() else 0f,
                onClick = { onOpen(meal) },
                modifier = Modifier
                    .animateItem()
                    .padding(bottom = 8.dp),
            )
        }
    }
    if (ranking.unrated.isNotEmpty()) {
        item(key = "unrated-label") {
            Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp)) {
                SectionLabel("Ohne Nährwerte")
                Text(
                    "Im Budget, aber die Mensa gibt keine Nährwerte an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(ranking.unrated, key = { _, it -> "unrated:${it.meal.id}" }) { _, meal ->
            UnratedRow(meal, onClick = { onOpen(meal) }, modifier = Modifier.animateItem().padding(bottom = 8.dp))
        }
    }
    item(key = "footer") { Footer(ranking, state) }
}

/**
 * Platz 1 groß wie eine Kennzahl-Karte in Health-Apps.
 * [fits]: Eine Portion passt noch ins heutige Kalorienziel (Pille "passt" hinter dem Namen).
 */
@Composable
private fun BestMealCard(
    meal: RankedMeal,
    by: RankBy,
    fits: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    AppCard(
        onClick = onClick,
        onClickLabel = "Details",
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Platz 1",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = meal.where(withLine = true),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MensaFormat.chf(meal.price),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meal.meal.name,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // fill = false: Die Pille steht direkt hinter einem kurzen Namen, nicht am rechten Rand.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (fits) {
                Spacer(Modifier.width(8.dp))
                FitsBadge()
            }
        }
        if (meal.meal.description.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meal.meal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RankBy.entries.forEach { metric ->
                Metric(
                    value = MensaFormat.metricValue(meal, metric),
                    unit = metric.unit,
                    label = metric.shortLabel,
                    highlighted = metric == by,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Metric(value: String, unit: String, label: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlighted) colors.primary else colors.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " $unit",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

/** Platz 2 und folgende: kompakte Zeile mit Balken, der den Abstand zu Platz 1 zeigt. [fits] wie bei Platz 1. */
@Composable
private fun RankRow(
    rank: Int,
    meal: RankedMeal,
    by: RankBy,
    fits: Boolean,
    share: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val secondary = if (by == RankBy.Calories) RankBy.Protein else RankBy.Calories
    AppCard(
        onClick = onClick,
        onClickLabel = "Details",
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(RankColumn),
            )
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = meal.meal.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fits) {
                        Spacer(Modifier.width(6.dp))
                        FitsBadge()
                    }
                }
                Text(
                    text = "${meal.where()} · ${MensaFormat.metricValue(meal, secondary)} ${secondary.unit}" +
                        meal.meal.dietTag(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${MensaFormat.metricValue(meal, by)} ${by.unit}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = MensaFormat.chf(meal.price),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ShareBar(share, Modifier.padding(start = RankColumn))
    }
}

private val RankColumn = 32.dp

/** Dünner Balken: wie viel ein Gericht im Vergleich zu Platz 1 hat. */
@Composable
private fun ShareBar(share: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                drawRoundRect(
                    fill.copy(alpha = 0.75f),
                    size = Size(size.width * share.coerceIn(0.04f, 1f), size.height),
                    cornerRadius = radius,
                )
            },
    )
}

@Composable
private fun UnratedRow(meal: RankedMeal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AppCard(
        onClick = onClick,
        onClickLabel = "Details",
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    meal.meal.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meal.where(withLine = true) + meal.meal.dietTag(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(MensaFormat.chf(meal.price), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        }
    }
}

@Composable
private fun Footer(ranking: Ranking, state: MensaUiState) {
    val hasEth = ranking.ranked.any { it.meal.mensa.university == University.ETH }
    val lines = buildList {
        if (ranking.tooExpensive > 0) {
            add(
                if (ranking.tooExpensive == 1) "1 weiteres Gericht kostet mehr als ${MensaFormat.chf(state.prefs.budget)}."
                else "${ranking.tooExpensive} weitere Gerichte kosten mehr als ${MensaFormat.chf(state.prefs.budget)}.",
            )
        }
        add("Preise für ${state.prefs.group.label}. Daten live von ETH Gastronomie und ZFV, ohne Gewähr.")
        if (hasEth) add("Polymensa: Protein aus den Angaben pro 100 g auf die Portion hochgerechnet.")
    }
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp),
    )
}

@Composable
private fun FailedBanner(failed: Set<Mensa>, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppCard(
        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${failed.joinToString(", ") { it.title }} gerade nicht erreichbar",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            QuietButton("Erneut", onClick = onRetry, compact = true)
        }
    }
}

/** Hinweis mitten in der Liste (statt EmptyState, der den ganzen Bildschirm füllen will). */
@Composable
private fun Message(title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** Ruhige Platzhalter-Karten, die leicht pulsieren, solange die Menüs laden. */
private fun LazyListScope.loadingPlaceholders() {
    item(key = "loading") {
        val transition = rememberInfiniteTransition(label = "loading")
        val alpha by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse",
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        ) {
            Placeholder(Modifier.height(196.dp))
            Spacer(Modifier.height(30.dp))
            repeat(4) { Placeholder(Modifier.height(76.dp)) }
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

// --- kleine Helfer ---

internal fun RankedMeal.metric(by: RankBy): Double = when (by) {
    RankBy.Protein -> nutrition.proteinG
    RankBy.Calories -> nutrition.kcal
    RankBy.Value -> proteinPerFranc
}

internal val RankBy.unit: String
    get() = when (this) {
        RankBy.Protein -> "g"
        RankBy.Calories -> "kcal"
        RankBy.Value -> "g/CHF"
    }

private val RankBy.shortLabel: String
    get() = when (this) {
        RankBy.Protein -> "Protein"
        RankBy.Calories -> "Kalorien"
        RankBy.Value -> "pro Franken"
    }

/** "Obere Mensa · Menu 3", oder "Obere Mensa +2", wenn es das Gericht in weiteren Mensen gibt. */
internal fun RankedMeal.where(withLine: Boolean = false): String = when {
    alsoAt.isNotEmpty() -> "${meal.mensa.title} +${alsoAt.size}"
    withLine -> meal.place()
    else -> meal.mensa.title
}

/** "Polymensa · Street" */
internal fun Meal.place(): String = "${mensa.title} · $line"

internal fun Meal.dietTag(): String = when {
    vegan -> " · vegan"
    vegetarian -> " · vegi"
    else -> ""
}
