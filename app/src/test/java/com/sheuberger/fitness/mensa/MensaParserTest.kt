package com.sheuberger.fitness.mensa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Prüft das Lesen der echten Menüdaten. Die Dateien unter test/resources/mensa sind gekürzte Kopien
 * der Antworten von ETH (JSON) und food2050 (HTML) vom 2. und 3. November 2026.
 */
class MensaParserTest {

    private fun resource(name: String) = javaClass.getResource("/mensa/$name")!!.readText()

    @Test
    fun eth_liestMittagUndAbendMitPreisenUndPortionsProtein() {
        val meals = EthMenuParser.parse(resource("eth_polymensa.json"), LocalDate.of(2026, 11, 2))

        val lasagne = meals.single { it.name == "Lasagne Emiliana" }
        assertEquals(MealTime.Lunch, lasagne.time)
        assertEquals("Street", lasagne.line)
        assertEquals(9.9, lasagne.price(PriceGroup.Students)!!, 0.001)
        assertEquals(11.9, lasagne.price(PriceGroup.Staff)!!, 0.001)
        assertEquals(15.9, lasagne.price(PriceGroup.External)!!, 0.001)
        val n = lasagne.nutrition!!
        assertEquals(584.0, n.kcal, 0.01)
        // 6,4 g pro 100 g; Portion = 584 kcal / 139 kcal pro 100 g = 420 g → 26,9 g Protein.
        assertEquals(6.4 * 584 / 139, n.proteinG, 0.01)
        assertEquals(420.1, n.weightG!!, 0.1)
        assertTrue(n.estimated)
        assertFalse(lasagne.isSide)

        assertTrue(meals.any { it.time == MealTime.Dinner && it.name == "Butter Chicken" })
        assertTrue(meals.single { it.name == "Rotes Linsen Dal" && it.time == MealTime.Lunch }.vegan)
        // Suppe und Buffet nach Gewicht zählen nicht als Hauptgericht.
        assertTrue(meals.single { it.line == "Tagessuppe" }.isSide)
        assertTrue(meals.single { it.name == "Hot & Cold Buffet" }.isSide)
        // Poly-Bowls haben keine Nährwerte.
        assertNull(meals.first { it.name == "Poly-Bowls" }.nutrition)
    }

    @Test
    fun eth_ohneMenuAnDiesemTagIstDieListeLeer() {
        // Samstag steht ohne Mahlzeiten im Plan, Sonntag fehlt ganz, und Dezember liegt außerhalb.
        assertTrue(EthMenuParser.parse(resource("eth_polymensa.json"), LocalDate.of(2026, 11, 7)).isEmpty())
        assertTrue(EthMenuParser.parse(resource("eth_polymensa.json"), LocalDate.of(2026, 11, 8)).isEmpty())
        assertTrue(EthMenuParser.parse(resource("eth_polymensa.json"), LocalDate.of(2026, 12, 1)).isEmpty())
    }

    @Test
    fun uzh_findetDieGerichteDesTages() {
        val links = UzhMenuParser.dishLinks(resource("uzh_weekly.html"), LocalDate.of(2026, 11, 3))
        assertEquals(4, links.size)
        assertTrue(links.all { it.endsWith("/2026-11-03") })
        assertTrue(links.any { it.contains("obere-mensa/lunch,menu-2/") })
    }

    @Test
    fun uzh_liestGerichtMitPreisenUndNaehrwerten() {
        val link = "https://app.food2050.ch/de/zfv/universitat-zurich,campus-zentrum/obere-mensa/lunch,menu-2/2026-11-03"
        val meal = UzhMenuParser.parseDish(resource("uzh_dish.html"), link, Mensa.UzhObere, MealTime.Lunch)
        assertNotNull(meal)
        meal!!
        assertEquals("Burrito", meal.name)
        assertEquals("Menu 2", meal.line)
        assertEquals(6.1, meal.price(PriceGroup.Students)!!, 0.001)
        assertEquals(9.0, meal.price(PriceGroup.Staff)!!, 0.001)
        assertEquals(13.0, meal.price(PriceGroup.External)!!, 0.001)
        assertEquals(937.97, meal.nutrition!!.kcal, 0.01)
        assertEquals(28.86, meal.nutrition!!.proteinG, 0.01)
        assertEquals(829.0, meal.nutrition!!.weightG!!, 0.01)
        assertFalse(meal.nutrition!!.estimated)
        assertTrue(meal.vegetarian)
        assertFalse(meal.vegan)
        assertFalse(meal.isSide)
    }

    @Test
    fun uzh_seiteOhneGerichtGibtNull() {
        assertNull(UzhMenuParser.parseDish("<html></html>", "https://x/a,b/2026-11-03", Mensa.UzhObere, MealTime.Lunch))
    }

    @Test
    fun klammernInTextenStoerenNicht() {
        val text = """xx{"menuItem":{"name":"A {B} \"C\""}}yy"""
        assertEquals("""{"menuItem":{"name":"A {B} \"C\""}}""", UzhMenuParser.balancedObject(text, 2))
    }

    @Test
    fun grossbuchstabenWerdenRuhig() {
        assertEquals("Paniertes Schnitzel", "PANIERTES SCHNITZEL".calmCase())
        assertEquals("Lasagne Emiliana", "Lasagne Emiliana".calmCase())
        assertEquals("Hot & Cold 100g", "Hot & Cold 100g".calmCase())
    }
}
