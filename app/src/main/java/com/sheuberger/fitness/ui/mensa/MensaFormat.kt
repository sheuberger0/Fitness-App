package com.sheuberger.fitness.ui.mensa

import com.sheuberger.fitness.mensa.RankBy
import com.sheuberger.fitness.mensa.RankedMeal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formate für die Mensa. Preise wie in der Schweiz üblich mit Punkt ("9.50 CHF"),
 * sonst deutsche Schreibweise wie im Rest der App.
 */
object MensaFormat {
    private val swiss = DecimalFormat("0.00", DecimalFormatSymbols(Locale.ROOT))
    private val oneDecimal = DecimalFormat("0.0", DecimalFormatSymbols(Locale.GERMANY))
    private val longDay = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)
    private val weekdays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun chf(value: Double): String = "${swiss.format(value)} CHF"
    fun grams(value: Double): String = "${value.roundToInt()} g"
    fun decimal(value: Double): String = oneDecimal.format(value)
    fun longDay(date: LocalDate): String = longDay.format(date)
    fun shortWeekday(date: LocalDate): String = weekdays[date.dayOfWeek.value - 1]

    /** Zahl ohne Einheit für eine Kennzahl. */
    fun metricValue(meal: RankedMeal, by: RankBy): String = when (by) {
        RankBy.Protein -> meal.nutrition.proteinG.roundToInt().toString()
        RankBy.Calories -> meal.nutrition.kcal.roundToInt().toString()
        RankBy.Value -> decimal(meal.proteinPerFranc)
    }
}
