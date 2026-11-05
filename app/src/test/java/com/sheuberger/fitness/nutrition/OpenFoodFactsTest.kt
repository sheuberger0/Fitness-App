package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodSource
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.FIELDS
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.IN_STORE_CODE
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.INVALID_CODE
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.MAX_BYTES
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.NO_CONNECTION
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.PRODUCT_URL
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.RATE_LIMITED
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.SERVER_ERROR
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.TOO_LARGE
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.UNEXPECTED
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.USER_AGENT
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.cleanText
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient.Companion.readLimited
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Prüft die Barcode-Suche bei Open Food Facts ohne Internet: Eine Attrappe ersetzt den Server und merkt sich
 * jede Anfrage, Pausen werden nur notiert statt abgewartet. Die Dateien unter test/resources/off sind echte
 * Antworten der API vom 4. November 2026 (gekürzt auf die Felder, die die App anfordert).
 */
class OpenFoodFactsTest {

    private val requests = mutableListOf<String>()
    private val pauses = mutableListOf<Long>()

    private fun resource(name: String) = javaClass.getResource("/off/$name")!!.readText()

    /** Client, dessen "Server" der Reihe nach [answers] liefert. */
    private fun client(vararg answers: OpenFoodFactsClient.Response): OpenFoodFactsClient {
        val queue = ArrayDeque(answers.toList())
        return OpenFoodFactsClient(
            http = { url ->
                requests += url
                queue.removeFirstOrNull() ?: error("Mehr Anfragen als erwartet")
            },
            pause = { pauses += it },
            now = { NOW },
        )
    }

    /** Client, dessen "Server" bei jeder Anfrage [error] wirft (wie HttpURLConnection ohne Netz). */
    private fun failingClient(error: Exception) = OpenFoodFactsClient(
        http = { url ->
            requests += url
            throw error
        },
        pause = { pauses += it },
        now = { NOW },
    )

    private fun ok(body: String) = OpenFoodFactsClient.Response(200, body)
    private fun status(code: Int, body: String = "") = OpenFoodFactsClient.Response(code, body)

    private fun lookup(code: String, vararg answers: OpenFoodFactsClient.Response): OffLookup =
        runBlocking { client(*answers).lookup(code) }

    private fun found(result: OffLookup): Food {
        assertTrue("Erwartet: gefunden, war: $result", result is OffLookup.Found)
        return (result as OffLookup.Found).food
    }

    /** Ein Produkt mit genau diesen Nährwerten (JSON-Text, z. B. `"energy-kcal_100g": 120`). */
    private fun product(nutriments: String, extra: String = "") =
        """{"code":"3017620422003","status":1,"product":{"product_name":"Test"$extra,"nutriments":{$nutriments}}}"""

    private fun parse(body: String): OffLookup = OpenFoodFactsClient.parse(body, "3017620422003", NOW)

    // --- Echte Antworten ---

    @Test
    fun nutella_mitPackungsgroesseOhnePortion() {
        val food = found(lookup("3017620422003", ok(resource("nutella_3017620422003.json"))))
        assertEquals("Nutella", food.name)
        assertEquals("Nutella", food.brand)
        assertEquals("3017620422003", food.barcode)
        assertEquals(FoodSource.OFF, food.source)
        assertEquals(FoodBasis.PER_100G, food.basis)
        assertEquals(539.0, food.kcal, 1e-9)
        assertEquals(6.3, food.proteinG!!, 1e-9)
        assertEquals(57.5, food.carbsG!!, 1e-9)
        assertEquals(30.9, food.fatG!!, 1e-9)
        // "serving_quantity" fehlt, nur die Einheit steht da: keine Portion.
        assertNull(food.portionG)
        assertEquals(400.0, food.packageG!!, 1e-9)
        assertEquals(NOW, food.fetchedAt)
        // Neu von Open Food Facts: noch nicht gespeichert, nicht bearbeitet, kein Favorit.
        assertEquals(0L, food.id)
        assertFalse(food.edited)
        assertFalse(food.favorite)
    }

    @Test
    fun toblerone_deutscherNameUndPortion() {
        val food = found(lookup("7614500010013", ok(resource("toblerone_7614500010013.json"))))
        // product_name ist Englisch, product_name_de geht vor.
        assertEquals("Milchschokolade mit Honig und Mandel-Torrone", food.name)
        assertEquals("Toblerone", food.brand)
        assertEquals(529.0, food.kcal, 1e-9)
        assertEquals(5.6, food.proteinG!!, 1e-9)
        assertEquals(61.0, food.carbsG!!, 1e-9)
        assertEquals(28.0, food.fatG!!, 1e-9)
        assertEquals(25.0, food.portionG!!, 1e-9)
        assertNull(food.packageG)
    }

