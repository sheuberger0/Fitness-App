package com.sheuberger.fitness.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NutritionRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repo = NutritionRepository(db)
    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = monday.plusDays(1)

    @After
    fun tearDown() = db.close()

    private fun entry(name: String, kcal: Double, date: LocalDate = monday, meal: MealSlot = MealSlot.LUNCH, at: Long = 1) =
        FoodEntry(date = date, loggedAt = at, meal = meal, name = name, kcal = kcal, proteinG = kcal / 20)

    @Test
    fun dailyTotalsSumEachDay() = runBlocking {
        repo.log(entry("Müesli", 600.0, meal = MealSlot.BREAKFAST))
        repo.log(entry("Pasta", 900.0))
        repo.log(entry("Pasta", 800.0, date = tuesday))

        val totals = repo.dailyTotals(monday, tuesday).first()
        assertEquals(listOf(monday, tuesday), totals.map { it.date })
        assertEquals(1500.0, totals[0].kcal, 1e-9)
        assertEquals(75.0, totals[0].proteinG!!, 1e-9)
    }

    @Test
    fun copyDayDuplicatesEntriesToTheNewDate() = runBlocking {
        repo.log(entry("Müesli", 600.0, meal = MealSlot.BREAKFAST, at = 1))
        repo.log(entry("Pasta", 900.0, at = 2))

        assertEquals(2, repo.copyDay(monday, tuesday, now = 100))

        assertEquals(listOf("Müesli", "Pasta"), repo.day(tuesday).first().map { it.name })
        assertEquals(2, repo.day(monday).first().size) // Original bleibt
    }

    @Test
    fun deletingAFoodKeepsItsEntries() = runBlocking {
        val id = repo.saveFood(Food(name = "Proteinriegel", basis = FoodBasis.PER_PORTION, kcal = 200.0))
        repo.log(entry("Proteinriegel", 200.0).copy(foodId = id, source = EntrySource.FOOD))

        repo.deleteFood(repo.food(id)!!)

        val left = repo.day(monday).first().single()
        assertEquals("Proteinriegel", left.name)
        assertNull(left.foodId)
    }

    @Test
    fun aKnownBarcodeUpdatesInsteadOfDuplicating() = runBlocking {
        val first = repo.saveFood(Food(name = "Joghurt", barcode = "7610000000003", kcal = 60.0, favorite = true))
        val second = repo.saveFood(Food(name = "Joghurt nature", barcode = "7610000000003", kcal = 62.0))

        assertEquals(first, second)
        val food = repo.foods.first().single()
        assertEquals("Joghurt nature", food.name)
        assertEquals(true, food.favorite)
    }

    @Test
    fun recentEntriesShowEachNameOnceNewestFirst() = runBlocking {
        repo.log(entry("Pasta", 900.0, at = 1))
        repo.log(entry("Müesli", 600.0, at = 2))
        repo.log(entry("Pasta", 950.0, date = tuesday, at = 3))

        val recent = repo.recentEntries.first()
        assertEquals(listOf("Pasta", "Müesli"), recent.map { it.name })
        assertEquals(950.0, recent.first().kcal, 1e-9)
    }

    @Test
    fun lastMensaEntryRemembersTheDish() = runBlocking {
        repo.log(entry("Curry", 700.0).copy(source = EntrySource.MENSA, mensa = "Polymensa"))
        repo.log(entry("Curry", 1050.0, date = tuesday, at = 5).copy(source = EntrySource.MENSA, amount = 1.5))

        assertEquals(1050.0, repo.lastMensaEntry("curry")!!.kcal, 1e-9)
        assertEquals(listOf("Curry"), repo.recentMensa.first().map { it.name })
    }

    @Test
    fun templatesSaveAndApplyCopies() = runBlocking {
        val breakfast = listOf(entry("Müesli", 600.0, meal = MealSlot.BREAKFAST), entry("Kaffee", 40.0, meal = MealSlot.BREAKFAST))
        val id = repo.saveTemplate(" Frühstück Standard ", MealSlot.BREAKFAST, breakfast)

        val template = repo.templates.first().single()
        assertEquals("Frühstück Standard", template.template.name)
        assertEquals(640.0, template.totals.kcal, 1e-9)

        assertEquals(2, repo.applyTemplate(id, tuesday, MealSlot.BREAKFAST, now = 10))
        assertEquals(listOf("Müesli", "Kaffee"), repo.day(tuesday).first().map { it.name })

        repo.deleteTemplate(template.template)
        assertEquals(emptyList<TemplateWithItems>(), repo.templates.first())
        assertEquals(2, repo.day(tuesday).first().size) // Angewendete Einträge bleiben
    }
}
