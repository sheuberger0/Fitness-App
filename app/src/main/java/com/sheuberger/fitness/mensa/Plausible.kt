package com.sheuberger.fitness.mensa

/**
 * Plausible Bereiche für Zahlen aus den Mensa-Seiten, an einer Stelle gesammelt.
 *
 * Die Daten kommen aus dem Internet: Ein Tippfehler beim Betreiber oder eine manipulierte Antwort soll
 * keine Fantasiewerte (z. B. 50 000 kcal oder -5 g Protein) in die Rangliste bringen. Eine Zahl außerhalb
 * ihres Bereichs oder eine nicht endliche Zahl (NaN, Infinity) zählt deshalb genau wie ein fehlendes Feld.
 * Jede Funktion gibt den Wert zurück, wenn er passt, sonst null.
 */
internal object Plausible {

    // --- pro Portion ---

    /** kcal pro Portion: mehr als 0, höchstens 3000. */
    fun kcal(value: Double?): Double? = value.inRange(0.0, 3000.0, minInclusive = false)

    /** Protein pro Portion in Gramm: 0 bis 200. */
    fun proteinG(value: Double?): Double? = value.inRange(0.0, 200.0)

    /** Kohlenhydrate pro Portion in Gramm: 0 bis 400. */
    fun carbsG(value: Double?): Double? = value.inRange(0.0, 400.0)

    /** Fett pro Portion in Gramm: 0 bis 200. */
    fun fatG(value: Double?): Double? = value.inRange(0.0, 200.0)

    /** Portionsgewicht in Gramm: mehr als 0, höchstens 2000. */
    fun weightG(value: Double?): Double? = value.inRange(0.0, 2000.0, minInclusive = false)

    /** Preis in CHF: mehr als 0 (0.00 heißt "Preis fehlt noch"), höchstens 50. */
    fun price(value: Double?): Double? = value.inRange(0.0, 50.0, minInclusive = false)

    // --- pro 100 g (so liefert die ETH die Makros) ---

    /** Protein, Kohlenhydrate oder Fett in Gramm pro 100 g: 0 bis 100. */
    fun gramsPer100g(value: Double?): Double? = value.inRange(0.0, 100.0)

    /** kcal pro 100 g: mehr als 0, höchstens 900 (reines Fett hat etwa 900). */
    fun kcalPer100g(value: Double?): Double? = value.inRange(0.0, 900.0, minInclusive = false)

    private fun Double?.inRange(min: Double, max: Double, minInclusive: Boolean = true): Double? {
        val v = this ?: return null
        if (!v.isFinite()) return null
        val aboveMin = if (minInclusive) v >= min else v > min
        return v.takeIf { aboveMin && it <= max }
    }
}