    @Test
    fun orangensaft_deutscherNameStattFranzoesisch() {
        val food = found(lookup("7613312052358", ok(resource("orangensaft_7613312052358.json"))))
        assertEquals("Orangensaft", food.name)
        // "M-Classic, Migros": die erste Marke.
        assertEquals("M-Classic", food.brand)
        assertEquals(40.0, food.kcal, 1e-9)
        assertEquals(0.5, food.proteinG!!, 1e-9)
        assertEquals(10.0, food.carbsG!!, 1e-9)
        assertEquals(0.5, food.fatG!!, 1e-9)
        assertNull(food.portionG)
    }

    @Test
    fun migrosJoghurt_ean8MitZweiVorneIstKeinLadencode() {
        val food = found(lookup("22039750", ok(resource("migros_joghurt_22039750.json"))))
        assertEquals("Müesli Classic Yogurt", food.name)
        assertEquals("Elsa", food.brand)
        assertEquals("22039750", food.barcode)
        assertEquals(149.8, food.kcal, 1e-9)
        assertEquals(4.0, food.proteinG!!, 1e-9)
        assertEquals(19.0, food.carbsG!!, 1e-9)
        assertEquals(6.2, food.fatG!!, 1e-9)
        assertEquals(150.0, food.portionG!!, 1e-9)
        assertEquals(1, requests.size)
    }

    @Test
    fun alleEchtenProdukte_habenPlausibleWerte() {
        val fixtures = mapOf(
            "3017620422003" to "nutella_3017620422003.json",
            "7614500010013" to "toblerone_7614500010013.json",
            "7613312052358" to "orangensaft_7613312052358.json",
            "22039750" to "migros_joghurt_22039750.json",
        )
        for ((code, file) in fixtures) {
            val food = found(lookup(code, ok(resource(file))))
            assertTrue(file, food.kcal in 0.0..900.0)
            for (grams in listOfNotNull(food.proteinG, food.carbsG, food.fatG)) assertTrue(file, grams in 0.0..100.0)
            // Mehr Makros als 100 g pro 100 g gibt es nicht.
            assertTrue(file, (food.proteinG ?: 0.0) + (food.carbsG ?: 0.0) + (food.fatG ?: 0.0) <= 100.0)
            assertEquals(code, food.barcode)
        }
    }

    @Test
    fun unbekannterCode_jsonStatusNull() {
        // Ältere API-Versionen antworteten mit HTTP 200 und "status": 0.
        assertEquals(OffLookup.NotFound, lookup("7641239876504", ok(resource("nicht_gefunden_7641239876504.json"))))
    }

    @Test
    fun unbekannterCode_http404() {
        assertEquals(
            OffLookup.NotFound,
            lookup("7641239876504", status(404, resource("nicht_gefunden_7641239876504.json"))),
        )
        assertEquals(1, requests.size)
    }

    @Test
    fun produktOhneKalorien_giltAlsNichtGefunden() {
        // Dann öffnet die App das Formular zum Selbst-Eintragen.
        assertEquals(OffLookup.NotFound, parse(product(""""proteins_100g": 10""")))
        assertEquals(OffLookup.NotFound, parse("""{"status":1,"product":{"product_name":"Ohne Nährwerte"}}"""))
    }

    // --- Fehler des Servers ---

