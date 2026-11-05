package com.sheuberger.fitness.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.nutrition.NutritionSettings
import com.sheuberger.fitness.nutrition.TargetSuggestion
import com.sheuberger.fitness.nutrition.Totals
import com.sheuberger.fitness.nutrition.Trend
import com.sheuberger.fitness.nutrition.TrendSummary
import com.sheuberger.fitness.nutrition.slotForTime
import com.sheuberger.fitness.nutrition.totals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Wie der eingebaute viewModelScope: läuft auf dem Haupt-Thread und endet mit dem ViewModel.
 * Die ViewModels nehmen den Scope als Parameter, damit Tests einen eigenen übergeben können, der nicht auf
 * den Haupt-Thread wartet (der ist im Test mit dem Test selbst beschäftigt).
 */
internal fun defaultViewModelScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Das Datum von heute und der gewählte Tag. */
private data class DaySelection(val today: LocalDate, val date: LocalDate)

/** Eine Mahlzeit der Tagesansicht mit ihren Einträgen. */
data class MealSection(val slot: MealSlot, val entries: List<FoodEntry>) {
    val totals: Totals = entries.totals()
}

data class NutritionUiState(
    val today: LocalDate,
    val loading: Boolean = true,
    val date: LocalDate = today,
    val entries: List<FoodEntry> = emptyList(),
    val goals: NutritionGoals = NutritionGoals(),
    /** Gibt es gestern Einträge? Dann bietet ein leerer heutiger Tag "Gestern übernehmen" an. */
    val yesterdayLogged: Boolean = false,
    val trend: TrendSummary? = null,
    /** Kalorien von gestern, für die Karte "Gestern übernehmen". null: gestern nichts eingetragen. */
    val yesterdayKcal: Double? = null,
    /**
     * Gibt es überhaupt schon Einträge? Wenn nicht, begrüßt der Tab mit einer kurzen Einführung.
     * Standard true, damit die Einführung beim Laden nicht kurz aufblitzt.
     */
    val hasHistory: Boolean = true,
) {
    // Einmal beim Erzeugen berechnet (nicht bei jedem Zugriff), die Oberfläche liest sie mehrmals.
    val totals: Totals = entries.totals()
    val sections: List<MealSection> = MealSlot.entries.map { slot -> MealSection(slot, entries.filter { it.meal == slot }) }

    val isToday: Boolean get() = date == today

    /** Positiv: so viel fehlt noch bis zum Ziel. Negativ: so viel liegt der Tag darüber. */
    val remainingKcal: Double get() = goals.kcalTarget - totals.kcal

    val canGoForward: Boolean get() = date < today
    val canCopyYesterday: Boolean get() = isToday && entries.isEmpty() && yesterdayLogged
    val canCopyToToday: Boolean get() = date < today && entries.isNotEmpty()

    /**
     * Welche Mahlzeit das "+" vorschlägt: heute nach der Uhrzeit, an anderen Tagen die erste Mahlzeit,
     * in der noch nichts steht (meist trägt man dann etwas Vergessenes nach).
     */
    fun suggestedSlot(time: LocalTime): MealSlot =
        if (isToday) slotForTime(time) else sections.firstOrNull { it.entries.isEmpty() }?.slot ?: MealSlot.SNACK
}

