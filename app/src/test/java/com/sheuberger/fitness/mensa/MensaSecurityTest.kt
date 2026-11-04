package com.sheuberger.fitness.mensa

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.LocalDate

/**
 * Prüft, dass kaputte, zu große oder manipulierte Antworten der Mensa-Server die App nicht abstürzen lassen
 * und keine Fantasiewerte in die Rangliste bringen.
 */
class MensaSecurityTest {

    private val day = LocalDate.of(2026, 11, 3)
    private val dishLink =
        "https://app.food2050.ch/de/zfv/universitat-zurich,campus-zentrum/obere-mensa/lunch,menu-2/2026-11-03"

    private fun resource(name: String) = javaClass.getResource("/mensa/$name")!!.readText()

    // --- readLimited ---

    @Test
    fun readLimited_unterDerGrenzeLiestAlles() {
        val text = "Grüezi Mensa ".repeat(2000) // über mehrere Blöcke, mit Umlauten
        val bytes = text.toByteArray(Charsets.UTF_8)
        val read = MensaClient.readLimited(ByteArrayInputStream(bytes), bytes.size.toLong(), Long.MAX_VALUE) { 0L }
        assertEquals(text, read)
    }

    @Test(expected = IOException::class)
    fun readLimited_ueberDerGrenzeGibtIOException() {
        MensaClient.readLimited(ByteArrayInputStream(ByteArray(11)), 10, Long.MAX_VALUE) { 0L }
    }

    @Test(expected = IOException::class)
    fun readLimited_abgelaufeneZeitGibtIOException() {
        MensaClient.readLimited(ByteArrayInputStream(ByteArray(10)), 100, deadlineNanos = 1_000) { 1_001 }
    }

