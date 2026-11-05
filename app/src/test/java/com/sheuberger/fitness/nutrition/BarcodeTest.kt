package com.sheuberger.fitness.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Barcode landet in einer Internetadresse. Deshalb darf [Barcode.validate] nur echte Codes durchlassen:
 * 8, 12 oder 13 Ziffern von 0 bis 9 mit richtiger Prüfziffer.
 */
class BarcodeTest {

    @Test
    fun gueltigeCodes_ean13_ean8_upcA() {
        assertEquals("4006381333931", Barcode.validate("4006381333931")) // EAN-13
        assertEquals("96385074", Barcode.validate("96385074")) // EAN-8
        assertEquals("036000291452", Barcode.validate("036000291452")) // UPC-A (12 Ziffern)
    }

    @Test
    fun leerzeichenWerdenEntfernt() {
        // So steht die Nummer oft unter dem Strichcode, und so tippt man sie ab.
        assertEquals("4006381333931", Barcode.validate("4 006381 333931"))
        assertEquals("4006381333931", Barcode.validate("  4006381333931\n"))
        assertEquals("96385074", Barcode.validate("9638\t5074"))
    }

    @Test
    fun falschePruefzifferWirdAbgelehnt() {
        assertNull(Barcode.validate("4006381333932"))
        assertNull(Barcode.validate("96385075"))
        assertNull(Barcode.validate("036000291453"))
    }

    @Test
    fun buchstabenUndSonderzeichenWerdenAbgelehnt() {
        assertNull(Barcode.validate("40063813339A1"))
        assertNull(Barcode.validate("abcdefgh"))
        assertNull(Barcode.validate("+4006381333931"))
        assertNull(Barcode.validate("4006-381-333931"))
    }

    @Test
    fun falscheLaengenWerdenAbgelehnt() {
        assertNull(Barcode.validate("1".repeat(30)))
        assertNull(Barcode.validate("123456789012345678901234567890"))
        assertNull(Barcode.validate("1234567")) // 7 Ziffern
        assertNull(Barcode.validate("00000000000000")) // 14 Ziffern (GTIN-14 kommt auf Kartons, nicht auf Packungen)
    }

    @Test
    fun angriffeAufDieAdresseWerdenAbgelehnt() {
        assertNull(Barcode.validate("' OR 1=1"))
        assertNull(Barcode.validate("%2F.."))
        assertNull(Barcode.validate("../../4006381333931"))
        assertNull(Barcode.validate("4006381333931?fields=*"))
        assertNull(Barcode.validate("4006381333931/../x"))
    }

    @Test
    fun leererTextWirdAbgelehnt() {
        assertNull(Barcode.validate(""))
        assertNull(Barcode.validate("   "))
    }

    @Test
    fun ziffernAusAnderenSchriftenWerdenAbgelehnt() {
        // Char.isDigit() hielte das für Ziffern. In einer URL wären es aber ganz andere Bytes.
        assertNull(Barcode.validate("٤٠٠٦٣٨١٣٣٣٩٣١")) // Arabisch-indische Ziffern
        assertNull(Barcode.validate("۴۰۰۶۳۸۱۳۳۳۹۳۱")) // Persische Ziffern
        assertNull(Barcode.validate("４００６３８１３３３９３１")) // Ziffern voller Breite (Japanisch, Chinesisch)
        assertNull(Barcode.validate("४००६३८१३३३९३१")) // Devanagari
        assertNull(Barcode.validate("400638133393١")) // nur die letzte Ziffer arabisch
    }

    @Test
    fun ladencodes_ean13MitZweiVorne() {
        // Abgewogenes Poulet aus der Migros: Die Nummer gilt nur dort.
        val code = Barcode.validate("2181113008607")!!
        assertTrue(Barcode.isInStore(code))
        assertFalse(Barcode.isInStore("4006381333931"))
        assertFalse(Barcode.isInStore("7614500010013"))
        // EAN-8 mit 2 vorne zählt nicht dazu: Migros-Eigenmarken haben solche Nummern, und Open Food Facts kennt sie
        // (z. B. 22039750, ein Joghurt).
        assertFalse(Barcode.isInStore(Barcode.validate("22039750")!!))
    }
}