/**
 * Die Tagesansicht im Tab "Essen": Einträge des gewählten Tages, Ziele und der Trend der letzten Wochen.
 * Neue Einträge legt das Sheet "Eintragen" an ([AddFoodViewModel]); hier wird nur bearbeitet und gelöscht.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    private val nutrition: NutritionRepository,
    private val settings: NutritionSettings,
    fitness: FitnessRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    scope: CoroutineScope = defaultViewModelScope(),
) : ViewModel(scope) {

    // Heute und der gewählte Tag in einem Zustand, damit beide immer zueinander passen (siehe [refreshToday]).
    private val selection = MutableStateFlow(now().toLocalDate().let { DaySelection(today = it, date = it) })

    // Auswahl und Einträge reisen zusammen: Sonst stünde beim Tageswechsel kurz der neue Tag über den alten Einträgen.
    private val day = selection.flatMapLatest { sel -> nutrition.day(sel.date).map { sel to it } }

    // Die letzten 14 Tage: für den Trend und um zu sehen, ob gestern etwas eingetragen ist.
    private val recentTotals = selection.map { it.today }.distinctUntilChanged()
        .flatMapLatest { today -> nutrition.dailyTotals(today.minusDays(13), today) }

    // "Zuletzt" ist leer, solange es noch gar keinen Eintrag gibt. Die Abfrage gibt es schon, sie kostet nichts extra.
    private val hasHistory = nutrition.recentEntries.map { it.isNotEmpty() }.distinctUntilChanged()

    val uiState: StateFlow<NutritionUiState> =
        combine(day, settings.goals, recentTotals, fitness.bodyWeights, hasHistory) {
                (sel, entries), goals, totals, weights, history ->
            val yesterday = totals.firstOrNull { it.date == sel.today.minusDays(1) }
            NutritionUiState(
                today = sel.today,
                loading = false,
                date = sel.date,
                entries = entries,
                goals = goals,
                yesterdayLogged = yesterday != null,
                trend = Trend.summarize(weights, totals, goals, sel.today),
                yesterdayKcal = yesterday?.kcal,
                hasHistory = history,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState(today = selection.value.today))

    // --- Tag wählen ---

    /** Zukünftige Tage gibt es nicht: Eingetragen wird, was schon gegessen ist. */
    fun selectDate(date: LocalDate) {
        selection.update { it.copy(date = minOf(date, it.today)) }
    }

    fun previousDay() = selection.update { it.copy(date = it.date.minusDays(1)) }

    fun nextDay() = selectDate(selection.value.date.plusDays(1))

    fun showToday() = selection.update { it.copy(date = it.today) }

    /**
     * Prüft, ob inzwischen ein neuer Tag ist. Der Screen ruft das bei jedem Öffnen auf: Android lässt die App oft
     * über Nacht im Hintergrund weiterlaufen, und am Morgen soll "Heute" wirklich heute sein und nicht gestern.
     */
    fun refreshToday() {
        val newToday = now().toLocalDate()
        selection.update { sel ->
            when {
                sel.today == newToday -> sel
                // Wer "Heute" offen hatte, sieht nach Mitternacht wieder "Heute" (den neuen, leeren Tag).
                sel.date == sel.today -> DaySelection(today = newToday, date = newToday)
                // Ein anderer Tag bleibt gewählt. minOf, falls die Uhr zurückgestellt wurde (andere Zeitzone).
                else -> DaySelection(today = newToday, date = minOf(sel.date, newToday))
            }
        }
    }

    // --- Einträge ---

    fun updateEntry(entry: FoodEntry) {
        viewModelScope.launch { nutrition.updateEntry(entry) }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch { nutrition.deleteEntry(entry) }
    }

    /** Macht ein Löschen rückgängig. Mit der alten ID, damit der Eintrag wieder an seiner Stelle steht. */
    fun restoreEntry(entry: FoodEntry) {
        viewModelScope.launch { nutrition.log(entry) }
    }

    /** "Als Vorlage speichern": die Einträge einer Mahlzeit, z. B. als "Frühstück Standard". */
    fun saveTemplate(name: String, section: MealSection, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            nutrition.saveTemplate(name, section.slot, section.entries)
            onSaved()
        }
    }

    /** Kopiert alle Einträge eines Tages, z. B. "Gestern übernehmen". [onCopied] bekommt die Anzahl. */
    fun copyDay(from: LocalDate, to: LocalDate, onCopied: (Int) -> Unit = {}) {
        viewModelScope.launch { onCopied(nutrition.copyDay(from, to)) }
    }

    // --- Ziele ---

    /**
     * Ziele aus dem Sheet "Ziele". Wer das Kalorienziel von Hand ändert, bekommt wie nach "Übernehmen" 14 Tage
     * Ruhe: Die Daten der letzten Wochen gehören noch zum alten Ziel. Ein Vorschlag gleich danach würde die
     * eigene Änderung noch einmal draufrechnen (von Hand 3800 auf 4000, dann "Vorschlag: Ziel auf 4220 kcal").
     */
    fun updateGoals(transform: (NutritionGoals) -> NutritionGoals) = settings.update { old ->
        val next = transform(old)
        if (next.kcalTarget != old.kcalTarget) next.copy(lastReview = now().toLocalDate()) else next
    }

    /** "Übernehmen": neues Kalorienziel. Danach ist 14 Tage Ruhe, bis der nächste Vorschlag kommt. */
    fun acceptSuggestion(suggestion: TargetSuggestion) =
        settings.update { it.copy(kcalTarget = suggestion.newTargetKcal, lastReview = now().toLocalDate()) }

    /** "Später": Ziel bleibt, der Vorschlag kommt frühestens in 14 Tagen wieder. */
    fun postponeSuggestion() = settings.update { it.copy(lastReview = now().toLocalDate()) }
}
