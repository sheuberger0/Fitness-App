package com.sheuberger.fitness.ui.nutrition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.TemplateWithItems
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.mensa.Mensa
import com.sheuberger.fitness.nutrition.MensaEstimate
import com.sheuberger.fitness.nutrition.mensaTimeFor
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppMenu
import com.sheuberger.fitness.ui.components.AppMenuItem
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.PillSegments
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SearchField
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.mensa.MenuLoad
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Woher ein neuer Eintrag kommt: die Segmente im Sheet "Eintragen". */
internal enum class AddSource(val label: String) {
    Recent("Zuletzt"),
    Mensa("Mensa"),
    Foods("Lebensmittel"),
    Quick("Schnell"),
}

/**
 * Womit das Sheet startet:
 * - Mittag und Abend an Werktagen: Mensa. Dort isst du meistens, und das Gericht ist dann ein Tipp entfernt.
 * - Sonst "Zuletzt": Die meisten Mahlzeiten wiederholen sich (dasselbe Frühstück, derselbe Snack).
 * - Ist "Zuletzt" noch leer (erster Start), "Lebensmittel": Dort geht es zum Scannen und Anlegen.
 */
internal fun defaultSource(slot: MealSlot, date: LocalDate, hasRecent: Boolean): AddSource {
    val weekday = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    return when {
        weekday && (slot == MealSlot.LUNCH || slot == MealSlot.DINNER) -> AddSource.Mensa
        hasRecent -> AddSource.Recent
        else -> AddSource.Foods
    }
}

/**
 * Mittag- oder Abendmenü? Wer bei "Mittag" auf "+" tippt, sucht das Mittagessen, auch wenn es schon Abend ist
 * (nachtragen). Bei Frühstück und Snacks entscheidet heute die Uhrzeit ([mensaTimeFor]).
 */
internal fun initialMensaTime(slot: MealSlot, isToday: Boolean, now: LocalTime): MealTime = when (slot) {
    MealSlot.LUNCH -> MealTime.Lunch
    MealSlot.DINNER -> MealTime.Dinner
    else -> if (isToday) mensaTimeFor(now) else MealTime.Lunch
}

/** Was über dem Sheet "Eintragen" offen ist. Immer nur eines (siehe [AddFoodSheet]). */
private sealed interface AddChild {
    class MensaDish(val meal: Meal) : AddChild
    class Relog(val entry: FoodEntry) : AddChild
    class Barcode : AddChild
    class LogFood(val food: Food) : AddChild
    class EditFood(val food: Food?, val barcode: String? = null) : AddChild
    class DeleteTemplate(val template: TemplateWithItems) : AddChild
}

/**
 * Sheet "Eintragen": Mahlzeit wählen, dann aus "Zuletzt" (Vorlagen und frühere Einträge), der Mensa, den eigenen
 * Lebensmitteln (mit Barcode-Scanner) oder "Schnell" (nur Kalorien) eintragen.
 *
 * Die Sheets für einen einzelnen Eintrag (Mensa-Gericht, Lebensmittel, Barcode, …) legen sich ÜBER dieses Sheet,
 * statt es zu ersetzen. So führt "Zurück" aus ihnen wieder in die Liste, mit derselben Suche und Scroll-Position.
 * Wird etwas eingetragen, schließt sich danach auch dieses Sheet: Im Tagebuch sieht man dann gleich den neuen
 * Eintrag und die neue Summe, und [onLogged] zeigt eine kurze Bestätigung.
 *
 * @param remainingKcal was an [date] noch bis zum Ziel fehlt, für "passt" und "Danach noch … kcal".
 */
