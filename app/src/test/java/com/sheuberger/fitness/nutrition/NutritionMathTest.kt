package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.mensa.Mensa
import com.sheuberger.fitness.mensa.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NutritionMathTest {

    private val day = LocalDate.of(2026, 11, 4)

    private val oats = Food(id = 7, name = "Haferflocken", kcal = 370.0, proteinG = 13.0, carbsG = 59.0, fatG = 7.0, portionG = 50.0)
    private val shake = Food(id = 8, name = "Proteinshake", basis = FoodBasis.PER_PORTION, kcal = 120.0, proteinG = 24.0)

    private fun meal(nutrition: Nutrition?) = Meal(
        mensa = Mensa.Polymensa,
        time = MealTime.Lunch,
        line = "Street",
        name = "Pasta Arrabbiata",
        description = "Penne, Tomatensauce",
        prices = emptyMap(),
        nutrition = nutrition,
        vegetarian = true,
        vegan = false,
        isSide = false,
    )

    @Test
    fun gramsOfAPer100gFood() {
        val entry = oats.toEntry(80.0, EntryUnit.GRAM, day, MealSlot.BREAKFAST, now = 1)!!
        assertEquals(296.0, entry.kcal, 1e-9)
        assertEquals(10.4, entry.proteinG!!, 1e-9)
        assertEquals(EntrySource.FOOD, entry.source)
        assertEquals(7L, entry.foodId)
    }

    @Test
    fun portionsUseThePortionWeight() {
        val entry = oats.toEntry(2.0, EntryUnit.PORTION, day, MealSlot.BREAKFAST)!!
        assertEquals(370.0, entry.kcal, 1e-9) // 2 × 50 g = 100 g
    }

    @Test
    fun perPortionFoodWithoutWeightOnlyAcceptsPortions() {
        assertEquals(240.0, shake.toEntry(2.0, EntryUnit.PORTION, day, MealSlot.SNACK)!!.kcal, 1e-9)
        assertNull(shake.toEntry(30.0, EntryUnit.GRAM, day, MealSlot.SNACK))
        assertEquals(listOf(EntryUnit.PORTION), shake.units())
        assertEquals(listOf(EntryUnit.PORTION, EntryUnit.GRAM), oats.units())
    }

    @Test
    fun zeroOrNegativeAmountIsRejected() {
        assertNull(oats.toEntry(0.0, EntryUnit.GRAM, day, MealSlot.SNACK))
        assertNull(oats.toEntry(-5.0, EntryUnit.GRAM, day, MealSlot.SNACK))
    }

    @Test
    fun changingTheAmountScalesAllValues() {
        val entry = FoodEntry(date = day, meal = MealSlot.LUNCH, name = "Pasta", kcal = 800.0, proteinG = 30.0, fatG = null, amount = 1.0)
        val bigger = entry.withAmount(1.5)
        assertEquals(1200.0, bigger.kcal, 1e-9)
        assertEquals(45.0, bigger.proteinG!!, 1e-9)
        assertNull(bigger.fatG)
        assertEquals(1.5, bigger.amount, 1e-9)
    }

    @Test
    fun totalsTreatMissingMacrosAsZero() {
        val entries = listOf(
            FoodEntry(date = day, meal = MealSlot.LUNCH, name = "A", kcal = 500.0, proteinG = 20.0),
            FoodEntry(date = day, meal = MealSlot.LUNCH, name = "B", kcal = 250.0, proteinG = null, carbsG = 30.0),
        )
        assertEquals(Totals(750.0, 20.0, 30.0, 0.0), entries.totals())
    }

    @Test
    fun mensaPortionPrefersYourLastEntryThenPublishedThenGuess() {
        val published = meal(Nutrition(850.0, 30.0, 100.0, 20.0, 400.0, estimated = true))
        val guess = { MensaEstimate.guess("Pasta Arrabbiata") }

        val fromMenu = mensaPortion(published, last = null, guess = guess)
        assertEquals(PortionOrigin.PUBLISHED, fromMenu.origin)
        assertEquals(850.0, fromMenu.kcal, 1e-9)
        assertTrue(fromMenu.estimated) // ETH-Protein ist berechnet

        val last = FoodEntry(date = day, meal = MealSlot.LUNCH, name = "Pasta Arrabbiata", kcal = 1500.0, proteinG = 50.0, amount = 1.5, source = EntrySource.MENSA)
        val remembered = mensaPortion(published, last, guess)
        assertEquals(PortionOrigin.LAST_TIME, remembered.origin)
        assertEquals(1000.0, remembered.kcal, 1e-9) // pro Portion
        assertEquals(50.0 / 1.5, remembered.proteinG!!, 1e-9)

        val unknown = mensaPortion(meal(null), last = null, guess = guess)
        assertEquals(PortionOrigin.GUESS, unknown.origin)
        assertEquals(800.0, unknown.kcal, 1e-9)
    }

    @Test
    fun mensaEntryCopiesDishAndPortion() {
        val dish = meal(Nutrition(850.0, 30.0, null, null, null, estimated = false))
        val entry = dish.toEntry(1.5, dish.publishedPortion()!!, day, MealSlot.LUNCH, now = 5)
        assertEquals("Pasta Arrabbiata", entry.name)
        assertEquals(1275.0, entry.kcal, 1e-9)
        assertEquals(45.0, entry.proteinG!!, 1e-9)
        assertEquals(EntrySource.MENSA, entry.source)
        assertEquals("Polymensa", entry.mensa)
        assertEquals("Street", entry.mensaLine)
        assertEquals(EntryUnit.PORTION, entry.unit)
    }

    @Test
    fun mealSlotFollowsTheTimeOfDay() {
        assertEquals(MealSlot.BREAKFAST, slotForTime(LocalTime.of(7, 0)))
        assertEquals(MealSlot.LUNCH, slotForTime(LocalTime.of(10, 30)))
        assertEquals(MealSlot.SNACK, slotForTime(LocalTime.of(15, 0)))
        assertEquals(MealSlot.DINNER, slotForTime(LocalTime.of(19, 0)))
        assertEquals(MealSlot.SNACK, slotForTime(LocalTime.of(22, 0)))
        assertEquals(MealSlot.DINNER, MealTime.Dinner.toSlot())
        assertEquals(MealTime.Lunch, mensaTimeFor(LocalTime.of(12, 0)))
        assertEquals(MealTime.Dinner, mensaTimeFor(LocalTime.of(17, 30)))
    }
}
