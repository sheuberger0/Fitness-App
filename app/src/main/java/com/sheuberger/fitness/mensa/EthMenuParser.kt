package com.sheuberger.fitness.mensa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/**
 * Liest den Wochenplan der ETH-Gastronomie (öffentliche JSON-Schnittstelle "cookpit", dieselbe, die
 * ethz.ch für die Menüseiten nutzt).
 *
 * Aufbau: weekly-rota-array (eine Woche pro Mensa) → day-of-week-array (Mo = 1) → opening-hour-array →
 * meal-time-array ("Mittag"/"Abend") → line-array (Ausgabe, z. B. "STREET") → meal.
 *
 * Achtung bei den Nährwerten: "energy" ist kcal pro Portion, "protein", "carbohydrates" und "fat" sind
 * Gramm pro 100 g, und "energy-outdated" sind kcal pro 100 g. Nachgerechnet: 4·Protein + 4·KH + 9·Fett
 * ergibt ziemlich genau "energy-outdated". Also: Portionsgewicht = energy / energy-outdated · 100 g.
 */
object EthMenuParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, date: LocalDate, mensa: Mensa = Mensa.Polymensa): List<Meal> {
        val root = json.parseToJsonElement(requireShallowJson(body)).obj ?: return emptyList()
        val facility = mensa.lunch?.toIntOrNull()
        val rota = root.arr("weekly-rota-array").mapNotNull { it.obj }.firstOrNull { rota ->
            val from = rota.str("valid-from")?.let(LocalDate::parse)
            val to = rota.str("valid-to")?.let(LocalDate::parse)
            (facility == null || rota.int("facility-id") == facility) &&
                from != null && to != null && !date.isBefore(from) && !date.isAfter(to)
        } ?: return emptyList()

        val day = rota.arr("day-of-week-array").mapNotNull { it.obj }
            .firstOrNull { it.int("day-of-week-code") == date.dayOfWeek.value } ?: return emptyList()

        val meals = mutableListOf<Meal>()
        for (hours in day.arr("opening-hour-array").mapNotNull { it.obj }) {
            for (mealTime in hours.arr("meal-time-array").mapNotNull { it.obj }) {
                val time = if (mealTime.str("name").orEmpty().contains("abend", ignoreCase = true)) {
                    MealTime.Dinner
                } else {
                    MealTime.Lunch
                }
                for (line in mealTime.arr("line-array").mapNotNull { it.obj }) {
                    // Noch nicht geplante Ausgaben stehen ohne "meal" im Plan.
                    val meal = line["meal"]?.obj ?: continue
                    parseMeal(mensa, time, line.str("name").orEmpty(), meal)?.let(meals::add)
                }
            }
        }
        return meals
    }

    private fun parseMeal(mensa: Mensa, time: MealTime, lineName: String, meal: JsonObject): Meal? {
        val name = meal.str("name")?.trim().orEmpty()
        if (name.isEmpty()) return null

        val prices = buildMap {
            for (p in meal.arr("meal-price-array").mapNotNull { it.obj }) {
                // Unplausible Preise (0, negativ, über 50 CHF) überspringen wir wie fehlende.
                val price = Plausible.price(p.double("price")) ?: continue
                val group = when (p.int("customer-group-code")) {
                    10 -> PriceGroup.Students
                    11 -> PriceGroup.Staff
                    13 -> PriceGroup.External
                    else -> null
                } ?: continue
                put(group, price)
            }
        }

        val classes = meal.arr("meal-class-array").mapNotNull { it.obj?.str("desc")?.lowercase() }
        val vegan = "vegan" in classes
        val side = "suppe" in classes || "salat" in classes ||
            SideWords.any { lineName.contains(it, ignoreCase = true) || name.contains(it, ignoreCase = true) }

        return Meal(
            mensa = mensa,
            time = time,
            line = lineName.calmCase(),
            name = name.calmCase(),
            // Die ETH trennt die Zutaten mit " | ", manchmal mit Zeilenumbrüchen.
            description = meal.str("description").orEmpty()
                .replace('\n', ' ')
                .split('|').map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(", "),
            prices = prices,
            nutrition = nutrition(meal),
            vegetarian = vegan || "vegetarisch" in classes,
            vegan = vegan,
            isSide = side,
        )
    }

    private fun nutrition(meal: JsonObject): Nutrition? {
        // Jeder Wert muss plausibel sein (siehe Plausible), sonst zählt er als fehlend.
        val kcalPortion = Plausible.kcal(meal.double("energy")) ?: return null
        val protein100 = Plausible.gramsPer100g(meal.double("protein")) ?: return null
        val carbs100 = Plausible.gramsPer100g(meal.double("carbohydrates"))
        val fat100 = Plausible.gramsPer100g(meal.double("fat"))
        val kcal100 = Plausible.kcalPer100g(meal.double("energy-outdated"))
            ?: Plausible.kcalPer100g(4 * protein100 + 4 * (carbs100 ?: 0.0) + 9 * (fat100 ?: 0.0))
            ?: return null
        val factor = kcalPortion / kcal100 // = Portionsgewicht / 100 g
        // Auch die umgerechneten Werte pro Portion prüfen: Ein unpassendes Paar aus energy und
        // energy-outdated ergäbe sonst z. B. eine 5-kg-Portion.
        val weight = Plausible.weightG(factor * 100) ?: return null
        val protein = Plausible.proteinG(protein100 * factor) ?: return null
        return Nutrition(
            kcal = kcalPortion,
            proteinG = protein,
            carbsG = Plausible.carbsG(carbs100?.times(factor)),
            fatG = Plausible.fatG(fat100?.times(factor)),
            weightG = weight,
            estimated = true,
        )
    }

    /** Beilagen und Angebote nach Gewicht, die nicht in die Rangliste gehören. */
    private val SideWords = listOf("suppe", "smoothie", "100g", "100 g", "dessert", "salatbuffet")
}

// --- kleine Helfer, damit das Lesen des JSON kurz bleibt ---

/** Tiefer verschachtelt ist kein echter Menüplan (gemessen: ETH 16 Ebenen, food2050 8). */
internal const val MAX_JSON_DEPTH = 64

/**
 * Gibt [text] zurück, wenn seine Klammern ([ und {, außerhalb von Strings) höchstens [MAX_JSON_DEPTH] tief
 * verschachtelt sind, sonst IllegalArgumentException. kotlinx.serialization liest JSON rekursiv; Tausende
 * verschachtelte Klammern (kaputte oder manipulierte Antwort) führen dort zu einem StackOverflowError,
 * den MensaClient nicht abfangen würde. Diese Prüfung kostet nur einen Durchlauf über den Text.
 */
internal fun requireShallowJson(text: String): String {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in text) {
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '[', '{' -> require(++depth <= MAX_JSON_DEPTH) { "JSON zu tief verschachtelt" }
            ']', '}' -> depth--
        }
    }
    return text
}

internal val JsonElement.obj: JsonObject? get() = this as? JsonObject
internal fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** Zahl, auch wenn sie als Text kommt ("6.1"). */
internal fun JsonObject.double(key: String): Double? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.doubleOrNull ?: p.content.replace(',', '.').toDoubleOrNull()
}
