package com.sheuberger.fitness.mensa

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class MensaRankingTest {

    private fun meal(
        name: String,
        price: Double,
        protein: Double?,
        kcal: Double = 700.0,
        mensa: Mensa = Mensa.Polymensa,
        side: Boolean = false,
    ) = Meal(
        mensa = mensa,
        time = MealTime.Lunch,
        line = "Linie",
        name = name,
        description = "",
        prices = mapOf(PriceGroup.Students to price, PriceGroup.External to price + 6),
        nutrition = protein?.let { Nutrition(kcal, it, null, null, null, estimated = false) },
        vegetarian = false,
        vegan = false,
        isSide = side,
    )

    private val meals = listOf(
        meal("Teuer", 12.5, protein = 50.0),
        meal("Burrito", 6.1, protein = 29.0, kcal = 938.0),
        meal("Schnitzel", 9.5, protein = 32.0, kcal = 731.0),
        meal("Gnocchi", 7.5, protein = 25.0, kcal = 1100.0),
        meal("Bowl", 9.5, protein = null),
        meal("Suppe", 4.9, protein = 2.0, side = true),
        meal("Platzhalter", 3.0, protein = 3.0, kcal = 104.0),
    )

    @Test
    fun nachProtein_nurImBudget_ohneBeilagen() {
        val r = MensaRanking.rank(meals, PriceGroup.Students, budget = 9.5, by = RankBy.Protein)
        // 9.50 zählt noch zum Budget, 12.50 nicht.
        assertEquals(listOf("Schnitzel", "Burrito", "Gnocchi"), r.ranked.map { it.meal.name })
        assertEquals(listOf("Bowl"), r.unrated.map { it.meal.name })
        assertEquals(1, r.tooExpensive)
    }

    @Test
    fun nachKalorienUndNachProteinProFranken() {
        val byKcal = MensaRanking.rank(meals, PriceGroup.Students, 9.5, RankBy.Calories)
        assertEquals(listOf("Gnocchi", "Burrito", "Schnitzel"), byKcal.ranked.map { it.meal.name })
        // Burrito: 29 g / 6.10 = 4.75 g pro Franken, Schnitzel 3.37, Gnocchi 3.33.
        val byValue = MensaRanking.rank(meals, PriceGroup.Students, 9.5, RankBy.Value)
        assertEquals(listOf("Burrito", "Schnitzel", "Gnocchi"), byValue.ranked.map { it.meal.name })
    }

    @Test
    fun preiskategorieUndMensaFilter() {
        // Für Externe kostet alles 6 Franken mehr: Mit 13 Franken bleibt nur der Burrito (12.10).
        val external = MensaRanking.rank(meals, PriceGroup.External, 13.0, RankBy.Protein)
        assertEquals(listOf("Burrito"), external.ranked.map { it.meal.name })

        val onlyUzh = MensaRanking.rank(meals, PriceGroup.Students, 9.5, RankBy.Protein, mensas = setOf(Mensa.UzhIrchel))
        assertTrue(onlyUzh.ranked.isEmpty())
    }

    @Test
    fun gleichesGerichtInMehrerenMensenErscheintEinmal() {
        val shared = listOf(
            meal("Steak", 7.5, protein = 38.0, mensa = Mensa.UzhObere),
            meal("Steak", 7.5, protein = 38.0, mensa = Mensa.UzhUntere),
            meal("Steak", 7.5, protein = 37.3, mensa = Mensa.UzhIrchel),
            meal("Steak", 8.5, protein = 30.0, mensa = Mensa.Polymensa),
        )
        val r = MensaRanking.rank(shared, PriceGroup.Students, 9.5, RankBy.Protein)
        assertEquals(2, r.ranked.size)
        assertEquals(Mensa.UzhObere, r.ranked[0].meal.mensa)
        assertEquals(listOf(Mensa.UzhUntere, Mensa.UzhIrchel), r.ranked[0].alsoAt)
        // Anderer Preis = anderes Angebot, bleibt eigener Eintrag.
        assertEquals(Mensa.Polymensa, r.ranked[1].meal.mensa)
    }

    @Test
    fun client_eineMensaFaelltAus_dieAnderenBleiben() = runBlocking {
        val eth = javaClass.getResource("/mensa/eth_polymensa.json")!!.readText()
        val client = MensaClient(http = { url ->
            if ("ethz.ch" in url) eth else throw IOException("offline")
        })
        val menu = client.load(LocalDate.of(2026, 11, 2))
        assertTrue(menu.meals.isNotEmpty())
        assertTrue(menu.meals.all { it.mensa == Mensa.Polymensa })
        assertEquals(setOf(Mensa.UzhObere, Mensa.UzhUntere, Mensa.UzhIrchel), menu.failed)
        // Mit Ausfällen wird nicht zwischengespeichert, der nächste Aufruf versucht es noch einmal.
        assertEquals(null, client.cached(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun client_liestUzhUeberWochenseiteUndGerichtsseiten() = runBlocking {
        val weekly = javaClass.getResource("/mensa/uzh_weekly.html")!!.readText()
        val dish = javaClass.getResource("/mensa/uzh_dish.html")!!.readText()
        val requested = mutableListOf<String>()
        val client = MensaClient(http = { url ->
            synchronized(requested) { requested += url }
            when {
                "ethz.ch" in url -> """{"weekly-rota-array":[]}"""
                "obere-mensa/lunch/menu/weekly" in url -> weekly
                "/menu/weekly" in url -> "<html></html>"
                else -> dish
            }
        })
        val menu = client.load(LocalDate.of(2026, 11, 3))
        assertTrue(menu.failed.isEmpty())
        assertEquals(4, menu.meals.size)
        assertTrue(requested.any { it.endsWith("obere-mensa/lunch/menu/weekly?week=45&year=2026") })
        assertTrue(client.cached(LocalDate.of(2026, 11, 3)) != null)
    }
}
