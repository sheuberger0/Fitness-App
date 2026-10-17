package com.sheuberger.fitness.ui.components

import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.data.ExerciseLog
import java.time.LocalDate

/** Ein Punkt im Graphen: ein Datum und ein Wert (Gewicht in kg). */
data class ChartPoint(val date: LocalDate, val value: Double)

/** Zeiträume für die Graphen. */
enum class TimeRange(val label: String, val months: Long?) {
    OneMonth("1M", 1),
    ThreeMonths("3M", 3),
    SixMonths("6M", 6),
    OneYear("1J", 12),
    All("Alle", null);

    val description: String
        get() = when (this) {
            OneMonth -> "im letzten Monat"
            ThreeMonths -> "in den letzten 3 Monaten"
            SixMonths -> "in den letzten 6 Monaten"
            OneYear -> "im letzten Jahr"
            All -> "insgesamt"
        }
}

fun List<ChartPoint>.inRange(range: TimeRange, today: LocalDate = LocalDate.now()): List<ChartPoint> {
    val months = range.months ?: return this
    val start = today.minusMonths(months)
    return filter { !it.date.isBefore(start) }
}

/**
 * Pro Tag zählt das schwerste Gewicht. Trägt man an einem Tag mehrere Sätze ein,
 * erscheint im Graphen also der beste Satz des Tages.
 */
fun List<ExerciseLog>.dailyBest(): List<ChartPoint> =
    groupBy { it.date }
        .map { (date, logs) -> ChartPoint(date, logs.maxOf { it.weightKg }) }
        .sortedBy { it.date }

fun List<BodyWeightEntry>.toChartPoints(): List<ChartPoint> =
    map { ChartPoint(it.date, it.weightKg) }.sortedBy { it.date }

/** Der neueste Eintrag (bei gleichem Datum der zuletzt angelegte). */
fun List<ExerciseLog>.latest(): ExerciseLog? = maxWithOrNull(compareBy({ it.date }, { it.id }))

/** Der beste Eintrag: höchstes Gewicht, bei Gleichstand der frühere (dort wurde der Rekord aufgestellt). */
fun List<ExerciseLog>.personalBest(): ExerciseLog? =
    maxWithOrNull(compareBy<ExerciseLog> { it.weightKg }.thenByDescending { it.date }.thenByDescending { it.id })
