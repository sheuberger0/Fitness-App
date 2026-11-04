package com.sheuberger.fitness.nutrition

/**
 * Schätzt die Kalorien eines Mensa-Gerichts ohne Nährwertangaben nach Stichworten im Namen.
 * Die Werte stammen aus deinem Plan (v2). Die App markiert das Ergebnis mit "≈ Schätzung", und du kannst
 * es beim Eintragen ändern. Die App merkt sich deinen Wert für das nächste Mal.
 */
object MensaEstimate {

    /** Stichwort-Gruppen und kcal pro Portion. Der erste Treffer zählt, deshalb stehen die schweren Gerichte oben. */
    private val rules: List<Pair<List<String>, Double>> = listOf(
        listOf("burger", "schnitzel", "pommes", "frites", "cordon bleu") to 1000.0,
        listOf("pasta", "spaghetti", "penne", "lasagne", "nudel", "gnocchi", "tortellini", "ravioli", "maccaroni", "älplermagronen") to 800.0,
        listOf("risotto", "curry") to 750.0,
        listOf("vegan") to 650.0,
        listOf("salat") to 400.0,
        listOf("suppe") to 250.0,
    )

    /** Für Gerichte, auf die kein Stichwort passt: ein durchschnittliches Hauptgericht. */
    const val DEFAULT_KCAL = 700.0

    /** Ein Dessert dazu. */
    const val DESSERT_EXTRA = 250.0

    fun guess(name: String, description: String = ""): PortionValues {
        val title = name.lowercase()
        val base = rules.firstOrNull { (words, _) -> words.any { it in title } }?.second ?: DEFAULT_KCAL
        val dessert = "dessert" in title || "dessert" in description.lowercase()
        return PortionValues(
            kcal = base + if (dessert) DESSERT_EXTRA else 0.0,
            proteinG = null,
            carbsG = null,
            fatG = null,
            estimated = true,
            origin = PortionOrigin.GUESS,
        )
    }
}