@Composable
fun AddFoodSheet(
    addFood: AddFoodViewModel,
    state: AddFoodUiState,
    date: LocalDate,
    today: LocalDate,
    initialSlot: MealSlot,
    remainingKcal: Double?,
    onLogged: (message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = com.sheuberger.fitness.ui.components.rememberAppSheetState()
    // Zustand hier oben und nicht im Sheet-Inhalt, damit er das Drehen des Bildschirms übersteht (wie in der Übungsauswahl).
    var slot by rememberSaveable { mutableStateOf(initialSlot) }
    var source by rememberSaveable {
        mutableStateOf(defaultSource(initialSlot, date, hasRecent = state.recent.isNotEmpty() || state.templates.isNotEmpty()))
    }
    var mensaTime by rememberSaveable { mutableStateOf(initialMensaTime(initialSlot, date == today, LocalTime.now())) }
    var query by rememberSaveable { mutableStateOf("") }
    var child by remember { mutableStateOf<AddChild?>(null) }
    // Die Listen lesen den Zustand über diese State-Variable statt direkt über den Parameter [state].
    // Ohne sie kam ein Update nicht immer an: Öffnete das Sheet mittags gleich im Segment "Mensa", zeigte die Liste
    // weiter Platzhalter, obwohl die Menüs längst geladen waren (gefunden mit einem Screenshot-Test). Wer einen State
    // liest, wird von Compose bei jeder Änderung neu aufgebaut, auch tief in AnimatedContent und im Overlay.
    val latest by rememberUpdatedState(state)
    // Eine Scroll-Position pro Segment: Beim Hin- und Herwechseln bleibt jede Liste, wo sie war.
    val recentList = rememberLazyListState()
    val mensaList = rememberLazyListState()
    val foodList = rememberLazyListState()

    fun close(message: String) = sheetState.dismiss { onLogged(message) }

    fun logAndClose(entry: FoodEntry) {
        addFood.log(entry)
        close("${NutritionFormat.quoted(entry.name)} eingetragen")
    }

    // Die Menüs erst laden, wenn das Segment "Mensa" zu sehen ist. Was der Mensa-Tab schon geladen hat, ist sofort da.
    LaunchedEffect(source, date) {
        if (source == AddSource.Mensa) addFood.loadMensa(date)
    }

    AppSheet(onDismiss = onDismiss, state = sheetState, fillHeight = true) {
        AppSheetHeader(
            title = "Eintragen",
            subtitle = listOfNotNull(NutritionFormat.day(date, today), remainingKcal?.let(NutritionFormat::remaining))
                .joinToString(" · "),
            action = {
                CircleIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Schließen",
                    onClick = { dismiss() },
                    style = CircleIconButtonStyle.Plain,
                )
            },
        )
        FitSegments(
            options = MealSlot.entries,
            selected = slot,
            onSelect = {
                slot = it
                // Mittag und Abend gibt es auch in der Mensa: Das Menü wechselt mit.
                if (it == MealSlot.LUNCH) mensaTime = MealTime.Lunch
                if (it == MealSlot.DINNER) mensaTime = MealTime.Dinner
            },
            label = { it.label },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(4.dp))
        FitSegments(
            options = AddSource.entries,
            selected = source,
            onSelect = { source = it },
            label = { it.label },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(4.dp))
        // weight(1f): Der Inhalt füllt den Rest des Sheets und scrollt selbst.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AnimatedContent(
                targetState = source,
                transitionSpec = {
                    // Wie beim Blättern: Das neue Segment kommt von der Seite, auf der es in der Leiste steht.
                    val forward = targetState.ordinal > initialState.ordinal
                    val shift = { width: Int -> if (forward) width / 10 else -width / 10 }
                    (slideInHorizontally(tween(260), shift) + fadeIn(tween(200, delayMillis = 40)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -shift(it) } + fadeOut(tween(120)))
                },
                contentAlignment = Alignment.TopStart,
                label = "addSource",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                when (shown) {
                    AddSource.Recent -> RecentList(
                        state = latest,
                        listState = recentList,
                        onApplyTemplate = { template ->
                            addFood.applyTemplate(template.template.id, date, slot)
                            close("${NutritionFormat.entries(template.items.size)} aus ${NutritionFormat.quoted(template.template.name)} eingetragen")
                        },
                        onDeleteTemplate = { child = AddChild.DeleteTemplate(it) },
                        onRelog = { child = AddChild.Relog(it) },
                    )
                    AddSource.Mensa -> MensaList(
                        load = latest.menus[date],
                        date = date,
                        mensas = latest.mensas,
                        time = mensaTime,
                        onTime = { mensaTime = it },
                        recentMensa = latest.recentMensa,
                        remainingKcal = remainingKcal,
                        listState = mensaList,
                        onRetry = { addFood.loadMensa(date, refresh = true) },
                        onDish = { child = AddChild.MensaDish(it) },
                        onRecent = { child = AddChild.Relog(it) },
                    )
                    AddSource.Foods -> FoodList(
                        foods = latest.foods,
                        query = query,
                        onQueryChange = { query = it },
                        listState = foodList,
                        onScan = { child = AddChild.Barcode() },
                        onCreate = { child = AddChild.EditFood(food = null) },
                        onFood = { child = AddChild.LogFood(it) },
                    )
                    AddSource.Quick -> QuickForm(
                        onLog = { name, kcal, protein ->
                            logAndClose(
                                FoodEntry(
                                    date = date,
                                    loggedAt = System.currentTimeMillis(),
                                    meal = slot,
                                    name = name.ifEmpty { NutritionFormat.QUICK_NAME },
                                    kcal = kcal,
                                    proteinG = protein,
                                    source = EntrySource.QUICK,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    // Die Sheets darüber. Jedes schließt sich vor seinem Callback selbst und ruft danach onDismiss. Öffnet der
    // Callback schon das nächste (z. B. "Bearbeiten"), darf dieses onDismiss es nicht gleich wieder schließen:
    // deshalb "nur wenn noch dasselbe offen ist" (===).
    when (val current = child) {
        null -> Unit
        is AddChild.MensaDish -> MensaLogSheet(
            meal = current.meal,
            date = date,
            initialSlot = slot,
            loadPortion = addFood::mensaPortion,
            onLog = ::logAndClose,
            onDismiss = { if (child === current) child = null },
            remainingKcal = remainingKcal,
        )
        is AddChild.Relog -> RelogSheet(
            entry = current.entry,
            date = date,
            initialSlot = slot,
            onLog = ::logAndClose,
            onDismiss = { if (child === current) child = null },
            remainingKcal = remainingKcal,
        )
        is AddChild.Barcode -> BarcodeSheet(
            resolve = addFood::resolveBarcode,
            onFood = { food ->
                // Neu von Open Food Facts: erst speichern, damit es beim nächsten Scan ohne Internet gefunden wird.
                if (food.id == 0L) {
                    addFood.saveFood(food) { saved -> child = AddChild.LogFood(saved) }
                } else {
                    child = AddChild.LogFood(food)
                }
            },
            onCreateFood = { code -> child = AddChild.EditFood(food = null, barcode = code) },
            onDismiss = { if (child === current) child = null },
        )
        is AddChild.LogFood -> key(current.food.id) {
            FoodLogSheet(
                // Der aktuelle Stand aus der Datenbank, damit z. B. der Favoriten-Stern gleich umspringt.
                food = latest.foods.firstOrNull { it.id == current.food.id } ?: current.food,
                date = date,
                initialSlot = slot,
                onLog = ::logAndClose,
                onEdit = { child = AddChild.EditFood(food = it) },
                onToggleFavorite = { food ->
                    // Vom gespeicherten Stand ausgehen: Zweimal schnell tippen schaltet dann sicher hin und zurück.
                    val saved = latest.foods.firstOrNull { it.id == food.id } ?: food
                    addFood.saveFood(saved.copy(favorite = !saved.favorite))
                },
                onDismiss = { if (child === current) child = null },
                remainingKcal = remainingKcal,
            )
        }
        is AddChild.EditFood -> FoodEditSheet(
            initial = current.food,
            // Nach dem Speichern gleich eintragen: Meist legt man ein Lebensmittel an, weil man es gerade isst.
            onSave = { food -> addFood.saveFood(food) { saved -> child = AddChild.LogFood(saved) } },
            onDismiss = { if (child === current) child = null },
            barcode = current.barcode,
            onDelete = if (current.food != null) addFood::deleteFood else null,
        )
        is AddChild.DeleteTemplate -> ConfirmDialog(
            title = "Vorlage löschen?",
            message = "${NutritionFormat.quoted(current.template.template.name)} wird gelöscht. " +
                "Einträge, die du damit angelegt hast, bleiben im Tagebuch.",
            confirmLabel = "Löschen",
            onDismiss = { if (child === current) child = null },
            onConfirm = { addFood.deleteTemplate(current.template.template) },
        )
    }
}

// --- Zuletzt ---

@Composable
private fun RecentList(
    state: AddFoodUiState,
    listState: LazyListState,
    onApplyTemplate: (TemplateWithItems) -> Unit,
    onDeleteTemplate: (TemplateWithItems) -> Unit,
    onRelog: (FoodEntry) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (state.templates.isEmpty() && state.recent.isEmpty()) {
            item(key = "empty") {
                ListMessage(
                    title = "Noch nichts eingetragen",
                    text = "Hier erscheinen deine letzten Einträge und Vorlagen. Fang mit der Mensa, einem " +
                        "Lebensmittel oder „Schnell“ an.",
                )
            }
        }
        if (state.templates.isNotEmpty()) {
            item(key = "templates-label") { ListLabel("Vorlagen", Modifier.animateItem()) }
            items(state.templates, key = { "template:${it.template.id}" }) { template ->
                TemplateRow(
                    template = template,
                    onApply = { onApplyTemplate(template) },
                    onDelete = { onDeleteTemplate(template) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent-label") { ListLabel("Zuletzt eingetragen", Modifier.animateItem()) }
            items(state.recent, key = { "recent:${it.id}" }) { entry ->
                PastEntryRow(entry = entry, onClick = { onRelog(entry) }, modifier = Modifier.animateItem())
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
    }
}

/** Eine Vorlage: Name und Teile links, Summe rechts, dahinter das Menü zum Löschen. */
@Composable
private fun TemplateRow(
    template: TemplateWithItems,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val count = template.items.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .appClickable(onClick = onApply, shape = AppFieldShape, pressedScale = 0.98f, onClickLabel = "Eintragen")
            .heightIn(min = 56.dp)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                template.template.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Die Namen der Teile sagen mehr als nur ihre Anzahl.
                template.items.joinToString(", ") { it.name }.ifEmpty { "Leer" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(NutritionFormat.kcal(template.totals.kcal), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                if (count == 1) "1 Teil" else "$count Teile",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            CircleIconButton(
                icon = Icons.Filled.MoreHoriz,
                contentDescription = "Optionen für ${template.template.name}",
                onClick = { menuOpen = true },
                style = CircleIconButtonStyle.Plain,
                size = 40.dp,
            )
            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
}

/** Ein früherer Eintrag zum erneuten Eintragen: wie im Tagebuch, mit Menge oder Mensa und Kalorien. */
@Composable
private fun PastEntryRow(entry: FoodEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SheetRow(
        title = entry.name,
        onClick = onClick,
        subtitle = NutritionFormat.entryDetail(entry),
        trailing = NutritionFormat.kcal(entry.kcal, entry.estimated),
        trailingSub = NutritionFormat.protein(entry.proteinG),
        modifier = modifier,
    )
}

// --- Mensa ---

@Composable
private fun MensaList(
    load: MenuLoad?,
    date: LocalDate,
    mensas: Set<Mensa>,
    time: MealTime,
    onTime: (MealTime) -> Unit,
    recentMensa: List<FoodEntry>,
    remainingKcal: Double?,
    listState: LazyListState,
    onRetry: () -> Unit,
    onDish: (Meal) -> Unit,
    onRecent: (FoodEntry) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "time") {
            PillSegments(
                options = MealTime.entries,
                selected = time,
                onSelect = onTime,
                label = { it.label },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        when (load) {
            // null: Das Laden startet gleich (LaunchedEffect), bis dahin sieht es aus wie Laden.
            null, MenuLoad.Loading -> item(key = "loading") { LoadingRows(count = 4) }
            MenuLoad.Offline -> item(key = "offline") {
                ListMessage(
                    title = "Keine Verbindung",
                    text = "Die Menüs kommen live von den Mensen. Prüfe deine Internetverbindung. " +
                        "Was du schon in der Mensa gegessen hast, steht unten auch ohne Internet.",
                    actionLabel = "Erneut versuchen",
                    onAction = onRetry,
                )
            }
            is MenuLoad.Loaded -> {
                val failed = load.menu.failed intersect mensas
                if (failed.isNotEmpty()) {
                    item(key = "failed") { FailedHint(failed, onRetry) }
                }
                // Hauptgerichte zuerst, dann Suppe, Salat und Dessert; innerhalb in der Reihenfolge der Mensen.
                val dishes = load.menu.meals
                    .filter { it.time == time && it.mensa in mensas }
                    .sortedWith(compareBy({ it.isSide }, { it.mensa.ordinal }))
                if (dishes.isEmpty()) {
                    item(key = "closed") { ClosedMessage(date = date, time = time) }
                } else {
                    items(dishes, key = { "dish:${it.id}" }) { meal ->
                        DishRow(meal = meal, remainingKcal = remainingKcal, onClick = { onDish(meal) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
        if (recentMensa.isNotEmpty()) {
            item(key = "recent-label") { ListLabel("Zuletzt in der Mensa", Modifier.animateItem()) }
            items(recentMensa, key = { "recent:${it.id}" }) { entry ->
                PastEntryRow(entry = entry, onClick = { onRecent(entry) }, modifier = Modifier.animateItem())
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * Ein Gericht: Name, Mensa und Linie, Kalorien und Protein. Ohne Angaben der Mensa steht die Schätzung da ("≈").
 * "passt", wenn eine Portion in das passt, was heute noch fehlt.
 */
@Composable
private fun DishRow(meal: Meal, remainingKcal: Double?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val nutrition = meal.nutrition
    val kcal = nutrition?.kcal ?: MensaEstimate.guess(meal.name, meal.description).kcal
    val fits = remainingKcal != null && remainingKcal > 0 && kcal <= remainingKcal
    SheetRow(
        title = meal.name,
        onClick = onClick,
        subtitle = "${meal.mensa.title} · ${meal.line}",
        trailing = NutritionFormat.kcal(kcal, estimated = nutrition == null),
        trailingSub = nutrition?.let { NutritionFormat.protein(it.proteinG) },
        subtitleBadge = if (fits) ({ FitsBadge() }) else null,
        modifier = modifier,
    )
}

@Composable
private fun ClosedMessage(date: LocalDate, time: MealTime) {
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    when {
        weekend -> ListMessage(
            title = "Wochenende",
            text = "Am Wochenende haben die Mensen zu. Was du zuletzt dort gegessen hast, steht unten.",
        )
        time == MealTime.Dinner -> ListMessage(
            title = "Kein Abendessen",
            text = "An diesem Tag gibt es in deinen Mensen kein Abendangebot.",
        )
        else -> ListMessage(
            title = "Kein Menü",
            text = "Für diesen Tag ist noch kein Menü veröffentlicht, oder die Mensen haben geschlossen.",
        )
    }
}

/** Einzelne Mensen nicht erreichbar, die anderen schon: ruhiger Hinweis über der Liste. */
@Composable
private fun FailedHint(failed: Set<Mensa>, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 4.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
        Text(
            text = "${failed.joinToString(", ") { it.title }} gerade nicht erreichbar",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        QuietButton(text = "Erneut", onClick = onRetry, compact = true)
    }
}

// --- Lebensmittel ---

@Composable
private fun FoodList(
    foods: List<Food>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onScan: () -> Unit,
    onCreate: () -> Unit,
    onFood: (Food) -> Unit,
) {
    val trimmed = query.trim()
    // Name und Marke, Groß- und Kleinschreibung egal. Die Datenbank sortiert schon: Favoriten zuerst, dann nach Name.
    val shown = remember(foods, trimmed) {
        if (trimmed.isEmpty()) {
            foods
        } else {
            foods.filter { it.name.contains(trimmed, ignoreCase = true) || it.brand?.contains(trimmed, ignoreCase = true) == true }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Lebensmittel suchen",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item(key = "scan") {
                CreateRow(
                    title = "Barcode scannen",
                    subtitle = "Packung scannen oder Nummer eintippen",
                    onClick = onScan,
                    icon = Icons.Outlined.QrCodeScanner,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item(key = "create") {
                CreateRow(title = "Neues Lebensmittel", subtitle = "Werte selbst eingeben", onClick = onCreate)
            }
            when {
                foods.isEmpty() -> item(key = "empty") {
                    ListMessage(
                        title = "Noch keine Lebensmittel",
                        text = "Gescannte und selbst angelegte Lebensmittel erscheinen hier, deine Favoriten ganz oben.",
                    )
                }
                shown.isEmpty() -> item(key = "none") {
                    ListMessage(title = "Nichts gefunden", text = "Kein Lebensmittel passt zu ${NutritionFormat.quoted(trimmed)}.")
                }
                else -> {
                    item(key = "foods-label") { ListLabel("Meine Lebensmittel", Modifier.animateItem()) }
                    items(shown, key = { "food:${it.id}" }) { food ->
                        SheetRow(
                            title = food.name,
                            onClick = { onFood(food) },
                            subtitle = listOfNotNull(food.brand, food.proteinG?.let { NutritionFormat.protein(it) })
                                .joinToString(" · ")
                                .ifEmpty { null },
                            trailing = NutritionFormat.kcal(food.kcal),
                            trailingSub = food.basis.label,
                            titleIcon = if (food.favorite) Icons.Filled.Star else null,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// --- Schnell ---

/** Nur Kalorien (und wenn bekannt Protein), z. B. ein Stück Kuchen bei Freunden. Der Name ist freiwillig. */
@Composable
private fun QuickForm(onLog: (name: String, kcal: Double, proteinG: Double?) -> Unit) {
    var kcalText by rememberSaveable { mutableStateOf("") }
    var proteinText by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val kcal = parseQuickKcal(kcalText)
    val protein = NutritionFormat.parse(proteinText)
    val valid = kcal != null && quickProteinError(proteinText, protein, kcal) == null
    val focus = remember { FocusRequester() }
    // Wer "Schnell" wählt, will tippen: Die Tastatur kommt gleich mit.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        QuickFields(
            kcalText = kcalText,
            onKcalChange = { kcalText = it },
            proteinText = proteinText,
            onProteinChange = { proteinText = it },
            name = name,
            onNameChange = { name = it },
            kcalModifier = Modifier.focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Für alles ohne genaue Angaben. Lieber grob geschätzt als gar nicht eingetragen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Eintragen",
            onClick = { if (kcal != null && valid) onLog(name.trim(), kcal, protein) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Kalorien, Protein und Name eines Schnell-Eintrags, beim Eintragen und beim Bearbeiten. Untereinander in voller
 * Breite: Nebeneinander wären die Felder auf 360 dp zu schmal für "Protein (freiwillig)".
 */
@Composable
internal fun QuickFields(
    kcalText: String,
    onKcalChange: (String) -> Unit,
    proteinText: String,
    onProteinChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    kcalModifier: Modifier = Modifier,
) {
    val kcal = parseQuickKcal(kcalText)
    val protein = NutritionFormat.parse(proteinText)
    Column(modifier) {
        NumberField(
            value = kcalText,
            onValueChange = onKcalChange,
            label = "Kalorien",
            suffix = "kcal",
            error = quickKcalError(kcalText),
            modifier = kcalModifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        NumberField(
            value = proteinText,
            onValueChange = onProteinChange,
            label = "Protein (freiwillig)",
            suffix = "g",
            error = quickProteinError(proteinText, protein, kcal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LabeledTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Name (freiwillig)",
            placeholder = NutritionFormat.QUICK_NAME,
        )
    }
}

/** Mehr auf einmal ist sicher ein Tippfehler (eine ganze Pizza hat etwa 1200 kcal). */
private const val MAX_QUICK_KCAL = 10000

/** Die Kalorien eines Schnell-Eintrags, oder null, wenn das Feld leer, 0 oder unrealistisch groß ist. */
internal fun parseQuickKcal(text: String): Double? = NutritionFormat.parse(text)?.takeIf { it > 0 && it <= MAX_QUICK_KCAL }

/** Fehler zum Kalorienfeld. Ein leeres Feld ist (noch) kein Fehler, nur "Eintragen" bleibt aus. */
internal fun quickKcalError(text: String): String? =
    if (text.isNotBlank() && parseQuickKcal(text) == null) "Zwischen 1 und $MAX_QUICK_KCAL kcal" else null

/**
 * Prüft das Protein. 1 g Protein hat etwa 4 kcal: Mehr Protein, als die Kalorien hergeben, ist ein Tippfehler
 * (z. B. Kalorien und Protein vertauscht). 10 % Spielraum, weil Angaben auf Packungen gerundet sind.
 */
internal fun quickProteinError(text: String, protein: Double?, kcal: Double?): String? = when {
    text.isBlank() -> null
    protein == null || protein > 1000 -> "Ungültige Zahl"
    kcal != null && protein * 4 > kcal * 1.1 -> "Mehr Protein, als die Kalorien hergeben (1 g ≈ 4 kcal)"
    else -> null
}

// --- kleine Helfer ---

/** Zwischenüberschrift in den Listen, bündig mit dem Sheet-Titel. */
@Composable
private fun ListLabel(text: String, modifier: Modifier = Modifier) {
    SectionLabel(text, modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))
}
