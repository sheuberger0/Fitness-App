package com.sheuberger.fitness.ui.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.mensa.DayMenu
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaPreferences
import com.sheuberger.fitness.mensa.MensaRanking
import com.sheuberger.fitness.mensa.MensaSettings
import com.sheuberger.fitness.mensa.Ranking
import com.sheuberger.fitness.nutrition.NutritionSettings
import com.sheuberger.fitness.nutrition.PortionValues
import com.sheuberger.fitness.ui.nutrition.defaultViewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Ergebnis des Ladens für den gewählten Tag. */
sealed interface MenuLoad {
    data object Loading : MenuLoad
    data class Loaded(val menu: DayMenu) : MenuLoad
    /** Keine einzige Mensa erreichbar, meist kein Internet. */
    data object Offline : MenuLoad
}

data class MensaUiState(
    val days: List<LocalDate>,
    val today: LocalDate,
    val date: LocalDate,
    val time: MealTime,
    val prefs: MensaPreferences,
    val load: MenuLoad,
    val refreshing: Boolean,
    val ranking: Ranking?,
    /**
     * Was heute noch bis zum Kalorienziel fehlt (negativ: so viel darüber). Nur, wenn der gewählte Tag heute ist:
     * Für einen anderen Tag sagt der heutige Stand nichts. null auch, solange das Tagebuch noch lädt.
     */
    val remainingKcal: Double? = null,
    /** Gerichte ([Meal.id]), deren Portion noch ins heutige Ziel passt (siehe [MensaViewModel.fittingMeals]). */
    val fitting: Set<String> = emptySet(),
) {
    /** Hat die Mensa an diesem Tag/zu dieser Zeit überhaupt etwas im Angebot? */
    val hasMenu: Boolean
        get() = (load as? MenuLoad.Loaded)?.menu?.meals?.any { it.time == time && it.mensa in prefs.mensas } == true
}

private data class Selection(val date: LocalDate, val time: MealTime)

/**
 * Rangliste der Mensa-Gerichte und, seit dem Kalorien-Tracker, wie viel heute noch bis zum Ziel fehlt.
 * Gerichte lassen sich von hier aus direkt ins Ernährungstagebuch eintragen.
 */
