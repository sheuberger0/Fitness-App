package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.GZIPOutputStream

/** Prüft Suche, Rangfolge und Barcodes der eingebauten Tabellen, mit kleinen Beispieldaten und den echten Dateien. */
class FoodCatalogTest {

    // Spalten wie in blv.tsv: id, Name, Suchwörter, Kategorie, kcal, Protein, Kohlenhydrate, Fett, Portion.
    private val blv = """
        # Quelle: Beispiel
        # id	name	suchwörter	kategorie	kcal	protein	kohlenhydrate	fett	portion_g
        290	Hühnerei, ganz, roh	Ei|Eier	Eier	140	12.6	0.3	9.8	55
        1070	Hühnerei, ganz, festgekocht	Ei|Eier|gekochtes Ei	Eier	156	14	0.3	10.9	55
        1062	Teigwaren mit Ei, gekocht	Eiernudeln	Teigwaren	132	5	25	1.1	250
        376	Eierschwamm, roh	Pfifferling	Gemüse/Pilze	24	2.3	0.2	0.5
        703	Haselnuss-Schokolade-Brotaufstrich	Nutella|Schokoaufstrich	Süssigkeiten	549	6	55.9	32.8	15
        10446	Brot (Durchschnitt)	Brot	Brote	264	9.2	44.6	4.6	40
        823	Weissbrot		Brote	242	8.5	48.9	0.8	40
        195	Milchschokolade		Süssigkeiten	537	6.5	56.9	31.1	25
        62	Vollmilch, pasteurisiert		Milch und Milchprodukte	68	3.2	4.7	4	200
        942	Müeslimischung, gezuckert		Frühstück	365	11.4	62.3	5.6	60
        349	Aubergine, roh	Eierfrucht	Gemüse	21	1.1	3.1	0.2	
        1061	Teigwaren ohne Ei, gekocht	Pasta	Teigwaren	158	5.9	31	0.9	250
    """.trimIndent()

    // Spalten wie in openfoodfacts_ch.tsv.gzip: Barcodes, Name, Marke, Läden, kcal, Protein, Kohlenhydrate, Fett,
    // Portion, Packung. Schon sortiert wie die echte Datei (Schweizer Produkte, beliebte zuerst).
    private val products = """
        # barcodes	name	marke	läden	kcal	protein	kohlenhydrate	fett	portion_g	packung_g
        7616800000006	Vollmilch UHT	M-Classic	Migros	65	3.2	4.8	3.5		1000
        7610800000000	Joghurt nature	Naturaplan	Coop	70	4	5	3.5	180	180
        7616800000013	Joghurt Erdbeer	M-Classic	Migros	95	3.5	14	2.7	180	180
        	Eier aus Freilandhaltung	Coop	Coop	140	12.6	0.3	9.8	53
        7610000000021	Molkerei Quark	Emmi		63	11	4	0.2		500
        3017620425035,8000500082379	Nutella	Nutella		539	6.3	57.5	30.9	15	1000
        0012345678905	Peanut Butter	Skippy		600	25	20	50	32	340
        	Rindsplätzli à la minute	Prix Garantie	Coop	110	22	0	2.5		
    """.trimIndent()

