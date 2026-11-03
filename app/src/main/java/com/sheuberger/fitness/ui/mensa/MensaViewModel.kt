package com.sheuberger.fitness.ui.mensa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.mensa.DayMenu
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaPreferences
import com.sheuberger.fitness.mensa.MensaRanking
import com.sheuberger.fitness.mensa.MensaSettings
import com.sheuberger.fitness.mensa.Ranking
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    /** Hat die Mensa an diesem Tag/zu dieser Zeit überhaupt etwas im Angebot? */
    val hasMenu: Boolean
        get() = (load as? MenuLoad.Loaded)?.menu?.meals?.any { it.time == time && it.mensa in prefs.mensas } == true
}

private data class Selection(val date: LocalDate, val time: MealTime)

class MensaViewModel(
    private val client: MensaClient,
    private val settings: MensaSettings,
    private val now: () -> java.time.LocalDateTime = java.time.LocalDateTime::now,
) : ViewModel() {

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

    val uiState: StateFlow<MensaUiState> =
        combine(selection, load, refreshing, settings.preferences) { sel, loads, isRefreshing, prefs ->
            val current = loads[sel.date] ?: MenuLoad.Loading
            MensaUiState(
                days = days,
                today = today,
                date = sel.date,
                time = sel.time,
                prefs = prefs,
                load = current,
                refreshing = isRefreshing,
                ranking = (current as? MenuLoad.Loaded)?.let { loaded ->
                    MensaRanking.rank(
                        meals = loaded.menu.meals.filter { it.time == sel.time },
                        group = prefs.group,
                        budget = prefs.budget,
                        by = prefs.rankBy,
                        mensas = prefs.mensas,
                    )
                },
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
