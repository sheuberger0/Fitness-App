package com.sheuberger.fitness.ui.nutrition

import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.nutrition.TargetSuggestion
import com.sheuberger.fitness.nutrition.TrendSummary
import com.sheuberger.fitness.ui.components.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionFormatTest {

    private val day = LocalDate.of(2026, 11, 5)

    @Test
    fun rest_bisFuenfzigKcalDanebenGiltAlsErreicht() {
        assertEquals("Noch 1450 kcal", NutritionFormat.remaining(1450.4))
        assertEquals("Noch 51 kcal", NutritionFormat.remaining(51.0))
        assertEquals("Ziel erreicht", NutritionFormat.remaining(50.0))
        assertEquals("Ziel erreicht", NutritionFormat.remaining(-50.0))
        assertEquals("320 kcal drüber", NutritionFormat.remaining(-320.0))
        assertTrue(NutritionFormat.goalReached(-50.0))
        assertFalse(NutritionFormat.goalReached(51.0))
    }

    @Test
    fun nachDemEintragen_wasBisZumZielBleibt() {
        assertEquals("Danach noch 220 kcal bis zum Ziel", NutritionFormat.afterLogging(220.0))
        assertEquals("Damit erreichst du dein Ziel", NutritionFormat.afterLogging(-20.0))
        assertEquals("Damit 120 kcal über dem Ziel", NutritionFormat.afterLogging(-120.0))
    }

    @Test
    fun zweiteZeile_mensaMitLinie_mengeNurWennNichtGenauEinePortion() {
        val mensa = FoodEntry(
            date = day, meal = MealSlot.LUNCH, name = "Pasta", kcal = 800.0,
            source = EntrySource.MENSA, mensa = "Polymensa", mensaLine = "Street",
        )
        assertEquals("Polymensa · Street", NutritionFormat.entryDetail(mensa))
        assertEquals("Polymensa · Street · 1,5 Portionen", NutritionFormat.entryDetail(mensa.copy(amount = 1.5)))
        assertEquals("Mensa", NutritionFormat.entryDetail(mensa.copy(mensa = null, mensaLine = null)))

        val food = FoodEntry(
            date = day, meal = MealSlot.BREAKFAST, name = "Haferflocken", kcal = 300.0,
            amount = 80.0, unit = EntryUnit.GRAM, source = EntrySource.FOOD,
        )
        assertEquals("80 g", NutritionFormat.entryDetail(food))

        // Bei Schnell-Einträgen sagt "1 Portion" nichts.
        val quick = FoodEntry(date = day, meal = MealSlot.SNACK, name = "Kuchen", kcal = 350.0)
        assertNull(NutritionFormat.entryDetail(quick))
        assertEquals("2 Portionen", NutritionFormat.entryDetail(quick.copy(amount = 2.0)))
    }

    @Test
    fun wochenziel_alsKalorienProTag() {
        assertEquals(
            "+0,25 kg pro Woche ≈ 275 kcal Überschuss pro Tag, weil 1 kg Körpergewicht etwa 7700 kcal entspricht.",
            NutritionFormat.dailyBalance(0.25),
        )
        assertEquals(
            "−0,50 kg pro Woche ≈ 550 kcal Defizit pro Tag, weil 1 kg Körpergewicht etwa 7700 kcal entspricht.",
            NutritionFormat.dailyBalance(-0.5),
        )
        assertEquals("Gewicht halten: so viel essen, wie du verbrauchst.", NutritionFormat.dailyBalance(0.0))
    }

    @Test
    fun vorschlag_nenntBeideWerteProWoche() {
        val suggestion = TargetSuggestion(newTargetKcal = 4020, deltaKcal = 220, actualWeeklyChangeKg = 0.05, goalWeeklyChangeKg = 0.25)
        assertEquals(
            "Geplant sind +0,25 kg pro Woche, zuletzt waren es +0,05 kg pro Woche.",
            NutritionFormat.suggestionReason(suggestion),
        )
    }

    @Test
    fun namen_inAnfuehrungszeichen_langeGekuerzt() {
        assertEquals("„Pasta“", NutritionFormat.quoted("  Pasta "))
        assertEquals("„Älplermagronen mit Apfelmus…“", NutritionFormat.quoted("Älplermagronen mit Apfelmus und Röstzwiebeln"))
    }

    @Test
    fun anzahlDerEintraege() {
        assertEquals("1 Eintrag", NutritionFormat.entries(1))
        assertEquals("3 Einträge", NutritionFormat.entries(3))
    }

    @Test
    fun trendHinweis_sagtWasNochFehlt() {
        val goals = NutritionGoals()
        val noWeights = TrendSummary(avgWeightKg = 75.0, weeklyChangeKg = null, avgIntakeKcal = 3700.0, loggedDays = 7, suggestion = null)
        assertTrue(trendHint(noWeights, goals, day)!!.startsWith("Wiege dich an mindestens 3 Tagen pro Woche"))

        val fewDays = noWeights.copy(weeklyChangeKg = 0.2, loggedDays = 4)
        assertTrue(trendHint(fewDays, goals, day)!!.startsWith("Trag an mindestens 5 von 7 Tagen ein"))

        // Mit Vorschlag spricht der Vorschlag für sich.
        val enough = fewDays.copy(loggedDays = 6)
        assertNull(trendHint(enough.copy(suggestion = TargetSuggestion(4020, 220, 0.0, 0.25)), goals, day))

        val paused = goals.copy(lastReview = day.minusDays(4))
        assertEquals(
            "Nächste Prüfung deines Ziels frühestens am ${Format.shortDate(day.plusDays(10))}.",
            trendHint(enough, paused, day),
        )
        assertEquals("Du liegst im Plan, dein Kalorienziel passt.", trendHint(enough, goals, day))
    }
}
