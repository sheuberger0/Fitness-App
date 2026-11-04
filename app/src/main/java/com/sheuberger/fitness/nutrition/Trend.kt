package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.data.DayTotal
import java.time.LocalDate
import kotlin.math.abs

/** Was die letzten Wochen über Gewicht und Essen sagen. Fehlen Daten, sind die Felder null. */
data class TrendSummary(
    /** Durchschnittsgewicht der letzten 7 Tage (heute eingeschlossen). Eine Wiegung reicht. */
    val avgWeightKg: Double?,
    /**
     * Durchschnitt der letzten 7 Tage minus Durchschnitt der 7 Tage davor, also kg pro Woche.
     * Braucht in beiden Wochen mindestens 3 Wiegungen.
     */
    val weeklyChangeKg: Double?,
    /** Durchschnittliche Kalorien der Tage mit Einträgen in den 7 Tagen vor heute. */
    val avgIntakeKcal: Double?,
    /** An wie vielen der 7 Tage vor heute etwas eingetragen ist. */
    val loggedDays: Int,
    /** Vorschlag für ein neues Kalorienziel, oder null (zu wenig Daten, Ziel passt, oder noch keine 14 Tage her). */
    val suggestion: TargetSuggestion?,
)

data class TargetSuggestion(
    val newTargetKcal: Int,
    /** Um so viel ändert sich das Ziel: newTargetKcal minus bisheriges Ziel. */
    val deltaKcal: Int,
    val actualWeeklyChangeKg: Double,
    val goalWeeklyChangeKg: Double,
)

/**
 * Vergleicht, wie sich dein Gewicht wirklich ändert, mit deinem Ziel, und schlägt bei Bedarf ein neues
 * Kalorienziel vor.
 *
 * Warum Wochendurchschnitte: Das Gewicht schwankt von Tag zu Tag um 1 kg und mehr (Wasser, Salz, Essen im
 * Magen). Der Durchschnitt von mindestens 3 Wiegungen pro Woche glättet das.
 */
object Trend {
    /** Etwa so viele kcal entsprechen 1 kg Körpergewicht. */
    const val KCAL_PER_KG = 7700.0

    /** So viele Wiegungen braucht eine Woche, damit ihr Durchschnitt für die Änderung zählt. */
    private const val MIN_WEIGHTS = 3

    /** So viele Tage mit Einträgen braucht die letzte Woche: Fehlen Tage, stimmt der Durchschnitt nicht. */
    private const val MIN_LOGGED_DAYS = 5

    /** Nach dem Übernehmen oder Verschieben eines Vorschlags so lange Ruhe: Der Körper braucht Zeit zum Reagieren. */
    private const val REVIEW_PAUSE_DAYS = 14L

    /** Kleinere Änderungen lohnen sich nicht: 50 kcal sind etwa ein Teelöffel Öl, so genau misst niemand. */
    private const val MIN_DELTA = 50

    /** Höchstens so viel auf einmal, damit ein Ausreißer in den Daten das Ziel nicht verdreht. */
    private const val MAX_DELTA = 300

    fun summarize(
        weights: List<BodyWeightEntry>,
        totals: List<DayTotal>,
        goals: NutritionGoals,
        today: LocalDate,
    ): TrendSummary {
        val thisWeek = weights.filter { it.date.isBetween(today.minusDays(6), today) }.map { it.weightKg }
        val lastWeek = weights.filter { it.date.isBetween(today.minusDays(13), today.minusDays(7)) }.map { it.weightKg }
        val weeklyChange = if (thisWeek.size >= MIN_WEIGHTS && lastWeek.size >= MIN_WEIGHTS) {
            thisWeek.average() - lastWeek.average()
        } else {
            null
        }

        // Heute zählt nicht mit: Der Tag ist noch nicht vorbei und würde den Durchschnitt nach unten ziehen.
        val days = totals.filter { it.date.isBetween(today.minusDays(7), today.minusDays(1)) }
        val avgIntake = if (days.isEmpty()) null else days.map { it.kcal }.average()

        return TrendSummary(
            avgWeightKg = if (thisWeek.isEmpty()) null else thisWeek.average(),
            weeklyChangeKg = weeklyChange,
            avgIntakeKcal = avgIntake,
            loggedDays = days.size,
            suggestion = suggest(weeklyChange, days.size, goals, today),
        )
    }

    private fun suggest(weeklyChange: Double?, loggedDays: Int, goals: NutritionGoals, today: LocalDate): TargetSuggestion? {
        if (weeklyChange == null || loggedDays < MIN_LOGGED_DAYS) return null
        val review = goals.lastReview
        if (review != null && today.isBefore(review.plusDays(REVIEW_PAUSE_DAYS))) return null

        // Fehlen pro Woche 0,2 kg zum Ziel, fehlen 0,2 · 7700 kcal pro Woche, also 220 kcal pro Tag.
        val perDay = (goals.weeklyGoalKg - weeklyChange) * KCAL_PER_KG / 7
        // Erst begrenzen, dann auf 10 runden (300 ist durch 10 teilbar, das Ergebnis ist dasselbe).
        // So kann auch ein absurder Wert (Tippfehler beim Gewicht) beim Runden nicht überlaufen.
        val delta = Math.round(perDay.coerceIn(-MAX_DELTA.toDouble(), MAX_DELTA.toDouble()) / 10).toInt() * 10
        if (abs(delta) < MIN_DELTA) return null

        val newTarget = (goals.kcalTarget + delta).coerceIn(NutritionGoals.MIN_KCAL, NutritionGoals.MAX_KCAL)
        // Schon an der Grenze (z. B. 6000 kcal und es sollen mehr werden): Es gibt nichts vorzuschlagen.
        if (newTarget == goals.kcalTarget) return null
        return TargetSuggestion(
            newTargetKcal = newTarget,
            deltaKcal = newTarget - goals.kcalTarget,
            actualWeeklyChangeKg = weeklyChange,
            goalWeeklyChangeKg = goals.weeklyGoalKg,
        )
    }

    /** [from] und [to] zählen mit. */
    private fun LocalDate.isBetween(from: LocalDate, to: LocalDate) = !isBefore(from) && !isAfter(to)
}
