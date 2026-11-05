package com.sheuberger.fitness.ui.nutrition

import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.nutrition.PortionOrigin
import com.sheuberger.fitness.nutrition.TargetSuggestion
import com.sheuberger.fitness.nutrition.Trend
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.mensa.MensaFormat
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Formate und Eingaben für den Kalorien-Tracker.
 * Kalorien stehen ohne Tausenderpunkt da ("3800 kcal"), wie in der Mensa-Rangliste. Kommazahlen haben ein
 * Komma wie im Rest der App ("1,5 Portionen").
 */
object NutritionFormat {
    private val symbols = DecimalFormatSymbols(Locale.GERMANY)

    // Ohne Tausenderpunkt: Diese Texte landen auch in Eingabefeldern und müssen sich wieder einlesen lassen.
    // HALF_UP rundet 0,25 auf 0,3 wie in der Schule (Java rundet sonst "zur geraden Zahl").
    private val upTo = (0..2).map { decimals ->
        DecimalFormat(if (decimals == 0) "0" else "0." + "#".repeat(decimals), symbols)
            .apply { roundingMode = RoundingMode.HALF_UP }
    }
    private val twoDecimals = DecimalFormat("0.00", symbols).apply { roundingMode = RoundingMode.HALF_UP }

    /** 850.4 -> "850 kcal" */
    fun kcal(value: Double): String = "${value.roundToInt()} kcal"

    /** Wie [kcal], mit "≈" davor, wenn der Wert geschätzt oder berechnet ist. */
    fun kcal(value: Double, estimated: Boolean): String = if (estimated) "≈ ${kcal(value)}" else kcal(value)

    /** 32.4 -> "32 g" */
    fun grams(value: Double): String = "${value.roundToInt()} g"

    /** "32 g Protein", oder null, wenn der Wert fehlt. */
    fun protein(value: Double?): String? = value?.let { "${grams(it)} Protein" }

    /** Zahl mit höchstens [decimals] Nachkommastellen, z. B. für Eingabefelder: 12.5 -> "12,5", 13.0 -> "13". */
    fun number(value: Double, decimals: Int = 1): String = upTo[decimals.coerceIn(0, 2)].format(value)

    /** "1 Portion", "1,5 Portionen", "250 g" */
    fun amount(amount: Double, unit: EntryUnit): String = when (unit) {
        EntryUnit.GRAM -> "${number(amount, 1)} g"
        EntryUnit.PORTION -> if (amount == 1.0) "1 Portion" else "${number(amount, 2)} Portionen"
    }

    /** Portionsgrößen der Mensa als Brüche, wie man sie spricht: 0.5 -> "½", 1.5 -> "1½". */
    fun portions(value: Double): String = when (value) {
        0.5 -> "½"
        1.5 -> "1½"
        else -> number(value, 2)
    }

    /** "Heute", "Gestern", sonst z. B. "Di, 3. Nov." (in einem anderen Jahr mit Jahreszahl). */
    fun day(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> buildString {
            // Eigene Wochentagskürzel ohne Punkt ("Di" statt "Di."), wie in der Mensa.
            append(MensaFormat.shortWeekday(date))
            append(", ")
            append(Format.shortDate(date))
            if (date.year != today.year) append(" ${date.year}")
        }
    }

    /** +0.2 -> "+0,20 kg", -0.5 -> "−0,50 kg". Echtes Minuszeichen wie in [Format.signedKg]. */
    fun signedKg(value: Double): String = "${signed(value)} kg"

    /** "+0,25 kg pro Woche" */
    fun weeklyGoal(value: Double): String = "${signedKg(value)} pro Woche"

    /** Kurz für Klammern: "+0,25" */
    fun signed(value: Double): String {
        val text = twoDecimals.format(abs(value))
        return when {
            abs(value) < 0.005 -> "±$text"
            value > 0 -> "+$text"
            else -> "−$text"
        }
    }

    /** 220 -> "+220", -150 -> "−150" */
    fun signed(delta: Int): String = when {
        delta > 0 -> "+$delta"
        delta < 0 -> "−${-delta}"
        else -> "±0"
    }

    /** Woher die Startwerte eines Mensa-Gerichts kommen, als kurzer Hinweis im Sheet. */
    fun origin(origin: PortionOrigin, estimated: Boolean): String = when (origin) {
        PortionOrigin.PUBLISHED -> if (estimated) "Angaben der Mensa, Protein berechnet" else "Angaben der Mensa"
        PortionOrigin.LAST_TIME -> "Werte vom letzten Mal"
        PortionOrigin.GUESS -> "≈ Schätzung"
    }

