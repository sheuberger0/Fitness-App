package com.sheuberger.fitness.ui.mensa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.mensa.Mensa
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaSettings
import com.sheuberger.fitness.mensa.Nutrition
import com.sheuberger.fitness.mensa.PriceGroup
import com.sheuberger.fitness.mensa.RankedMeal
import com.sheuberger.fitness.mensa.Ranking
import com.sheuberger.fitness.nutrition.NutritionSettings
import com.sheuberger.fitness.nutrition.PortionOrigin
import com.sheuberger.fitness.nutrition.toEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.abs

/**
 * Prüft das ViewModel des Mensa-Tabs. Seit dem Kalorien-Tracker braucht es das Ernährungstagebuch, deshalb mit
 * Robolectric und einer echten Datenbank im Speicher. Die Menüs kommen aus einer Attrappe (echter ETH-Wochenplan).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MensaViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val nutrition = NutritionRepository(db)

    // Wie im Test des Sheets "Eintragen": Unconfined startet Coroutinen sofort im aufrufenden Thread, und die
    // Attrappe wartet nie. So ist das Laden der Menüs fertig, sobald das ViewModel steht.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val monday = LocalDate.of(2026, 11, 2)
    private val ethWeek = javaClass.getResource("/mensa/eth_polymensa.json")!!.readText()

    // Nur die Polymensa hat Menüs; die UZH-Wochenseiten sind leer.
    private val client = MensaClient { url -> if ("idapps.ethz.ch" in url) ethWeek else "<html></html>" }

    /** Das Kalorienziel ohne eigene Einstellung. */
    private val goal = 3800.0

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** Montag, 11:30 Uhr: Heute ist der erste Tag der Liste, gezeigt wird das Mittagessen. */
    private fun viewModel() = MensaViewModel(
        client = client,
        settings = MensaSettings(context),
        nutrition = nutrition,
        nutritionSettings = NutritionSettings(context),
        now = { monday.atTime(11, 30) },
        scope = scope,
    )

    /** Wartet, bis der Zustand passt. Ältere Werte (z. B. vor dem ersten Blick ins Tagebuch) werden übersprungen. */
    private fun MensaViewModel.stateWhere(predicate: (MensaUiState) -> Boolean): MensaUiState = runBlocking {
        withTimeout(5_000) { uiState.first(predicate) }
    }

    private fun log(kcal: Double, date: LocalDate = monday) = runBlocking {
        nutrition.log(FoodEntry(date = date, meal = MealSlot.BREAKFAST, name = "Porridge", kcal = kcal))
    }

    // --- Wochentage und Budget ---

    @Test
    fun wochentage_unterDerWocheDieseWoche_amWochenendeDieNaechste() {
        val tuesday = LocalDate.of(2026, 11, 3)
        assertEquals((2..6).map { LocalDate.of(2026, 11, it) }, MensaViewModel.weekdays(tuesday))
        val saturday = LocalDate.of(2026, 11, 7)
        assertEquals((9..13).map { LocalDate.of(2026, 11, it) }, MensaViewModel.weekdays(saturday))
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals((9..13).map { LocalDate.of(2026, 11, it) }, MensaViewModel.weekdays(sunday))
    }

    @Test
    fun budgetInFuenfzigRappenSchritten() {
        assertEquals(10.0, stepBudget(9.5, +1), 0.001)
        assertEquals(9.0, stepBudget(9.5, -1), 0.001)
        assertEquals(4.0, stepBudget(4.0, -1), 0.001)
    }

    // --- Was heute noch fehlt ---

    @Test
    fun heuteNoch_zielMinusDieEintraegeVonHeute() {
        log(650.0)
        log(1200.0, date = monday.minusDays(1)) // gestern: zählt nicht

        val state = viewModel().stateWhere { it.remainingKcal != null }

        assertEquals(goal - 650.0, state.remainingKcal!!, 0.001)
    }

    @Test
    fun heuteNoch_nurWennHeuteGewaehltIst() {
        val vm = viewModel()
        vm.stateWhere { it.remainingKcal != null }

        vm.selectDate(monday.plusDays(1))

        val tuesday = vm.stateWhere { it.date == monday.plusDays(1) && it.ranking != null }
        assertNull(tuesday.remainingKcal)
        assertTrue(tuesday.fitting.isEmpty())
    }

    @Test
    fun hinweisImKopf() {
        assertEquals("Heute noch 1450 kcal", MensaFormat.remainingToday(1450.4))
        assertEquals("Heute noch 51 kcal", MensaFormat.remainingToday(51.0))
        // ±50 kcal sind ein Schritt beim Einstellen des Ziels: So genau isst niemand.
        assertEquals("Ziel erreicht", MensaFormat.remainingToday(50.0))
        assertEquals("Ziel erreicht", MensaFormat.remainingToday(0.0))
        assertEquals("Ziel erreicht", MensaFormat.remainingToday(-50.0))
        assertEquals("320 kcal über dem Ziel", MensaFormat.remainingToday(-320.0))
    }

    // --- "passt" ---

    private fun dish(name: String, kcal: Double) = RankedMeal(
        meal = Meal(
            mensa = Mensa.Polymensa,
            time = MealTime.Lunch,
            line = "Street",
            name = name,
            description = "",
            prices = mapOf(PriceGroup.Students to 7.0),
            nutrition = Nutrition(kcal = kcal, proteinG = 30.0, carbsG = null, fatG = null, weightG = null, estimated = false),
            vegetarian = false,
            vegan = false,
            isSide = false,
        ),
        price = 7.0,
    )

    @Test
    fun passt_nurWennEsUnterscheidet() {
        val burger = dish("Burger", 1100.0)
        val curry = dish("Curry", 750.0)
        val salat = dish("Salat", 400.0)
        val ranking = Ranking(ranked = listOf(burger, curry, salat), unrated = emptyList(), tooExpensive = 0)

        assertEquals(setOf(curry.meal.id, salat.meal.id), MensaViewModel.fittingMeals(ranking, remainingKcal = 800.0))
        // Genau so viel, wie noch fehlt, passt auch.
        assertEquals(setOf(curry.meal.id, salat.meal.id), MensaViewModel.fittingMeals(ranking, remainingKcal = 750.0))
        // Morgens passt alles: Dann hätte jedes Gericht ein "passt", das sagt nichts.
        assertTrue(MensaViewModel.fittingMeals(ranking, remainingKcal = goal).isEmpty())
        // Nichts passt mehr.
        assertTrue(MensaViewModel.fittingMeals(ranking, remainingKcal = 300.0).isEmpty())
        assertTrue(MensaViewModel.fittingMeals(ranking, remainingKcal = -200.0).isEmpty())
        // Ohne Tagebuch (anderer Tag) oder ohne Menü.
        assertTrue(MensaViewModel.fittingMeals(ranking, remainingKcal = null).isEmpty())
        assertTrue(MensaViewModel.fittingMeals(null, remainingKcal = 800.0).isEmpty())
    }

    @Test
    fun passt_imZustandFuerHeute() {
        val vm = viewModel()
        val ranked = vm.stateWhere { it.ranking != null && it.remainingKcal != null }.ranking!!.ranked
        val kcals = ranked.map { it.nutrition.kcal }.sorted()
        assertTrue("Der Wochenplan braucht Gerichte mit verschiedenen Kalorien", kcals.first() < kcals.last())
        // So viel gegessen, dass nur noch die leichteren Gerichte passen.
        val remaining = (kcals.first() + kcals.last()) / 2
        log(goal - remaining)

        val state = vm.stateWhere { s -> s.remainingKcal?.let { abs(it - remaining) < 0.001 } == true }

        val expected = ranked.filter { it.nutrition.kcal <= remaining }.map { it.meal.id }.toSet()
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, state.fitting)
    }

    // --- Eintragen ---

    @Test
    fun eintragen_landetImTagebuch_undRueckgaengigNimmtEsWiederHeraus() {
        val vm = viewModel()
        val meal = vm.stateWhere { it.ranking != null && it.remainingKcal != null }.ranking!!.ranked.first().meal
        val values = runBlocking { vm.mensaPortion(meal) }
        val logged = CompletableDeferred<FoodEntry>()

        vm.log(meal.toEntry(1.0, values, monday, MealSlot.LUNCH)) { logged.complete(it) }

        val saved = runBlocking { withTimeout(5_000) { logged.await() } }
        assertNotEquals("Der Eintrag kommt mit seiner ID zurück (für Rückgängig)", 0L, saved.id)
        assertEquals(meal.name, saved.name)
        val after = vm.stateWhere { s -> s.remainingKcal?.let { abs(it - (goal - values.kcal)) < 0.001 } == true }
        assertEquals(goal - values.kcal, after.remainingKcal!!, 0.001)

        vm.undoLog(saved)

        vm.stateWhere { it.remainingKcal == goal }
        assertTrue(runBlocking { nutrition.day(monday).first() }.isEmpty())
    }

    @Test
    fun startwerte_ersteMalVonDerMensa_danachVomLetztenMal() {
        val vm = viewModel()
        val meal = vm.stateWhere { it.ranking != null }.ranking!!.ranked.first().meal

        val published = runBlocking { vm.mensaPortion(meal) }
        assertEquals(PortionOrigin.PUBLISHED, published.origin)
        assertEquals(meal.nutrition!!.kcal, published.kcal, 0.001)

        // Korrigiert eingetragen: 1½ Portionen zu je 800 kcal. Das nächste Mal startet das Gericht damit.
        runBlocking { nutrition.log(meal.toEntry(1.5, published.copy(kcal = 800.0, estimated = false), monday, MealSlot.LUNCH)) }

        val next = runBlocking { vm.mensaPortion(meal) }
        assertEquals(PortionOrigin.LAST_TIME, next.origin)
        assertEquals(800.0, next.kcal, 0.001)
    }
}
