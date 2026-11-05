package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.MealTemplateItem
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.MealTime
import java.time.LocalDate
import java.time.LocalTime

// Rechenregeln des Kalorien-Trackers, ohne Android und ohne Datenbank. Deshalb lassen sie sich mit
// einfachen Unit-Tests prüfen (NutritionMathTest).

/** Summen von Kalorien und Makros, z. B. für einen Tag oder eine Mahlzeit. */
data class Totals(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    operator fun plus(other: Totals) = Totals(
        kcal + other.kcal,
        proteinG + other.proteinG,
        carbsG + other.carbsG,
        fatG + other.fatG,
    )
}

/** Fehlende Makros zählen als 0. */
fun List<FoodEntry>.totals(): Totals = fold(Totals()) { sum, e ->
    sum + Totals(e.kcal, e.proteinG ?: 0.0, e.carbsG ?: 0.0, e.fatG ?: 0.0)
}

/**
 * Ändert die Menge eines Eintrags. Kalorien und Makros wachsen im selben Verhältnis:
 * von 1 auf 1,5 Portionen heißt 1,5-mal so viele Kalorien. So braucht ein Eintrag keine Werte pro 100 g.
 */
fun FoodEntry.withAmount(newAmount: Double): FoodEntry {
    require(newAmount > 0) { "Die Menge muss größer als 0 sein." }
    if (amount <= 0) return copy(amount = newAmount)
    val factor = newAmount / amount
    return copy(
        amount = newAmount,
        kcal = kcal * factor,
        proteinG = proteinG?.times(factor),
        carbsG = carbsG?.times(factor),
        fatG = fatG?.times(factor),
    )
}

/** Mit welchem Faktor die Werte von [this] für diese Menge malgenommen werden. null, wenn die Einheit nicht passt. */
fun Food.factorFor(amount: Double, unit: EntryUnit): Double? = when (basis) {
    FoodBasis.PER_100G -> when (unit) {
        EntryUnit.GRAM -> amount / 100.0
        EntryUnit.PORTION -> portionG?.let { amount * it / 100.0 }
    }
    FoodBasis.PER_PORTION -> when (unit) {
        EntryUnit.PORTION -> amount
        EntryUnit.GRAM -> portionG?.let { amount / it }
    }
}

/** In welchen Einheiten man dieses Lebensmittel eintragen kann. */
fun Food.units(): List<EntryUnit> = when {
    portionG != null -> listOf(EntryUnit.PORTION, EntryUnit.GRAM)
    basis == FoodBasis.PER_100G -> listOf(EntryUnit.GRAM)
    else -> listOf(EntryUnit.PORTION)
}

/** Ein Tagebuch-Eintrag für [amount] [unit] dieses Lebensmittels. null, wenn die Einheit nicht passt. */
fun Food.toEntry(
    amount: Double,
    unit: EntryUnit,
    date: LocalDate,
    meal: MealSlot,
    now: Long = System.currentTimeMillis(),
): FoodEntry? {
    if (amount <= 0) return null
    val factor = factorFor(amount, unit) ?: return null
    return FoodEntry(
        date = date,
        loggedAt = now,
        meal = meal,
        name = name,
        kcal = kcal * factor,
        proteinG = proteinG?.times(factor),
        carbsG = carbsG?.times(factor),
        fatG = fatG?.times(factor),
        amount = amount,
        unit = unit,
        source = EntrySource.FOOD,
        foodId = id.takeIf { it != 0L },
    )
}

/** Woher die Werte einer Mensa-Portion stammen. */
enum class PortionOrigin { PUBLISHED, LAST_TIME, GUESS }

/** Nährwerte einer Mensa-Portion, bevor die Portionsgröße (½, 1, 1½, 2) darauf angewendet wird. */
data class PortionValues(
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val estimated: Boolean,
    val origin: PortionOrigin,
)