    /** Name für Schnell-Einträge ohne eigenen Namen. */
    const val QUICK_NAME = "Schnell eingetragen"

    /**
     * Was bis zum Tagesziel fehlt: "Noch 1450 kcal", "Ziel erreicht" oder "320 kcal drüber".
     * Bis 50 kcal daneben zählt als erreicht: Das ist etwa ein Teelöffel Öl, so genau misst niemand.
     */
    fun remaining(remainingKcal: Double): String {
        val rounded = remainingKcal.roundToInt()
        return when {
            rounded > NutritionGoals.KCAL_STEP -> "Noch $rounded kcal"
            rounded < -NutritionGoals.KCAL_STEP -> "${-rounded} kcal drüber"
            else -> "Ziel erreicht"
        }
    }

    /** true, wenn [remaining] "Ziel erreicht" sagt. */
    fun goalReached(remainingKcal: Double): Boolean = abs(remainingKcal.roundToInt()) <= NutritionGoals.KCAL_STEP

    /**
     * Zweite Zeile eines Eintrags: "Polymensa · Street", "250 g", "1,5 Portionen".
     * Bei Mensa-Gerichten steht die Menge nur da, wenn es nicht genau eine Portion war. Bei Schnell-Einträgen
     * ist "1 Portion" nichtssagend und fällt weg (null).
     */
    fun entryDetail(entry: FoodEntry): String? {
        val onePortion = entry.unit == EntryUnit.PORTION && entry.amount == 1.0
        return when (entry.source) {
            EntrySource.MENSA -> {
                val place = listOfNotNull(entry.mensa, entry.mensaLine).joinToString(" · ").ifEmpty { "Mensa" }
                if (onePortion) place else "$place · ${amount(entry.amount, entry.unit)}"
            }
            EntrySource.FOOD -> amount(entry.amount, entry.unit)
            EntrySource.QUICK -> if (onePortion) null else amount(entry.amount, entry.unit)
        }
    }

    /** "1 Eintrag", "3 Einträge" */
    fun entries(count: Int): String = if (count == 1) "1 Eintrag" else "$count Einträge"

    /** Name in deutschen Anführungszeichen, lange Namen gekürzt, z. B. für Snackbars: „Pasta Bolognese“ */
    fun quoted(name: String, maxLength: Int = 28): String {
        val trimmed = name.trim()
        val short = if (trimmed.length <= maxLength) trimmed else trimmed.take(maxLength - 1).trimEnd() + "…"
        return "„$short“"
    }

    /**
     * Ein Satz zum Zielvorschlag: geplante und tatsächliche Änderung pro Woche.
     * "In den letzten zwei Wochen", weil [Trend] den Durchschnitt dieser Woche mit dem der Woche davor vergleicht.
     */
    fun suggestionReason(suggestion: TargetSuggestion): String =
        "Geplant sind ${signedKg(suggestion.goalWeeklyChangeKg)} pro Woche, " +
            "in den letzten zwei Wochen waren es ${signedKg(suggestion.actualWeeklyChangeKg)}."

    /**
     * Was das Wochenziel pro Tag bedeutet: +0,25 kg pro Woche sind 0,25 · 7700 kcal ÷ 7 Tage ≈ 275 kcal
     * Überschuss pro Tag. Auf 5 gerundet, mehr Genauigkeit gibt es bei solchen Schätzungen nicht.
     */
    fun dailyBalance(weeklyGoalKg: Double): String {
        val perDay = Math.round(abs(weeklyGoalKg) * Trend.KCAL_PER_KG / 7 / 5).toInt() * 5
        return when {
            perDay == 0 -> "Gewicht halten: so viel essen, wie du verbrauchst"
            weeklyGoalKg > 0 -> "≈ $perDay kcal Überschuss pro Tag"
            else -> "≈ $perDay kcal Defizit pro Tag"
        }
    }

    /** Liest "1,5" und "1.5". null bei leerer oder ungültiger Eingabe. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * Filtert beim Tippen: nur Ziffern 0–9 und höchstens ein Komma oder Punkt. So kann im Feld nie etwas
     * stehen, das [parse] nicht versteht (Buchstaben, Minus, zwei Kommas).
     */
    fun decimalInput(raw: String, maxLength: Int = 7): String {
        var separator = false
        return buildString {
            for (c in raw) {
                when {
                    c in '0'..'9' -> append(c)
                    (c == ',' || c == '.') && !separator -> {
                        append(c)
                        separator = true
                    }
                }
            }
        }.take(maxLength)
    }
}
