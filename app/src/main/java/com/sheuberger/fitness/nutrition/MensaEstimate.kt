package com.sheuberger.fitness.nutrition

/**
 * Schätzt die Kalorien eines Mensa-Gerichts ohne Nährwertangaben nach Stichworten im Namen.
 * Die Werte stammen aus deinem Plan (v2). Die App markiert das Ergebnis mit "≈ Schätzung", und du kannst
 * es beim Eintragen ändern. Die App merkt sich deinen Wert für das nächste Mal.
 *
 * Die Stichworte sind Wortteile in Kleinbuchstaben: "nudel" passt auf "Nudeln" und "Eiernudeln",
 * "schnitzel" auf "Wienerschnitzel". Groß- und Kleinschreibung spielt keine Rolle.
 */
object MensaEstimate {

    /** Stichwort-Gruppen und kcal pro Portion. Der erste Treffer zählt, deshalb stehen die schweren Gerichte oben. */
    private val rules: List<Pair<List<String>, Double>> = listOf(
        // "Schnipo" ist Schweizerdeutsch für Schnitzel mit Pommes.
        listOf("burger", "schnitzel", "schnipo", "pommes", "frites", "cordon bleu", "cordon-bleu") to 1000.0,
        // "magronen" passt auf "Älplermagronen" und auf "Älpler Magronen" mit Leerzeichen.
        listOf(
            "pasta", "teigwaren", "spaghetti", "penne", "lasagne", "nudel", "gnocchi", "tortellini", "ravioli",
            "maccaroni", "makkaroni", "maccheroni", "magronen",
        ) to 800.0,
        listOf("risotto", "curry") to 750.0,
        listOf("vegan") to 650.0,
        // "Salätli" und "Süppli" (Schweizer Verkleinerungsformen) enthalten "salat" bzw. "suppe" nicht.
        listOf("salat", "salätli") to 400.0,
        listOf("suppe", "süppli", "süppchen") to 250.0,
    )

    /** Wörter für einen Nachtisch, im Namen oder in der Beschreibung. */
    private val dessertWords = listOf("dessert", "nachspeise", "nachtisch")

    /** Für Gerichte, auf die kein Stichwort passt: ein durchschnittliches Hauptgericht. */
    const val DEFAULT_KCAL = 700.0

    /** Ein Dessert dazu. */
    const val DESSERT_EXTRA = 250.0

    fun guess(name: String, description: String = ""): PortionValues {
        // lowercase() ohne Sprache: "Ä" wird zu "ä", und das Ergebnis hängt nicht von der Spracheinstellung ab.
        val title = name.lowercase()
        val text = description.lowercase()
        val base = rules.firstOrNull { (words, _) -> words.any { it in title } }?.second ?: DEFAULT_KCAL
        val dessert = dessertWords.any { it in title || it in text }
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
