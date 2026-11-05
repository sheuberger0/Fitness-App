package com.sheuberger.fitness.ui.nutrition

import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodSource
import com.sheuberger.fitness.nutrition.FoodCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/** Die kleinen Helfer der Lebensmittel-Liste im Sheet "Eintragen": eigene Lebensmittel suchen und wiederfinden. */
class FoodListHelpersTest {

    // In der Reihenfolge der Datenbank: Favoriten zuerst, dann nach Name.
    private val foods = listOf(
        Food(id = 1, name = "Proteinriegel", kcal = 198.0, favorite = true),
        Food(id = 2, name = "Bircher Müesli", brand = "M-Classic", kcal = 150.0),
        Food(id = 3, name = "Hühnerei, ganz, roh", source = FoodSource.BLV, kcal = 140.0),
        Food(id = 4, name = "Vollmilch", brand = "Coop", barcode = "7610000000001", source = FoodSource.OFF, kcal = 65.0),
        Food(id = 5, name = "Protein Shake", kcal = 120.0),
    )

    @Test
    fun suche_umlauteUndMarke() {
        assertEquals(listOf(2L), foods.matching("muesli").map { it.id })
        assertEquals(listOf(2L), foods.matching("m-classic müsli").map { it.id })
        assertEquals(listOf(4L), foods.matching("coop").map { it.id })
        assertEquals(emptyList<Long>(), foods.matching("gibtsnicht"))
    }

    @Test
    fun suche_guteTrefferZuerst_sonstReihenfolgeDerDatenbank() {
        // "ei" steckt überall nur mitten im Wort: gleich gut, also bleibt die Reihenfolge, der Favorit vorne.
        assertEquals(listOf(1L, 3L, 5L), foods.matching("ei").map { it.id })
        // "protein" ist bei "Protein Shake" ein ganzes Wort, bei "Proteinriegel" nur der Anfang: Der Shake kommt
        // vor den Favoriten.
        assertEquals(listOf(5L, 1L), foods.matching("protein").map { it.id })
        // "milch" am Wortende von "Vollmilch" zählt als gutes Wort.
        assertEquals(listOf(4L), foods.matching("milch").map { it.id })
    }

    @Test
    fun suche_nurSatzzeichen_zeigtAlles() {
        assertEquals(foods, foods.matching(" , "))
    }

    @Test
    fun tabelle_verstecktNur_wasObenSchonSteht() = runBlocking {
        val blv = "829\tRuchbrot\t\tBrote\t234\t9.5\t44.3\t1.2\t40\n" +
            "703\tHaselnuss-Schokolade-Brotaufstrich\tNutella\tSüssigkeiten\t549\t6\t55.9\t32.8\t15\n"
        val catalog = FoodCatalog({ name ->
            if (name == FoodCatalog.BLV_FILE) ByteArrayInputStream(blv.toByteArray()) else throw FileNotFoundException(name)
        })
        // Beide schon einmal eingetragen, also gespeichert.
        val saved = listOf(
            Food(id = 7, name = "Ruchbrot", source = FoodSource.BLV, kcal = 234.0),
            Food(id = 8, name = "Haselnuss-Schokolade-Brotaufstrich", source = FoodSource.BLV, kcal = 549.0),
        )
        // "ruchbrot": Die Kopie steht unter "Meine Lebensmittel", in der Tabelle also nicht noch einmal.
        val bread = saved.matching("ruchbrot")
        assertEquals(listOf(7L), bread.map { it.id })
        assertTrue(catalog.search("ruchbrot").without(bread).generic.isEmpty())
        // "nutella" findet den Brotaufstrich nur über das Suchwort der Tabelle, die Kopie kennt es nicht. Dann bleibt
        // er in der Tabelle, sonst wäre er nach dem ersten Eintragen unter "nutella" verschwunden.
        val spread = saved.matching("nutella")
        assertTrue(spread.isEmpty())
        assertEquals(
            listOf("Haselnuss-Schokolade-Brotaufstrich"),
            catalog.search("nutella").without(spread).generic.map { it.name },
        )
    }

    @Test
    fun gespeicherteFassung_perIdBarcodeOderNameUndQuelle() {
        assertEquals(1L, foods.savedVersionOf(Food(id = 1, name = "anders", kcal = 0.0))?.id)
        // Aus der Produktliste (ID 0): gleicher Barcode.
        assertEquals(4L, foods.savedVersionOf(Food(name = "Vollmilch UHT", barcode = "7610000000001", source = FoodSource.OFF, kcal = 65.0))?.id)
        // Aus der Nährwerttabelle (ohne Barcode): gleiche Quelle und gleicher Name.
        assertEquals(3L, foods.savedVersionOf(Food(name = "Hühnerei, ganz, roh", source = FoodSource.BLV, kcal = 140.0))?.id)
        // Gleicher Name, aber selbst angelegt: nicht dasselbe.
        assertNull(foods.savedVersionOf(Food(name = "Hühnerei, ganz, roh", kcal = 140.0)))
    }
}
