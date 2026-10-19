package com.sheuberger.fitness.ui.bodyweight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.ui.components.ChartPoint
import com.sheuberger.fitness.ui.components.TimeRange
import com.sheuberger.fitness.ui.components.inRange
import com.sheuberger.fitness.ui.components.toChartPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Kennzahlen für den gewählten Zeitraum. */
data class RangeStats(
    val start: ChartPoint,
    val end: ChartPoint,
    val min: Double,
    val max: Double,
    val average: Double,
) {
    /** Veränderung vom ersten bis zum letzten Eintrag im Zeitraum. */
    val change: Double get() = end.value - start.value
}

data class BodyWeightUiState(
    val loading: Boolean = true,
    /** Alle Einträge, der neueste zuerst (so wie die Liste sie anzeigt). */
    val entries: List<BodyWeightEntry> = emptyList(),
    val range: TimeRange = TimeRange.ThreeMonths,
    /** Punkte für den Graphen, nur im gewählten Zeitraum, älteste zuerst. */
    val points: List<ChartPoint> = emptyList(),
    val stats: RangeStats? = null,
    /** Durchschnitt der letzten 7 Tage (inkl. heute), null ohne Einträge in dieser Zeit. */
    val weekAverage: Double? = null,
) {
    val latest: BodyWeightEntry? get() = entries.firstOrNull()
}

class BodyWeightViewModel(private val repository: FitnessRepository) : ViewModel() {

    // Der Zeitraum ist reiner Oberflächenzustand, wird aber hier gehalten,
    // damit er beim Drehen des Bildschirms erhalten bleibt.
    private val range = MutableStateFlow(TimeRange.ThreeMonths)

    val uiState: StateFlow<BodyWeightUiState> =
        combine(repository.bodyWeights, range) { entries, range ->
            val today = LocalDate.now()
            val points = entries.toChartPoints().inRange(range, today)
            val weekStart = today.minusDays(6)
            val lastWeek = entries.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }
            BodyWeightUiState(
                loading = false,
                entries = entries.sortedWith(compareByDescending<BodyWeightEntry> { it.date }.thenByDescending { it.id }),
                range = range,
                points = points,
                stats = points.takeIf { it.isNotEmpty() }?.let { list ->
                    RangeStats(
                        start = list.first(),
                        end = list.last(),
                        min = list.minOf { it.value },
                        max = list.maxOf { it.value },
                        average = list.map { it.value }.average(),
                    )
                },
                weekAverage = lastWeek.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BodyWeightUiState(),
        )

    fun selectRange(newRange: TimeRange) {
        range.value = newRange
    }

    /** Neuer Eintrag (id = 0) oder geänderter Eintrag. Ein anderer Eintrag am selben Tag wird ersetzt. */
    fun save(entry: BodyWeightEntry) {
        viewModelScope.launch { repository.saveBodyWeight(entry) }
    }

    fun delete(entry: BodyWeightEntry) {
        viewModelScope.launch { repository.deleteBodyWeight(entry) }
    }

    /** Macht ein Löschen rückgängig: Der Eintrag wird mit neuer ID wieder angelegt. */
    fun restore(entry: BodyWeightEntry) {
        viewModelScope.launch { repository.saveBodyWeight(entry.copy(id = 0)) }
    }
}
