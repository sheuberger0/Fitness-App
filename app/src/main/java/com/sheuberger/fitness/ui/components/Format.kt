package com.sheuberger.fitness.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Alle Formatierungen an einem Ort, damit Zahlen und Daten überall gleich aussehen. */
object Format {
    private val locale = Locale.GERMANY
    private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(locale))
    private val longDate = DateTimeFormatter.ofPattern("d. MMM yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("d. MMM", locale)
    private val weekdayDate = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMM yy", locale)

    /** 82.5 -> "82,5" */
    fun number(value: Double): String = numberFormat.format(value)

    /** 82.5 -> "82,5 kg" */
    fun kg(value: Double): String = "${number(value)} kg"

    /** 2.5 -> "+2,5 kg", -1.0 -> "−1 kg", 0.0 -> "±0 kg" */
    fun signedKg(delta: Double): String = when {
        abs(delta) < 0.005 -> "±0 kg"
        delta > 0 -> "+${kg(delta)}"
        else -> "−${kg(-delta)}"
    }

    fun setLine(weightKg: Double, reps: Int?): String =
        if (reps != null) "${kg(weightKg)} × $reps" else kg(weightKg)

    fun date(date: LocalDate): String = longDate.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun weekdayDate(date: LocalDate): String = weekdayDate.format(date)
    fun monthYear(date: LocalDate): String = monthYear.format(date)

    /** "Heute", "Gestern", "vor 3 Tagen" oder ein Datum. */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days == 0L -> "Heute"
            days == 1L -> "Gestern"
            days in 2..6 -> "vor $days Tagen"
            date.year == today.year -> shortDate(date)
            else -> date(date)
        }
    }

    /** Akzeptiert "82,5" und "82.5". Gibt null zurück, wenn die Eingabe ungültig ist. */
    fun parseWeight(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }

    fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..999 }

    fun exerciseCount(count: Int): String = if (count == 1) "1 Übung" else "$count Übungen"
}
