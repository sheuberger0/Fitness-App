package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.Food

/** Ergebnis einer Barcode-Suche bei Open Food Facts. */
sealed interface OffLookup {
    data class Found(val food: Food) : OffLookup
    data object NotFound : OffLookup
    data class Failed(val message: String) : OffLookup
}

/**
 * Sucht Produkte per Barcode bei Open Food Facts (offene Datenbank, Lesen ohne Konto und ohne Schlüssel).
 * Platzhalter: Die eigentliche Abfrage kommt in einem eigenen Schritt.
 */
class OpenFoodFactsClient {
    suspend fun lookup(barcode: String): OffLookup = OffLookup.Failed("Barcode-Suche ist noch nicht eingebaut.")

    companion object {
        /** Pflicht laut Lizenz (ODbL): Die Quelle wird überall genannt, wo die Daten erscheinen. */
        const val ATTRIBUTION = "Daten: Open Food Facts, ODbL"
    }
}