class MensaViewModel(
    private val client: MensaClient,
    private val settings: MensaSettings,
    private val nutrition: NutritionRepository,
    nutritionSettings: NutritionSettings,
    private val now: () -> java.time.LocalDateTime = java.time.LocalDateTime::now,
    scope: CoroutineScope = defaultViewModelScope(),
) : ViewModel(scope) {

    private val today = now().toLocalDate()
    private val days = weekdays(today)

    private val selection = MutableStateFlow(
        Selection(
            date = if (today in days) today else days.first(),
            // Nach dem Mittag (ab 14 Uhr) zeigt die Liste gleich das Abendessen.
            time = if (today in days && now().toLocalTime() >= LocalTime.of(14, 0)) MealTime.Dinner else MealTime.Lunch,
        ),
    )
    private val load = MutableStateFlow<Map<LocalDate, MenuLoad>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private var loadJob: Job? = null

    // Ziel minus alles, was heute im Tagebuch steht. Aktualisiert sich von selbst bei jedem neuen Eintrag,
    // auch bei einem aus dem Essen-Tab.
    private val remainingToday: Flow<Double?> =
        combine(nutrition.day(today), nutritionSettings.goals) { entries, goals ->
            val remaining: Double? = goals.kcalTarget - entries.sumOf { it.kcal }
            remaining
        }
            // Die Menüs warten nicht auf das Tagebuch (beim ersten Start nach einem Update öffnet sich die Datenbank
            // erst). Bis es da ist, oder falls es sich nicht lesen lässt, fehlt nur der Hinweis "Heute noch …".
            .onStart { emit(null) }
            .catch { emit(null) }

    val uiState: StateFlow<MensaUiState> =
        combine(selection, load, refreshing, settings.preferences, remainingToday) { sel, loads, isRefreshing, prefs, remaining ->
            val current = loads[sel.date] ?: MenuLoad.Loading
            val ranking = (current as? MenuLoad.Loaded)?.let { loaded ->
                MensaRanking.rank(
                    meals = loaded.menu.meals.filter { it.time == sel.time },
                    group = prefs.group,
                    budget = prefs.budget,
                    by = prefs.rankBy,
                    mensas = prefs.mensas,
                )
            }
            val remainingKcal = if (sel.date == today) remaining else null
            MensaUiState(
                days = days,
                today = today,
                date = sel.date,
                time = sel.time,
                prefs = prefs,
                load = current,
                refreshing = isRefreshing,
                ranking = ranking,
                remainingKcal = remainingKcal,
                fitting = fittingMeals(ranking, remainingKcal),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MensaUiState(days, today, selection.value.date, selection.value.time, settings.preferences.value,
                MenuLoad.Loading, refreshing = false, ranking = null),
        )

    init {
        fetch(selection.value.date)
    }

    fun selectDate(date: LocalDate) {
        selection.update { it.copy(date = date) }
        if (load.value[date] !is MenuLoad.Loaded) fetch(date)
    }

    fun selectTime(time: MealTime) = selection.update { it.copy(time = time) }

    fun updatePrefs(transform: (MensaPreferences) -> MensaPreferences) = settings.update(transform)

    /** Ziehen zum Aktualisieren oder "Erneut versuchen". */
    fun refresh() = fetch(selection.value.date, refresh = true)

    // --- Eintragen ---

    /** Startwerte für das Sheet "Eintragen": dein letzter Eintrag, die Angaben der Mensa oder eine Schätzung. */
    suspend fun mensaPortion(meal: Meal): PortionValues = nutrition.mensaPortion(meal)

    /**
     * Trägt ein Gericht ins Tagebuch ein. [onLogged] bekommt den Eintrag mit seiner ID zurück, z. B. für
     * "Rückgängig". Läuft im viewModelScope: Das Speichern wird fertig, auch wenn du gleich den Tab wechselst.
     */
    fun log(entry: FoodEntry, onLogged: (FoodEntry) -> Unit = {}) {
        viewModelScope.launch {
            val id = nutrition.log(entry)
            onLogged(entry.copy(id = id))
        }
    }

    /** Nimmt einen eben eingetragenen Eintrag wieder heraus ("Rückgängig" in der Bestätigung). */
    fun undoLog(entry: FoodEntry) {
        viewModelScope.launch { nutrition.deleteEntry(entry) }
    }

    private fun fetch(date: LocalDate, refresh: Boolean = false) {
        // Schon im Zwischenspeicher? Dann sofort zeigen, ohne Ladezustand.
        client.cached(date)?.takeIf { !refresh }?.let { cached ->
            load.update { it + (date to MenuLoad.Loaded(cached)) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hadData = load.value[date] is MenuLoad.Loaded
            if (hadData) refreshing.value = true else load.update { it + (date to MenuLoad.Loading) }
            try {
                val menu = client.load(date, refresh)
                val allFailed = menu.meals.isEmpty() && menu.failed.isNotEmpty()
                load.update {
                    // Beim Aktualisieren ohne Netz lieber die alten Daten behalten.
                    if (allFailed && hadData) it else it + (date to if (allFailed) MenuLoad.Offline else MenuLoad.Loaded(menu))
                }
            } finally {
                // Auch wenn das Laden abgebrochen wird (anderer Tag gewählt), dreht sich der Kreis nicht weiter.
                refreshing.value = false
            }
        }
    }

    companion object {
        /**
         * Welche Gerichte noch ins heutige Ziel passen: Die Portion hat laut Mensa höchstens so viele kcal, wie
         * heute noch fehlen. Gerichte ohne Nährwerte zählen nicht mit, über sie lässt sich nichts sagen.
         *
         * Passen alle (z. B. morgens, wenn noch 3800 kcal fehlen) oder keines, bleibt die Menge leer: Ein "passt"
         * an jedem Gericht wäre nur Rauschen. Wirklich hilft es abends, wenn nur noch manche Gerichte passen.
         */
        fun fittingMeals(ranking: Ranking?, remainingKcal: Double?): Set<String> {
            if (ranking == null || remainingKcal == null) return emptySet()
            val fits = ranking.ranked.filter { it.nutrition.kcal <= remainingKcal }
            return if (fits.size == ranking.ranked.size) emptySet() else fits.mapTo(mutableSetOf()) { it.meal.id }
        }

        /** Montag bis Freitag dieser Woche; am Wochenende schon die nächste Woche. */
        fun weekdays(today: LocalDate): List<LocalDate> {
            val start = if (today.dayOfWeek >= DayOfWeek.SATURDAY) {
                today.plusDays(8L - today.dayOfWeek.value)
            } else {
                today.minusDays(today.dayOfWeek.value - 1L)
            }
            return (0L until 5L).map { start.plusDays(it) }
        }
    }
}