/** Die Werte, die die Mensa für eine Portion veröffentlicht. null, wenn sie keine angibt. */
fun Meal.publishedPortion(): PortionValues? = nutrition?.let {
    PortionValues(it.kcal, it.proteinG, it.carbsG, it.fatG, estimated = it.estimated, origin = PortionOrigin.PUBLISHED)
}

/**
 * Startwerte für das Eintragen eines Mensa-Gerichts:
 * 1. Dein letzter Eintrag desselben Gerichts (falls du z. B. eine größere Portion korrigiert hast),
 * 2. sonst die Angaben der Mensa,
 * 3. sonst die Schätzung nach Stichworten ([guess]).
 */
fun mensaPortion(meal: Meal, last: FoodEntry?, guess: () -> PortionValues): PortionValues {
    if (last != null && last.amount > 0 && last.unit == EntryUnit.PORTION) {
        val f = 1.0 / last.amount
        return PortionValues(
            kcal = last.kcal * f,
            proteinG = last.proteinG?.times(f),
            carbsG = last.carbsG?.times(f),
            fatG = last.fatG?.times(f),
            estimated = last.estimated,
            origin = PortionOrigin.LAST_TIME,
        )
    }
    return meal.publishedPortion() ?: guess()
}

/** Ein Tagebuch-Eintrag für [portions] Portionen eines Mensa-Gerichts. */
fun Meal.toEntry(
    portions: Double,
    values: PortionValues,
    date: LocalDate,
    slot: MealSlot,
    now: Long = System.currentTimeMillis(),
): FoodEntry {
    require(portions > 0) { "Die Menge muss größer als 0 sein." }
    return FoodEntry(
        date = date,
        loggedAt = now,
        meal = slot,
        name = name,
        kcal = values.kcal * portions,
        proteinG = values.proteinG?.times(portions),
        carbsG = values.carbsG?.times(portions),
        fatG = values.fatG?.times(portions),
        amount = portions,
        unit = EntryUnit.PORTION,
        source = EntrySource.MENSA,
        mensa = mensa.title,
        mensaLine = line,
        estimated = values.estimated,
    )
}

/** Mittag in der Mensa landet bei "Mittag", Abend bei "Abend". */
fun MealTime.toSlot(): MealSlot = if (this == MealTime.Lunch) MealSlot.LUNCH else MealSlot.DINNER

/** Vorschlag für die Mahlzeit nach Uhrzeit. In der App lässt er sich jederzeit ändern. */
fun slotForTime(time: LocalTime): MealSlot = when {
    time < LocalTime.of(10, 30) -> MealSlot.BREAKFAST
    time < LocalTime.of(14, 30) -> MealSlot.LUNCH
    time >= LocalTime.of(17, 0) && time < LocalTime.of(21, 30) -> MealSlot.DINNER
    else -> MealSlot.SNACK
}

/** Mittag oder Abend in der Mensa, je nach Uhrzeit: ab 15 Uhr zeigt die App die Abendmenüs. */
fun mensaTimeFor(time: LocalTime): MealTime = if (time < LocalTime.of(15, 0)) MealTime.Lunch else MealTime.Dinner

/** Kopiert einen Eintrag als Teil einer Vorlage. */
fun FoodEntry.toTemplateItem(templateId: Long, position: Int) = MealTemplateItem(
    templateId = templateId,
    position = position,
    name = name,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    amount = amount,
    unit = unit,
    source = source,
    foodId = foodId,
    mensa = mensa,
    mensaLine = mensaLine,
    estimated = estimated,
)

/** Legt aus einem Teil einer Vorlage einen neuen Eintrag an. */
fun MealTemplateItem.toEntry(date: LocalDate, slot: MealSlot, now: Long) = FoodEntry(
    date = date,
    loggedAt = now,
    meal = slot,
    name = name,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    amount = amount,
    unit = unit,
    source = source,
    foodId = foodId,
    mensa = mensa,
    mensaLine = mensaLine,
    estimated = estimated,
)
