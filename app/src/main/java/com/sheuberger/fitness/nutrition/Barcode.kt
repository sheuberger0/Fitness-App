package com.sheuberger.fitness.nutrition

/**
 * Prüft Barcodes, bevor die App damit irgendetwas macht. Alles, was die Kamera oder das Tippfeld liefert,
 * gilt als unsicher: Nur Ziffern, nur die Längen von EAN-8, UPC-A und EAN-13, und die Prüfziffer muss stimmen.
 * So landen Buchstaben, Sonderzeichen oder 30-stellige Zahlen nie in einer Internetadresse.
 */
object Barcode {
    private val lengths = setOf(8, 12, 13)

    /** Der bereinigte Code (Leerzeichen entfernt), oder null, wenn er ungültig ist. */
    fun validate(raw: String): String? {
        val code = raw.filterNot { it.isWhitespace() }
        if (code.length !in lengths || !code.all { it in '0'..'9' }) return null
        return code.takeIf { checkDigitValid(it) }
    }

    /**
     * Ladencodes (EAN-13 mit 2 am Anfang) vergibt jedes Geschäft selbst, z. B. für abgewogenen Käse.
     * Dieselbe Nummer kann woanders etwas anderes sein, deshalb fragt die App nicht bei Open Food Facts nach.
     */
    fun isInStore(code: String): Boolean = code.length == 13 && code.first() == '2'

    /** Gewichte 3 und 1 abwechselnd von rechts; die Prüfziffer ergänzt die Summe zum nächsten Vielfachen von 10. */
    internal fun checkDigitValid(code: String): Boolean {
        val digits = code.map { it - '0' }
        val sum = digits.dropLast(1).asReversed().withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        return (10 - sum % 10) % 10 == digits.last()
    }
}
