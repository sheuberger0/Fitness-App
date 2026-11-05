package com.sheuberger.fitness.ui.nutrition

import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.mensa.MealTime
import com.sheuberger.fitness.nutrition.NutritionGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Eingaben in den Sheets: Mengen, Schnell-Einträge, Bearbeiten, Ziele und womit "Eintragen" startet. */
class NutritionInputTest {

    private val thursday = LocalDate.of(2026, 11, 5)
    private val saturday = LocalDate.of(2026, 11, 7)

    // --- Menge ---

    @Test
    fun portionen_inHalbenSchritten_aufsRaster() {
        assertEquals(1.5, stepAmount(1.0, EntryUnit.PORTION, +1), 1e-9)
        assertEquals(1.5, stepAmount(1.3, EntryUnit.PORTION, +1), 1e-9)
        assertEquals(1.0, stepAmount(1.3, EntryUnit.PORTION, -1), 1e-9)
        assertEquals(1.0, stepAmount(1.5, EntryUnit.PORTION, -1), 1e-9)
        // Nie 0: Bei einer halben Portion bleibt "−" stehen.
        assertEquals(0.5, stepAmount(0.5, EntryUnit.PORTION, -1), 1e-9)
        assertEquals(50.0, stepAmount(50.0, EntryUnit.PORTION, +1), 1e-9)
    }

    @Test
    fun gramm_unterFuenfzigInFuenferSchritten_sonstInZehnern() {
        assertEquals(160.0, stepAmount(150.0, EntryUnit.GRAM, +1), 1e-9)
        assertEquals(140.0, stepAmount(150.0, EntryUnit.GRAM, -1), 1e-9)
        assertEquals(130.0, stepAmount(123.0, EntryUnit.GRAM, +1), 1e-9)
        assertEquals(50.0, stepAmount(45.0, EntryUnit.GRAM, +1), 1e-9)
        assertEquals(60.0, stepAmount(50.0, EntryUnit.GRAM, +1), 1e-9)
        assertEquals(45.0, stepAmount(50.0, EntryUnit.GRAM, -1), 1e-9)
        assertEquals(50.0, stepAmount(55.0, EntryUnit.GRAM, -1), 1e-9)
        assertEquals(5.0, stepAmount(5.0, EntryUnit.GRAM, -1), 1e-9)
        assertEquals(5000.0, stepAmount(5000.0, EntryUnit.GRAM, +1), 1e-9)
    }

    @Test
    fun menge_leerNullOderUnrealistisch_istUngueltig() {
        assertEquals(1.5, parseAmount("1,5", EntryUnit.PORTION)!!, 1e-9)
        assertEquals(250.0, parseAmount("250", EntryUnit.GRAM)!!, 1e-9)
        assertNull(parseAmount("", EntryUnit.GRAM))
        assertNull(parseAmount("0", EntryUnit.PORTION))
        assertNull(parseAmount("6000", EntryUnit.GRAM))
        assertNull(parseAmount("60", EntryUnit.PORTION))
    }

    // --- Sheet "Eintragen" ---

    @Test
    fun startsegment_mensaMittagsUndAbendsAnWerktagen_sonstZuletzt() {
        assertEquals(AddSource.Mensa, defaultSource(MealSlot.LUNCH, thursday, hasRecent = true))
        assertEquals(AddSource.Mensa, defaultSource(MealSlot.DINNER, thursday, hasRecent = false))
        assertEquals(AddSource.Recent, defaultSource(MealSlot.LUNCH, saturday, hasRecent = true))
        assertEquals(AddSource.Recent, defaultSource(MealSlot.BREAKFAST, thursday, hasRecent = true))
        // Erster Start: Unter "Zuletzt" steht noch nichts, also gleich zum Scannen und Anlegen.
        assertEquals(AddSource.Foods, defaultSource(MealSlot.BREAKFAST, thursday, hasRecent = false))
    }

    @Test
    fun mensaMenue_nachDerMahlzeit_sonstNachDerUhrzeit() {
        // Mittagessen am Abend nachtragen: trotzdem das Mittagsmenü.
        assertEquals(MealTime.Lunch, initialMensaTime(MealSlot.LUNCH, isToday = true, now = LocalTime.of(19, 0)))
        assertEquals(MealTime.Dinner, initialMensaTime(MealSlot.DINNER, isToday = true, now = LocalTime.of(9, 0)))
        assertEquals(MealTime.Dinner, initialMensaTime(MealSlot.SNACK, isToday = true, now = LocalTime.of(16, 0)))
        assertEquals(MealTime.Lunch, initialMensaTime(MealSlot.SNACK, isToday = false, now = LocalTime.of(16, 0)))
    }