    private val catalog = FoodCatalog({ name ->
        when (name) {
            FoodCatalog.BLV_FILE -> ByteArrayInputStream(blv.toByteArray())
            FoodCatalog.OFF_FILE -> ByteArrayInputStream(gzip(products))
            else -> throw FileNotFoundException(name)
        }
    })

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray()) }
        return bytes.toByteArray()
    }

    private fun search(query: String) = runBlocking { catalog.search(query) }

    // --- Text falten ---

    @Test
    fun falten_umlauteUndSchreibweisen() {
        // Schweizer und deutsche Schreibweise, mit und ohne Umlaut: alles dasselbe.
        val muesli = SearchText.fold("Müesli")
        assertEquals(muesli, SearchText.fold("Müsli"))
        assertEquals(muesli, SearchText.fold("muesli"))
        assertEquals(muesli, SearchText.fold("MÜSLI"))
        assertEquals(SearchText.fold("Käse"), SearchText.fold("kaese"))
        assertEquals("huhnerei ganz roh", SearchText.fold("Hühnerei, ganz, roh"))
        assertEquals("m classic", SearchText.fold("M-Classic"))
        assertEquals("creme brulee", SearchText.fold("Crème brûlée"))
        assertEquals("strasse", SearchText.fold("Straße"))
        assertEquals("", SearchText.fold(" – , "))
    }

    // --- Suche ---

    @Test
    fun suche_ei_findetZuerstHuehnereier() {
        val result = search("ei")
        // "Ei" ist ein eigenes Suchwort der Hühnereier, kurzer Name zuerst. Danach "Ei" als ganzes Wort, dann Wortanfänge.
        assertEquals(
            listOf("Hühnerei, ganz, roh", "Hühnerei, ganz, festgekocht", "Teigwaren mit Ei, gekocht", "Eierschwamm, roh"),
            result.generic.take(4).map { it.name },
        )
        // "Aubergine" passt nur über ihr unsichtbares Suchwort "Eierfrucht", "Teigwaren ohne Ei" gerade nicht:
        // beide hinter alles, was man als Ei erkennt.
        val names = result.generic.map { it.name }
        assertTrue(names.indexOf("Aubergine, roh") > names.indexOf("Eierschwamm, roh"))
        assertTrue(names.indexOf("Teigwaren ohne Ei, gekocht") > names.indexOf("Aubergine, roh"))
        assertEquals("Eier aus Freilandhaltung", result.products.first().name)
        // "ei" am Wortende von "Molkerei" zählt nicht als guter Treffer (zu kurz), steht also hinten.
        assertTrue(result.products.indexOfFirst { it.name == "Molkerei Quark" } > 0)
    }

    @Test
    fun suche_nutella_findetBrotaufstrichUndProdukt() {
        val result = search("Nutella")
        assertEquals("Haselnuss-Schokolade-Brotaufstrich", result.generic.single().name)
        assertEquals("Nutella", result.products.single().name)
    }

    @Test
    fun suche_zusammengesetzteWoerter_wortendeVorWortanfang() {
        // "Vollmilch" ist eine Milch, "Milchschokolade" eine Schokolade.
        assertEquals(listOf("Vollmilch, pasteurisiert", "Milchschokolade"), search("milch").generic.map { it.name })
        // "Brot" zuerst, dann die Brote ("Weissbrot"), erst dann der Brotaufstrich.
        assertEquals(
            listOf("Brot (Durchschnitt)", "Weissbrot", "Haselnuss-Schokolade-Brotaufstrich"),
            search("brot").generic.map { it.name },
        )
    }

    @Test
    fun suche_wortendeNurBeiLebensmittelWoertern() {
        // "nute" ist der Anfang von "Nutella", kein Lebensmittel-Wort: Das Wortende von "minute" zählt nicht.
        val products = search("nute").products.map { it.name }
        assertEquals("Nutella", products.first())
        assertTrue(products.indexOf("Rindsplätzli à la minute") > 0)
        // "milch" steht in der Nährwerttabelle (Kategorie "Milch und Milchprodukte"): Das Wortende zählt.
        assertEquals("Vollmilch, pasteurisiert", search("milch").generic.first().name)
    }

    @Test
    fun suche_mitLadenUndMarke() {
        // "migros" passt über den Laden, auch wenn die Marke "M-Classic" heißt.
        assertEquals(listOf("Joghurt Erdbeer"), search("migros joghurt").products.map { it.name })
        assertEquals(listOf("Joghurt nature"), search("coop joghurt").products.map { it.name })
        assertEquals(2, search("m-classic").products.size)
    }

    @Test
    fun suche_umlauteEgal() {
        assertEquals("Müeslimischung, gezuckert", search("muesli").generic.single().name)
        assertEquals("Müeslimischung, gezuckert", search("Müsli").generic.single().name)
        assertEquals("Hühnerei, ganz, roh", search("huehnerei roh").generic.single().name)
    }

    @Test
    fun suche_zuKurzOderLeer_findetNichts() {
        assertTrue(search("").isEmpty)
        assertTrue(search("e").isEmpty)
        assertTrue(search(" , ").isEmpty)
        assertTrue(search("gibtsnicht").isEmpty)
    }

    @Test
    fun suche_haeltSichAnsLimit() = runBlocking {
        val result = catalog.search("ei", genericLimit = 2, productLimit = 1)
        assertEquals(2, result.generic.size)
        assertEquals(1, result.products.size)
    }

    // --- Barcode und Lebensmittel ---

    @Test
    fun barcode_findetAlleCodesUndBeideSchreibweisen() = runBlocking {
        assertEquals("Nutella", catalog.byBarcode("3017620425035")?.name)
        assertEquals("Nutella", catalog.byBarcode("8000500082379")?.name)
        // Gespeichert als EAN-13 mit führender 0, gescannt als UPC-A (12 Ziffern), und umgekehrt.
        assertEquals("Peanut Butter", catalog.byBarcode("012345678905")?.name)
        assertNull(catalog.byBarcode("4000000000000"))
        // Keine Teilnummer: "301762042503" ist nicht "3017620425035".
        assertNull(catalog.byBarcode("301762042503"))
    }

    @Test
    fun zuLebensmittel_wertePro100gMitPortion() = runBlocking {
        val egg = catalog.search("Hühnerei roh").generic.single().toFood()
        assertEquals(0L, egg.id)
        assertEquals(FoodSource.BLV, egg.source)
        assertEquals(FoodBasis.PER_100G, egg.basis)
        assertEquals(140.0, egg.kcal, 1e-9)
        assertEquals(12.6, egg.proteinG!!, 1e-9)
        assertEquals(55.0, egg.portionG!!, 1e-9)
        assertNull(egg.barcode)

        val nutella = catalog.byBarcode("8000500082379")!!.toFood()
        assertEquals(FoodSource.OFF, nutella.source)
        assertEquals("3017620425035", nutella.barcode) // der erste Code
        assertEquals("Nutella", nutella.brand)
        assertEquals(1000.0, nutella.packageG!!, 1e-9)
    }

    @Test
    fun zeileOhnePortion_wirdTrotzdemGelesen() {
        // Die Zeile "Eierschwamm" endet ohne Tab für die leere Portion. Sie muss trotzdem gefunden werden.
        val mushroom = search("pfifferling").generic.single()
        assertEquals("Eierschwamm, roh", mushroom.name)
        assertNull(mushroom.portionG)
    }

    @Test
    fun fehlendeDateien_leereTabellenStattAbsturz() = runBlocking {
        val empty = FoodCatalog.empty()
        assertTrue(empty.search("ei").isEmpty)
        assertNull(empty.byBarcode("3017620425035"))
        assertTrue(empty.isLoaded)
    }

    // --- Die echten Dateien der App ---

    /** Die Dateien aus app/src/main/assets, so wie die App sie liest. Unit-Tests laufen im Ordner app/. */
    private val real = FoodCatalog({ name -> File("src/main/assets/$name").inputStream() })

    @Test
    fun echteDateien_ladenUndFinden() = runBlocking {
        val started = System.nanoTime()
        real.preload()
        val egg = real.search("ei")
        val loadMs = (System.nanoTime() - started) / 1_000_000
        assertEquals("Hühnerei, ganz, roh", egg.generic.first().name)
        assertEquals(55.0, egg.generic.first().portionG!!, 1e-9)
        assertEquals("Haselnuss-Schokolade-Brotaufstrich", real.search("nutella").generic.first().name)
        assertTrue(real.search("nutella").products.any { it.name == "Nutella" })
        assertTrue(real.search("migros joghurt").products.isNotEmpty())
        assertEquals("Nutella", real.byBarcode("3017620425035")?.name)

        // Eine Suche über alle Produkte muss schnell sein, sie läuft bei jedem Tippen.
        val searchStarted = System.nanoTime()
        repeat(10) { real.search("vollmilch") }
        val searchMs = (System.nanoTime() - searchStarted) / 1_000_000 / 10
        println("Nährwert-Tabellen: geladen in $loadMs ms, eine Suche $searchMs ms")
        assertTrue("Suche dauert $searchMs ms", searchMs < 200)
    }

    @Test
    fun echteDateien_wertePlausibel() {
        // Die BLV-Tabelle: jede Zeile mit Name und Kalorien zwischen 0 und 900, wie in naehrwerte.py geprüft.
        val lines = File("src/main/assets/${FoodCatalog.BLV_FILE}").readLines().filterNot { it.startsWith("#") }
        assertTrue("nur ${lines.size} Lebensmittel", lines.size > 1000)
        for (line in lines) {
            val c = line.split('\t')
            assertEquals(line, 9, c.size)
            assertFalse(line, c[1].isBlank())
            assertTrue(line, c[4].toDouble() in 0.0..900.0)
        }
        val products = runBlocking { real.search("schokolade", productLimit = 1000).products }
        assertTrue(products.size > 100)
        assertTrue(products.all { it.kcal in 0.0..900.0 && (it.proteinG ?: 0.0) in 0.0..100.0 })
    }
}