    @Test
    fun http429_genauEineAnfrageOhneWiederholung() {
        assertEquals(OffLookup.Failed(RATE_LIMITED), lookup("3017620422003", status(429)))
        assertEquals(1, requests.size)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun http503_zweiWiederholungenMitWachsenderPause() {
        val unavailable = status(503, resource("nicht_verfuegbar_503.html"))
        assertEquals(OffLookup.Failed(SERVER_ERROR), lookup("3017620422003", unavailable, unavailable, unavailable))
        assertEquals(3, requests.size)
        assertEquals(listOf(1_000L, 3_000L), pauses)
    }

    @Test
    fun http503_dannErfolg() {
        val food = found(lookup("3017620422003", status(503), ok(resource("nutella_3017620422003.json"))))
        assertEquals("Nutella", food.name)
        assertEquals(listOf(1_000L), pauses)
    }

    @Test
    fun andererHttpStatus_meldetDenStatus() {
        val result = lookup("3017620422003", status(418))
        assertTrue(result is OffLookup.Failed && result.message.contains("418"))
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun keineVerbindung_meldungOhneBarcode() {
        val result = runBlocking { failingClient(IOException("failed to connect to ${PRODUCT_URL}3017620422003")).lookup("3017620422003") }
        assertEquals(OffLookup.Failed(NO_CONNECTION), result)
        // Die Meldung der Exception enthält die Adresse mit dem Barcode und landet deshalb nicht in der App.
        assertFalse((result as OffLookup.Failed).message.contains("3017620422003"))
        assertEquals(1, requests.size)
    }

    @Test
    fun zeitueberschreitung_istKeineVerbindung() {
        val result = runBlocking { failingClient(SocketTimeoutException("Read timed out")).lookup("3017620422003") }
        assertEquals(OffLookup.Failed(NO_CONNECTION), result)
    }

    @Test
    fun antwortUeberZweiMegabyte_wirdAbgebrochen() {
        val huge = OpenFoodFactsClient(
            // Wie download(): Der Inhalt läuft durch readLimited.
            http = { OpenFoodFactsClient.Response(200, readLimited(ByteArrayInputStream(ByteArray(MAX_BYTES + 1)))) },
            pause = { pauses += it },
            now = { NOW },
        )
        assertEquals(OffLookup.Failed(TOO_LARGE), runBlocking { huge.lookup("3017620422003") })
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun readLimited_liestBisZurGrenzeAllesMitUmlauten() {
        // Über mehrere 8-KB-Blöcke. Durch die 6 Zeichen vorne liegt ein "ü" (zwei Bytes) genau auf der
        // ersten Blockgrenze (Byte 8191 und 8192) und darf dort nicht zerschnitten werden.
        val text = "Birne " + "Müesli ".repeat(5_000)
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, readLimited(ByteArrayInputStream(bytes), limit = bytes.size))
        try {
            readLimited(ByteArrayInputStream(bytes), limit = bytes.size - 1)
            fail("TooLargeException erwartet")
        } catch (e: TooLargeException) {
            // erwartet
        }
    }

    @Test
    fun keinJson_unerwarteteAntwort() {
        // Z. B. die Anmeldeseite eines WLANs, die mit HTTP 200 antwortet.
        assertEquals(OffLookup.Failed(UNEXPECTED), lookup("3017620422003", ok(resource("nicht_verfuegbar_503.html"))))
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("[]"))
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("\"Fehler\""))
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("""{"status":1}"""))
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("""{"status":1,"product":"kaputt"}"""))
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("""{"status":1,"product":{"""))
    }

    @Test
    fun tiefVerschachteltesJson_stuerztNichtAb() {
        // Eine kaputte oder manipulierte Antwort mit Tausenden Klammern darf die App nicht abstürzen lassen.
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("[".repeat(100_000) + "]".repeat(100_000)))
        assertEquals(OffLookup.Failed(UNEXPECTED), parse("""{"product":""" + "{\"a\":".repeat(10_000)))
    }

    // --- Der Barcode wird vor der Anfrage geprüft ---

    @Test
    fun ungueltigerCode_ohneAnfrage() {
        for (code in listOf("123", "4006381333932", "abcdefgh", "../3017620422003", "3017620422003?fields=*", "")) {
            assertEquals(code, OffLookup.Failed(INVALID_CODE), lookup(code))
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun ladencode_ohneAnfrage() {
        // Abgewogenes Poulet aus der Migros: Die Nummer gilt nur im Laden.
        assertEquals(OffLookup.Failed(IN_STORE_CODE), lookup("2181113008607"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun adresseEnthaeltNurDieGepruefteNummer() {
        lookup(" 3017 620422003\n", ok(resource("nutella_3017620422003.json")))
        val url = requests.single()
        assertEquals("${PRODUCT_URL}3017620422003.json?fields=$FIELDS", url)
        assertTrue(url.startsWith("https://world.openfoodfacts.org/api/v2/product/3017620422003.json?"))
        // In der Adresse stehen nach dem Pfad nur die festen Feldnamen.
        assertTrue(url.substringAfter("?fields=").all { it.isLetterOrDigit() || it == '_' || it == ',' })
    }

    @Test
    fun userAgent_ohneEmailAdresse() {
        assertFalse(USER_AGENT.contains('@'))
        assertTrue(USER_AGENT.startsWith("FitnessApp/"))
    }

    // --- Nährwerte ---

    @Test
    fun kalorienAusKilojoule() {
        // 1000 kJ / 4,184 = 239,0 kcal
        assertEquals(239.0, found(parse(product(""""energy-kj_100g": 1000"""))).kcal, 1e-9)
        // Das alte Feld "energy" ist immer kJ.
        assertEquals(538.2, found(parse(product(""""energy_100g": 2252"""))).kcal, 1e-9)
    }

    @Test
    fun kilojouleImKalorienfeld_nimmtDieUmgerechnetenKilojoule() {
        // 2252 kcal pro 100 g gibt es nicht: Da steht der kJ-Wert im falschen Feld.
        val food = found(parse(product(""""energy-kcal_100g": 2252, "energy-kj_100g": 2252""")))
        assertEquals(538.2, food.kcal, 1e-9)
    }

    @Test
    fun kalorienAusDerPortion() {
        // 132 kcal in 25 g sind 528 kcal pro 100 g.
        val food = found(parse(product(""""energy-kcal_serving": 132, "proteins_serving": 1.4""", ""","serving_quantity": 25""")))
        assertEquals(528.0, food.kcal, 1e-9)
        assertEquals(5.6, food.proteinG!!, 1e-9)
    }

    @Test
    fun unplausibleWerteFallenWeg() {
        // Über 900 kcal pro 100 g (mehr als reines Öl) und keine kJ: keine brauchbaren Kalorien.
        assertEquals(OffLookup.NotFound, parse(product(""""energy-kcal_100g": 950""")))
        assertEquals(OffLookup.NotFound, parse(product(""""energy-kcal_100g": -10""")))

        val food = found(
            parse(
                product(
                    """"energy-kcal_100g": 120, "proteins_100g": 120, "carbohydrates_100g": -3, "fat_100g": 100""",
                    ""","serving_quantity": 5000, "product_quantity": 0""",
                ),
            ),
        )
        assertEquals(120.0, food.kcal, 1e-9)
        assertNull(food.proteinG) // über 100 g pro 100 g
        assertNull(food.carbsG) // negativ
        assertEquals(100.0, food.fatG!!, 1e-9) // reines Fett ist möglich
        assertNull(food.portionG) // 5 kg sind keine Portion
        assertNull(food.packageG)
    }

    @Test
    fun zahlenAlsTextMitKomma() {
        val food = found(parse(product(""""energy-kcal_100g": "120", "proteins_100g": "12,5", "fat_100g": "3 g"""")))
        assertEquals(120.0, food.kcal, 1e-9)
        assertEquals(12.5, food.proteinG!!, 1e-9)
        assertNull(food.fatG) // Text mit Einheit gilt als fehlend
    }

    @Test
    fun portionInMilliliternZaehltWieGramm_andereEinheitNicht() {
        val drink = found(parse(product(""""energy-kcal_100g": 40""", ""","serving_quantity": 250, "serving_quantity_unit": "ml"""")))
        assertEquals(250.0, drink.portionG!!, 1e-9)
        val pieces = found(parse(product(""""energy-kcal_100g": 40""", ""","serving_quantity": 2, "serving_quantity_unit": "Stück"""")))
        assertNull(pieces.portionG)
    }

    @Test
    fun aufEineNachkommastelleGerundet() {
        val food = found(parse(product(""""energy-kcal_100g": 123.456, "proteins_100g": 7.04""")))
        assertEquals(123.5, food.kcal, 1e-9)
        assertEquals(7.0, food.proteinG!!, 1e-9)
    }

    // --- Namen ---

    @Test
    fun ohneNamen_heisstEsNachDemBarcode() {
        val food = found(parse("""{"status":1,"product":{"product_name":"  ","nutriments":{"energy-kcal_100g":100}}}"""))
        assertEquals("Produkt 3017620422003", food.name)
        assertNull(food.brand)
    }

    @Test
    fun markenAlsListe() {
        val food = found(parse(product(""""energy-kcal_100g": 100""", ""","brands": ["", "Migros", "M-Classic"]""")))
        assertEquals("Migros", food.brand)
    }

    @Test
    fun cleanText_entferntSteuerzeichenUndUnsichtbares() {
        assertEquals("Nutella Light", cleanText("  Nu\u0000tel‮la \n\t Light\r\n", 80))
        // Nullbreites Leerzeichen und Zeichen, die die Schreibrichtung umdrehen, fallen weg.
        assertEquals("ZeroWidth", cleanText("Zero​Width⁦", 80))
        // Geschütztes Leerzeichen wird zu einem normalen.
        assertEquals("Le Gruyère AOP", cleanText("Le Gruyère  AOP", 80))
        assertNull(cleanText(" \u0000\n ", 80))
        assertNull(cleanText(null, 80))
    }

    @Test
    fun cleanText_begrenztDieLaenge() {
        assertEquals(80, cleanText("a".repeat(500), 80)!!.length)
        assertEquals("Sehr langer", cleanText("Sehr langer Name", 12))
        // Ein Emoji (zwei Zeichen) wird nicht in der Mitte zerschnitten.
        assertEquals("a".repeat(79), cleanText("a".repeat(79) + "🍫" + "b", 80))
    }

    @Test
    fun langerNameAusDemInternet_wirdGekuerzt() {
        val food = found(parse(product(""""energy-kcal_100g": 100""", ""","product_name_de": "${"Schokolade ".repeat(20)}"""")))
        assertTrue(food.name.length <= 80)
        assertTrue(food.name.startsWith("Schokolade Schokolade"))
    }

    private companion object {
        const val NOW = 1_762_300_000_000L
    }
}
