package com.sheuberger.fitness.mensa

// Datenmodell für die Mensa-Rangliste. Die Menüs kommen live aus dem Internet und werden nicht in der
// Datenbank gespeichert. Deshalb gibt es hier keine Room-Entities, und an der Datenbank ändert sich nichts.

enum class University(val label: String) { ETH("ETH"), UZH("UZH") }

/**
 * Die Mensen, deren Menüs die App liest.
 * [slug] ist bei der UZH der Pfad auf app.food2050.ch (dort veröffentlicht der Betreiber ZFV die Menüs),
 * bei der ETH die "facility"-Nummer der ETH-Gastronomie.
 */
enum class Mensa(
    val title: String,
    val university: University,
    val place: String,
    internal val lunch: String?,
    internal val dinner: String? = null,
) {
    Polymensa("Polymensa", University.ETH, "ETH Zentrum", lunch = "9", dinner = "9"),
    UzhObere(
        "Obere Mensa", University.UZH, "UZH Zentrum",
        lunch = "universitat-zurich,campus-zentrum/obere-mensa/lunch",
    ),
    UzhUntere(
        "Untere Mensa", University.UZH, "UZH Zentrum",
        lunch = "universitat-zurich,campus-zentrum/untere-mensa/mittagsverpflegung",
        dinner = "universitat-zurich,campus-zentrum/untere-mensa/abendverpflegung",
    ),
    UzhIrchel(
        "Mensa Irchel", University.UZH, "UZH Irchel",
        lunch = "universitat-zurich,campus-irchel/mensa/mittagsverpflegung",
    ),
    ;

    fun source(time: MealTime): String? = if (time == MealTime.Lunch) lunch else dinner
}

enum class MealTime(val label: String) { Lunch("Mittag"), Dinner("Abend") }

/** Preiskategorie. Beide Mensen haben dieselben drei Stufen, nur heißen sie leicht anders. */
enum class PriceGroup(val label: String) {
    Students("Studierende"),
    Staff("Mitarbeitende"),
    External("Externe"),
}

/**
 * Nährwerte für eine ganze Portion.
 * [estimated]: Die ETH gibt Protein, Kohlenhydrate und Fett nur pro 100 g an, die Kalorien aber pro Portion.
 * Daraus rechnen wir das Portionsgewicht und dann das Protein pro Portion aus (siehe [EthMenuParser]).
 */
data class Nutrition(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double?,
    val fatG: Double?,
    val weightG: Double?,
    val estimated: Boolean,
)

data class Meal(
    val mensa: Mensa,
    val time: MealTime,
    /** Ausgabe/Linie, z. B. "STREET" (ETH) oder "Menu 1" (UZH). */
    val line: String,
    val name: String,
    val description: String,
    val prices: Map<PriceGroup, Double>,
    val nutrition: Nutrition?,
    val vegetarian: Boolean,
    val vegan: Boolean,
    /** Suppe, Salat, Dessert, Buffet nach Gewicht: zählt nicht als Hauptgericht. */
    val isSide: Boolean,
) {
    val id: String get() = "${mensa.name}|${time.name}|$line|$name"
    fun price(group: PriceGroup): Double? = prices[group]
}

/** Was beim Laden eines Tages herauskam. Eine Mensa kann ausfallen, ohne dass die anderen fehlen. */
data class DayMenu(
    val meals: List<Meal>,
    val failed: Set<Mensa>,
)

/** "PANIERTES SCHNITZEL" -> "Paniertes Schnitzel". Die UZH schreibt Gerichte komplett in Großbuchstaben. */
internal fun String.calmCase(): String {
    val letters = filter { it.isLetter() }
    if (letters.isEmpty() || letters.any { it.isLowerCase() }) return trim()
    return lowercase().split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase() }
    }.trim()
}
