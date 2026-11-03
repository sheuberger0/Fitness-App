package com.sheuberger.fitness.mensa

/** Wonach die Rangliste sortiert. */
enum class RankBy(val label: String) {
    Protein("Protein"),
    Calories("Kalorien"),
    /** Gramm Protein pro Franken: wie viel Protein man fürs Geld bekommt. */
    Value("Protein pro CHF"),
}

data class RankedMeal(
    val meal: Meal,
    val price: Double,
    /** Dasselbe Gericht zum selben Preis gibt es auch hier (die UZH-Mensen teilen sich oft eine Küche). */
    val alsoAt: List<Mensa> = emptyList(),
) {
    val nutrition: Nutrition get() = meal.nutrition!!
    val proteinPerFranc: Double get() = if (price > 0) nutrition.proteinG / price else 0.0
}

data class Ranking(
    /** Hauptgerichte im Budget, mit Nährwerten, sortiert. */
    val ranked: List<RankedMeal>,
    /** Hauptgerichte im Budget, aber ohne Nährwerte: lassen sich nicht einordnen. */
    val unrated: List<RankedMeal>,
    /** Wie viele Hauptgerichte über dem Budget liegen (nur zur Info). */
    val tooExpensive: Int,
)

/**
 * Die eigentliche Rangliste. Bewusst eine reine Funktion ohne Android-Abhängigkeiten,
 * damit sie sich einfach testen lässt (siehe MensaRankingTest).
 */
object MensaRanking {

    /** Weniger Kalorien als das ist kein Mittagessen (Suppe, kleiner Salat, Platzhalter-Einträge). */
    const val MIN_MEAL_KCAL = 200.0

    fun rank(
        meals: List<Meal>,
        group: PriceGroup,
        budget: Double,
        by: RankBy,
        mensas: Set<Mensa> = Mensa.entries.toSet(),
    ): Ranking {
        val mains = meals.filter { meal ->
            meal.mensa in mensas && !meal.isSide && meal.price(group) != null &&
                (meal.nutrition == null || meal.nutrition.kcal >= MIN_MEAL_KCAL)
        }
        // + 0.001: Preise kommen als Kommazahl; 9.5 soll bei einem Budget von 9.50 sicher mitzählen.
        val affordable = mains.filter { it.price(group)!! <= budget + 0.001 }
            .map { RankedMeal(it, it.price(group)!!) }
            .let(::mergeDuplicates)

        val comparator: Comparator<RankedMeal> = when (by) {
            RankBy.Protein -> compareByDescending<RankedMeal> { it.nutrition.proteinG }.thenByDescending { it.nutrition.kcal }
            RankBy.Calories -> compareByDescending<RankedMeal> { it.nutrition.kcal }.thenByDescending { it.nutrition.proteinG }
            RankBy.Value -> compareByDescending<RankedMeal> { it.proteinPerFranc }.thenByDescending { it.nutrition.proteinG }
        }
        return Ranking(
            ranked = affordable.filter { it.meal.nutrition != null }.sortedWith(comparator.thenBy { it.price }),
            unrated = affordable.filter { it.meal.nutrition == null }.sortedBy { it.price },
            tooExpensive = mains.filter { it.price(group)!! > budget + 0.001 }.distinctBy { it.name.lowercase() }.size,
        )
    }

    /**
     * Gleiches Gericht, gleicher Preis, mehrere Mensen: nur einmal in die Liste, mit den anderen Mensen
     * in [RankedMeal.alsoAt]. Es bleibt der Eintrag mit dem meisten Protein (die Portionen weichen leicht ab).
     */
    private fun mergeDuplicates(meals: List<RankedMeal>): List<RankedMeal> =
        meals.groupBy { Triple(it.meal.name.lowercase(), it.meal.time, it.price) }.values.map { same ->
            val best = same.maxBy { it.meal.nutrition?.proteinG ?: -1.0 }
            best.copy(alsoAt = same.map { it.meal.mensa }.filter { it != best.meal.mensa }.distinct())
        }
}