    @Test
    fun schnell_kalorienSindPflicht_proteinMussPassen() {
        assertEquals(350.0, parseQuickKcal("350")!!, 1e-9)
        assertNull(parseQuickKcal("0"))
        assertNull(quickKcalError(""))
        assertEquals("Zwischen 1 und 10000 kcal", quickKcalError("20000"))

        assertNull(quickProteinError("", null, 350.0))
        assertNull(quickProteinError("20", 20.0, 350.0))
        // 100 g Protein sind schon 400 kcal, mehr als der ganze Eintrag hat: wohl Kalorien und Protein vertauscht.
        assertEquals("Mehr Protein, als die Kalorien hergeben (1 g ≈ 4 kcal)", quickProteinError("100", 100.0, 350.0))
        assertEquals("Ungültige Zahl", quickProteinError(",", null, 350.0))
    }

    // --- Eintrag bearbeiten ---

    @Test
    fun bearbeiten_dieMengeRechnetAllesMit() {
        val oats = FoodEntry(
            id = 7, date = thursday, loggedAt = 42, meal = MealSlot.LUNCH, name = "Haferflocken",
            kcal = 300.0, proteinG = 10.0, carbsG = 50.0, fatG = 5.0, amount = 80.0, unit = EntryUnit.GRAM,
            source = EntrySource.FOOD,
        )
        val edited = editedEntry(oats, amount = 120.0, slot = MealSlot.BREAKFAST, name = "", kcalText = "", proteinText = "")!!

        assertEquals(450.0, edited.kcal, 1e-9)
        assertEquals(15.0, edited.proteinG!!, 1e-9)
        assertEquals(75.0, edited.carbsG!!, 1e-9)
        assertEquals(MealSlot.BREAKFAST, edited.meal)
        // Name, ID und Zeitpunkt bleiben: Es ist derselbe Eintrag.
        assertEquals("Haferflocken", edited.name)
        assertEquals(7L, edited.id)
        assertEquals(42L, edited.loggedAt)
    }

    @Test
    fun bearbeiten_schnell_dieFelderGelten() {
        val quick = FoodEntry(id = 3, date = thursday, meal = MealSlot.SNACK, name = NutritionFormat.QUICK_NAME, kcal = 350.0, proteinG = 5.0)

        val cake = editedEntry(quick, amount = 2.0, slot = MealSlot.SNACK, name = " Kuchen ", kcalText = "700", proteinText = "10")!!
        assertEquals("Kuchen", cake.name)
        assertEquals(700.0, cake.kcal, 1e-9)
        assertEquals(10.0, cake.proteinG!!, 1e-9)
        assertEquals(2.0, cake.amount, 1e-9)

        // Ohne Namen wieder der Standardname, leeres Protein heißt "unbekannt", ohne Kalorien kein Speichern.
        val unnamed = editedEntry(quick, amount = 1.0, slot = MealSlot.SNACK, name = "", kcalText = "350", proteinText = "")!!
        assertEquals(NutritionFormat.QUICK_NAME, unnamed.name)
        assertNull(unnamed.proteinG)
        assertNull(editedEntry(quick, amount = 1.0, slot = MealSlot.SNACK, name = "Kuchen", kcalText = "", proteinText = ""))
    }

    // --- Ziele ---

    @Test
    fun ziele_springenAufsRaster_undBleibenInDenGrenzen() {
        assertEquals(4050, stepKcalTarget(4020, +1))
        assertEquals(4000, stepKcalTarget(4020, -1))
        assertEquals(3850, stepKcalTarget(3800, +1))
        assertEquals(3750, stepKcalTarget(3800, -1))
        assertEquals(NutritionGoals.MAX_KCAL, stepKcalTarget(NutritionGoals.MAX_KCAL, +1))
        assertEquals(NutritionGoals.MIN_KCAL, stepKcalTarget(NutritionGoals.MIN_KCAL, -1))
        assertEquals(155, stepProteinTarget(150, +1))
        assertEquals(150, stepProteinTarget(152, -1))
    }

    @Test
    fun wochenziel_inSchrittenVonFuenfHundertstelKilo() {
        assertEquals(0.3, stepWeeklyGoal(0.25, +1), 1e-9)
        assertEquals(0.2, stepWeeklyGoal(0.25, -1), 1e-9)
        assertEquals(-0.05, stepWeeklyGoal(0.0, -1), 1e-9)
        assertEquals(0.0, stepWeeklyGoal(-0.05, +1), 1e-9)
        assertEquals(-0.3, stepWeeklyGoal(-0.25, -1), 1e-9)
        assertEquals(1.0, stepWeeklyGoal(1.0, +1), 1e-9)
        assertEquals(-1.0, stepWeeklyGoal(-1.0, -1), 1e-9)
    }

    @Test
    fun protein_proKiloKoerpergewicht() {
        assertEquals("≈ 1,9 g pro kg Körpergewicht. Für Muskelaufbau gelten 1,6 bis 2,2 g als sinnvoll.", proteinHint(150, 78.0))
        assertEquals("Für Muskelaufbau gelten 1,6 bis 2,2 g pro kg Körpergewicht als sinnvoll.", proteinHint(150, null))
    }
}
