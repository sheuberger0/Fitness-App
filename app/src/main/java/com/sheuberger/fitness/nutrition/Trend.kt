package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.data.DayTotal
import java.time.LocalDate

/** Was die letzten Wochen über Gewicht und Essen sagen. Fehlen Daten, sind die Felder null. */
data class TrendSummary(
    /** Durchschnittsgewicht der letzten 7 Tage. */
    val avgWeightKg: Double?,
    /** Durchschnitt der letzten 7 Tage minus Durchschnitt der 7 Tage davor, also kg pro Woche. */
    val weeklyChangeKg: Double?,
    /** Durchschnittliche Kalorien der Tage mit Einträgen in den letzten 7 Tagen. */
    val avgIntakeKcal: Double?,
    /** An wie vielen der letzten 7 Tage etwas eingetragen ist. */
    val loggedDays: Int,
    /** Vorschlag für ein neues Kalorienziel, oder null (zu wenig Daten, Ziel passt, oder noch keine 14 Tage her). */
    val suggestion: TargetSuggestion?,
)

data class TargetSuggestion(
    val newTargetKcal: Int,
    val deltaKcal: Int,
    val actualWeeklyChangeKg: Double,
    val goalWeeklyChangeKg: Double,
)

/** Platzhalter: Die Berechnung kommt in einem eigenen Schritt (mit Tests). */
object Trend {
    /** Etwa so viele kcal entsprechen 1 kg Körpergewicht. */
    const val KCAL_PER_KG = 7700.0

    fun summarize(
        weights: List<BodyWeightEntry>,
        totals: List<DayTotal>,
        goals: NutritionGoals,
        today: LocalDate,
    ): TrendSummary = TrendSummary(null, null, null, 0, null)
}