    @Test
    fun readLimited_zeitLaeuftWaehrendDesLesensAb() {
        // Jeder Block kostet 1 s; nach 30 s muss abgebrochen werden, obwohl die Daten noch nicht zu Ende sind.
        var clock = 0L
        val endless = object : java.io.InputStream() {
            override fun read(): Int = 'a'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill('a'.code.toByte(), off, off + 1)
                return 1
            }
        }
        try {
            MensaClient.readLimited(endless, Long.MAX_VALUE, deadlineNanos = 30_000_000_000L) {
                clock.also { clock += 1_000_000_000L }
            }
            fail("IOException erwartet")
        } catch (e: IOException) {
            assertTrue(clock in 30_000_000_000L..33_000_000_000L)
        }
    }

    // --- dishLinks ---

    @Test
    fun dishLinks_nurEchteFood2050Links() {
        val html = """
            <a href="http://app.food2050.ch/de/zfv/x/lunch,menu-1/2026-11-03">a</a>
            <a href="https://app.food2050.ch.evil.com/de/zfv/x/lunch,menu-1/2026-11-03">b</a>
            <a href="https://evil.com/https://app.food2050.ch/de/zfv/x/lunch,menu-1/2026-11-03">c</a>
            <a href="https://evil.com/?u=https://app.food2050.ch/de/zfv/x/lunch,menu-1/2026-11-03">d</a>
            <a href="$dishLink">ok</a>
        """.trimIndent()
        assertEquals(listOf(dishLink), UzhMenuParser.dishLinks(html, day))
    }

    @Test
    fun dishLinks_hoechstens40() {
        val html = (1..500).joinToString("\n") {
            """<a href="https://app.food2050.ch/de/zfv/x/lunch,menu-$it/2026-11-03">x</a>"""
        }
        val links = UzhMenuParser.dishLinks(html, day)
        assertEquals(UzhMenuParser.MAX_DISH_LINKS, links.size)
        assertEquals(40, links.size)
    }

    @Test
    fun dishLinks_echteSeiteUnveraendert() {
        assertEquals(4, UzhMenuParser.dishLinks(resource("uzh_weekly.html"), day).size)
    }

    // --- robust gegen kaputte Antworten: Exceptions sind erlaubt (MensaClient fängt sie), Errors nicht ---

    private val nested = "[".repeat(10_000) + "]".repeat(10_000)

    private val badBodies: List<String>
        get() {
            val eth = resource("eth_polymensa.json")
            val uzh = resource("uzh_dish.html")
            return listOf(
                "",
                "<html><head><title>502 Bad Gateway</title></head><body><h1>Fehler</h1></body></html>",
                eth.substring(0, eth.length / 2),
                uzh.substring(0, uzh.length / 2),
                nested,
                """{"weekly-rota-array":$nested}""",
                "[".repeat(10_000),
                "{\"a\":".repeat(10_000),
                uzhPage("""{"menuItem":$nested}"""),
                uzhPage("""{"menuItem":${"{\"a\":".repeat(10_000)}"""),
                uzhPage(nested),
                """self.__next_f.push([1,"$nested"])""",
                uzhPage("""{"menuItem":{}}"""),
                uzhPage("""{"menuItem":{"dish":{"name":"X"}}}"""),
                ethBody("""{"name":"X"}"""),
                ethBody("{}"),
            ) + listOf("6,1", "NaN", "Infinity", "-5").flatMap { n ->
                listOf(
                    ethBody(ethMeal(energy = "\"$n\"", protein = "\"$n\"", price = "\"$n\"")),
                    ethBody(ethMeal(energy = n.replace(',', '.'), protein = "\"$n\"")),
                    uzhPage(uzhItem(kcal = "\"$n\"", protein = "\"$n\"", price = "\"$n\"")),
                )
            }
        }

    @Test
    fun ethParser_wirftKeinenError() {
        for (body in badBodies) {
            try {
                EthMenuParser.parse(body, LocalDate.of(2026, 11, 2))
            } catch (ignored: Exception) {
                // erlaubt: MensaClient behandelt das als Ausfall dieser Mensa
            }
        }
    }

    @Test
    fun uzhParser_wirftKeinenError() {
        for (body in badBodies) {
            try {
                UzhMenuParser.dishLinks(body, day)
                UzhMenuParser.parseDish(body, dishLink, Mensa.UzhObere, MealTime.Lunch)
            } catch (ignored: Exception) {
                // erlaubt: MensaClient behandelt das als Ausfall dieses Gerichts
            }
        }
    }

    // --- Plausibilität ---

    @Test
    fun eth_unendlicheKalorienGebenKeineNaehrwerte() {
        val meals = EthMenuParser.parse(ethBody(ethMeal(energy = "\"Infinity\"")), LocalDate.of(2026, 11, 2))
        assertNull(meals.single().nutrition)
    }

    @Test
    fun eth_negativesProteinGibtKeineNaehrwerte() {
        val meals = EthMenuParser.parse(ethBody(ethMeal(protein = "-5")), LocalDate.of(2026, 11, 2))
        assertNull(meals.single().nutrition)
    }

    @Test
    fun eth_unplausiblesPortionsgewichtGibtKeineNaehrwerte() {
        // 2900 kcal bei 50 kcal pro 100 g wären 5,8 kg.
        val meals = EthMenuParser.parse(
            ethBody(ethMeal(energy = "2900", kcal100 = "50", protein = "1")),
            LocalDate.of(2026, 11, 2),
        )
        assertNull(meals.single().nutrition)
    }

    @Test
    fun eth_preisUeber50WirdUebersprungen() {
        val meal = EthMenuParser.parse(ethBody(ethMeal(price = "120")), LocalDate.of(2026, 11, 2)).single()
        assertNull(meal.price(PriceGroup.Students))
        assertNotNull(meal.nutrition)
    }

    @Test
    fun eth_gueltigeWerteBleibenGleich() {
        val n = EthMenuParser.parse(ethBody(ethMeal()), LocalDate.of(2026, 11, 2)).single().nutrition!!
        assertEquals(584.0, n.kcal, 0.001)
        assertEquals(6.4 * 584 / 139, n.proteinG, 0.001)
        assertEquals(11 * 584 / 139.0, n.carbsG!!, 0.001)
        assertEquals(7.3 * 584 / 139, n.fatG!!, 0.001)
    }

    @Test
    fun uzh_unendlicheKalorienGebenKeineNaehrwerte() {
        val meal = UzhMenuParser.parseDish(uzhPage(uzhItem(kcal = "\"Infinity\"")), dishLink, Mensa.UzhObere, MealTime.Lunch)
        assertNull(meal!!.nutrition)
    }

    @Test
    fun uzh_negativesProteinGibtKeineNaehrwerte() {
        val meal = UzhMenuParser.parseDish(uzhPage(uzhItem(protein = "-5")), dishLink, Mensa.UzhObere, MealTime.Lunch)
        assertNull(meal!!.nutrition)
    }

    @Test
    fun uzh_preisUeber50WirdUebersprungen() {
        val meal = UzhMenuParser.parseDish(uzhPage(uzhItem(price = "120")), dishLink, Mensa.UzhObere, MealTime.Lunch)!!
        assertNull(meal.price(PriceGroup.Students))
        assertNotNull(meal.nutrition)
    }

    @Test
    fun uzh_unplausibleKohlenhydrateFehlenNur() {
        val meal = UzhMenuParser.parseDish(uzhPage(uzhItem(carbs = "\"NaN\"")), dishLink, Mensa.UzhObere, MealTime.Lunch)!!
        assertNull(meal.nutrition!!.carbsG)
        assertEquals(30.0, meal.nutrition!!.proteinG, 0.001)
    }

    @Test
    fun echteDatenWerdenWieBisherGelesen() {
        // Alle Gerichte der ETH-Datei (Montag 10, Dienstag 12) behalten ihre Nährwerte und alle drei Preise.
        // Mit Nährwerten waren es schon vorher 7 und 8: die anderen haben energy 0 oder gar keine Angaben.
        val monday = EthMenuParser.parse(resource("eth_polymensa.json"), LocalDate.of(2026, 11, 2))
        val tuesday = EthMenuParser.parse(resource("eth_polymensa.json"), day)
        assertEquals(10, monday.size)
        assertEquals(12, tuesday.size)
        assertEquals(7, monday.count { it.nutrition != null })
        assertEquals(8, tuesday.count { it.nutrition != null })
        val eth = monday + tuesday
        assertTrue(eth.all { it.prices.size == 3 })
        assertTrue(eth.mapNotNull { it.nutrition }.all { it.carbsG != null && it.fatG != null && it.weightG != null })
        val uzh = UzhMenuParser.parseDish(resource("uzh_dish.html"), dishLink, Mensa.UzhObere, MealTime.Lunch)!!
        val n = uzh.nutrition!!
        assertEquals(937.97, n.kcal, 0.01)
        assertEquals(28.86, n.proteinG, 0.01)
        assertEquals(110.81, n.carbsG!!, 0.01)
        assertEquals(829.0, n.weightG!!, 0.01)
        assertEquals(3, uzh.prices.size)
    }

    // --- Testdaten ---

    private fun ethMeal(
        energy: String = "584",
        kcal100: String = "139",
        protein: String = "6.4",
        price: String = "9.9",
    ) = """{"name":"Testgericht","energy":$energy,"energy-outdated":$kcal100,"protein":$protein,
        "carbohydrates":11,"fat":7.3,"meal-price-array":[{"price":$price,"customer-group-code":10}]}"""

    private fun ethBody(meal: String) = """{"weekly-rota-array":[{"facility-id":9,
        "valid-from":"2026-11-02","valid-to":"2026-11-08","day-of-week-array":[{"day-of-week-code":1,
        "opening-hour-array":[{"meal-time-array":[{"name":"Mittag","line-array":[{"name":"STREET",
        "meal":$meal}]}]}]}]}]}"""

    private fun uzhItem(
        kcal: String = "800",
        protein: String = "30",
        carbs: String = "90",
        price: String = "6.1",
    ) = """{"menuItem":{"dish":{"name":"Testgericht","stats":{"energy":{"amount":$kcal},
        "protein":{"amount":$protein},"carbohydrates":{"amount":$carbs},"fat":{"amount":20},
        "servingWeight":{"amount":500}}},"prices":[{"amount":$price,"priceCategory":{"name":"Studierende"}}]}}"""

    /** Verpackt [payload] wie food2050 in ein `self.__next_f.push([1,"…"])`-Skript. */
    private fun uzhPage(payload: String) =
        "<html><script>self.__next_f.push([1,${JsonPrimitive(payload)}])</script></html>"
}
