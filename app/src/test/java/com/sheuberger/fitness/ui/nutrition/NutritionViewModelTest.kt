package com.sheuberger.fitness.ui.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.nutrition.NutritionSettings
import com.sheuberger.fitness.nutrition.TargetSuggestion
import com.sheuberger.fitness.nutrition.withAmount
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NutritionViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val nutrition = NutritionRepository(db)
    private val fitness = FitnessRepository(db)
    private val settings = NutritionSettings(context)

    // Eigener Scope statt des Haupt-Threads: Der ist im Test mit dem Test selbst beschäftigt (siehe defaultViewModelScope).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Feste Uhr, die ein Test vorstellen kann: Donnerstag, 5. November 2026, mittags.
    private var clock = LocalDateTime.of(2026, 11, 5, 12, 30)
    private val today = clock.toLocalDate()
    private val yesterday = today.minusDays(1)

    private val vm = NutritionViewModel(nutrition, settings, fitness, now = { clock }, scope = scope)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** Wartet, bis die Tagesansicht geladen ist und [predicate] erfüllt. Das Zeitlimit lässt einen Fehler scheitern statt hängen. */
    private fun awaitState(predicate: (NutritionUiState) -> Boolean = { true }): NutritionUiState = runBlocking {
        withTimeout(5_000) { vm.uiState.first { !it.loading && predicate(it) } }
    }

    private fun <T> await(deferred: CompletableDeferred<T>): T = runBlocking { withTimeout(5_000) { deferred.await() } }

    private fun log(name: String, kcal: Double, date: LocalDate = today, meal: MealSlot = MealSlot.LUNCH, at: Long = 1): Long =
        runBlocking { nutrition.log(FoodEntry(date = date, loggedAt = at, meal = meal, name = name, kcal = kcal, proteinG = kcal / 20)) }

    // --- Tag wählen ---

    @Test
    fun zukuenftigeTage_lassenSichNichtWaehlen() {
        assertFalse(awaitState().canGoForward)

        vm.selectDate(today.plusDays(3))
        vm.previousDay()
        // Wäre der Tag in der Zukunft gelandet, stünde jetzt der übermorgige Tag da und nicht gestern.
        val state = awaitState { it.date != today }
        assertEquals(yesterday, state.date)
        assertTrue(state.canGoForward)

        vm.nextDay()
        assertTrue(awaitState { it.date == today }.isToday)
        vm.nextDay()
        vm.previousDay()
        assertEquals(yesterday, awaitState { it.date != today }.date)
    }

    @Test
    fun heute_springtVonEinemFruehenTagZurueck() {
        vm.selectDate(today.minusDays(10))
        assertEquals(today.minusDays(10), awaitState { !it.isToday }.date)

        vm.showToday()
        assertTrue(awaitState { it.isToday }.isToday)
    }

    @Test
    fun nachMitternacht_wirdHeuteZumNeuenTag() {
        awaitState { it.isToday }
        clock = LocalDateTime.of(2026, 11, 6, 0, 10)
        vm.refreshToday()

        val state = awaitState { it.today == today.plusDays(1) }
        // Wer "Heute" offen hatte, sieht den neuen Tag.
        assertEquals(today.plusDays(1), state.date)
        assertTrue(state.isToday)
    }

    @Test
    fun nachMitternacht_bleibtEinFruehererTagGewaehlt() {
        vm.previousDay()
        awaitState { it.date == yesterday }
        clock = LocalDateTime.of(2026, 11, 6, 0, 10)
        vm.refreshToday()

        val state = awaitState { it.today == today.plusDays(1) }
        assertEquals(yesterday, state.date)
        assertTrue(state.canGoForward)
    }

    // --- Tage kopieren ---

    @Test
    fun gesternUebernehmen_nurAnEinemLeerenHeute() {
        log("Müesli", 600.0, date = yesterday, meal = MealSlot.BREAKFAST, at = 1)
        log("Pasta", 900.0, date = yesterday, at = 2)
        log("Apfel", 80.0, date = yesterday, meal = MealSlot.SNACK, at = 3)

        val before = awaitState { it.yesterdayLogged }
        assertTrue(before.canCopyYesterday)
        assertEquals(1580.0, before.yesterdayKcal!!, 1e-9)

        val copied = CompletableDeferred<Int>()
        vm.copyDay(yesterday, today) { copied.complete(it) }
        assertEquals(3, await(copied))

        val after = awaitState { it.entries.size == 3 }
        assertEquals(1580.0, after.totals.kcal, 1e-9)
        // Heute ist nicht mehr leer: Die Karte verschwindet.
        assertFalse(after.canCopyYesterday)
    }

    @Test
    fun gesternUebernehmen_nichtOhneEintraegeVonGestern() {
        log("Pasta", 900.0, date = today.minusDays(2))
        val state = awaitState { it.hasHistory }
        assertFalse(state.yesterdayLogged)
        assertFalse(state.canCopyYesterday)
    }

    @Test
    fun aufHeuteKopieren_nurFuerFruehereTageMitEintraegen() {
        log("Pasta", 900.0, date = yesterday)
        assertFalse(awaitState { it.hasHistory }.canCopyToToday)

        vm.previousDay()
        assertTrue(awaitState { it.date == yesterday && it.entries.isNotEmpty() }.canCopyToToday)

        vm.previousDay()
        assertFalse(awaitState { it.date == yesterday.minusDays(1) }.canCopyToToday)
    }

    // --- Mahlzeit für "+" ---

    @Test
    fun vorgeschlageneMahlzeit_heuteNachUhrzeit() {
        val state = awaitState { it.isToday }
        assertEquals(MealSlot.BREAKFAST, state.suggestedSlot(LocalTime.of(8, 0)))
        assertEquals(MealSlot.LUNCH, state.suggestedSlot(LocalTime.of(12, 30)))
        assertEquals(MealSlot.SNACK, state.suggestedSlot(LocalTime.of(16, 0)))
        assertEquals(MealSlot.DINNER, state.suggestedSlot(LocalTime.of(19, 0)))
    }

    @Test
    fun vorgeschlageneMahlzeit_anFrueherenTagenDieErsteLeere() {
        log("Müesli", 600.0, date = yesterday, meal = MealSlot.BREAKFAST)
        vm.previousDay()

        val state = awaitState { it.date == yesterday && it.entries.isNotEmpty() }
        // Nach dem Frühstück fehlt das Mittagessen, egal wie spät es gerade ist.
        assertEquals(MealSlot.LUNCH, state.suggestedSlot(LocalTime.of(8, 0)))
        assertEquals(MealSlot.LUNCH, state.suggestedSlot(LocalTime.of(20, 0)))
    }

    // --- Einträge ---

    @Test
    fun bearbeiteterEintrag_ersetztDenAlten() {
        log("Pasta", 900.0)
        val entry = awaitState { it.entries.size == 1 }.entries.single()

        vm.updateEntry(entry.withAmount(1.5).copy(meal = MealSlot.DINNER))

        val state = awaitState { it.entries.singleOrNull()?.meal == MealSlot.DINNER }
        assertEquals(1350.0, state.totals.kcal, 1e-9)
        assertEquals(entry.id, state.entries.single().id)
    }

    @Test
    fun geloeschterEintrag_kommtMitRueckgaengigWieder() {
        val id = log("Pasta", 900.0)
        val entry = awaitState { it.entries.size == 1 }.entries.single()

        vm.deleteEntry(entry)
        awaitState { it.entries.isEmpty() }
        vm.restoreEntry(entry)

        // Mit der alten ID, also an derselben Stelle wie vorher.
        assertEquals(id, awaitState { it.entries.size == 1 }.entries.single().id)
    }

    @Test
    fun vorlage_ausEinerMahlzeit() {
        log("Müesli", 600.0, meal = MealSlot.BREAKFAST, at = 1)
        log("Kaffee", 50.0, meal = MealSlot.BREAKFAST, at = 2)
        log("Pasta", 900.0, at = 3)
        val breakfast = awaitState { it.entries.size == 3 }.sections.first { it.slot == MealSlot.BREAKFAST }

        val saved = CompletableDeferred<Unit>()
        vm.saveTemplate("Frühstück Standard", breakfast) { saved.complete(Unit) }
        await(saved)

        val template = runBlocking { nutrition.templates.first() }.single()
        assertEquals("Frühstück Standard", template.template.name)
        assertEquals(listOf("Müesli", "Kaffee"), template.items.map { it.name })
    }

    @Test
    fun ersterStart_ohneEintraege_istOhneVerlauf() {
        assertFalse(awaitState().hasHistory)
        log("Pasta", 900.0, date = yesterday)
        assertTrue(awaitState { it.hasHistory }.hasHistory)
    }

    // --- Ziele und Vorschläge ---

    @Test
    fun vorschlagUebernehmen_setztZielUndPause() {
        vm.acceptSuggestion(TargetSuggestion(newTargetKcal = 4020, deltaKcal = 220, actualWeeklyChangeKg = 0.0, goalWeeklyChangeKg = 0.25))

        val goals = awaitState { it.goals.kcalTarget == 4020 }.goals
        assertEquals(today, goals.lastReview)
        assertEquals(goals, settings.goals.value)
    }

    @Test
    fun vorschlagVerschieben_behaeltZielUndPausiert() {
        vm.postponeSuggestion()

        val goals = settings.goals.value
        assertEquals(NutritionGoals.DEFAULT_KCAL, goals.kcalTarget)
        assertEquals(today, goals.lastReview)
    }

    @Test
    fun kalorienzielVonHand_pausiertVorschlaege_proteinNicht() {
        vm.updateGoals { it.copy(proteinTargetG = 160) }
        assertNull(settings.goals.value.lastReview)

        vm.updateGoals { it.copy(kcalTarget = 4000) }
        assertEquals(4000, settings.goals.value.kcalTarget)
        assertEquals(today, settings.goals.value.lastReview)
    }

    @Test
    fun vorschlag_erscheintMitGenugDaten_undRuhtNachDemUebernehmen() {
        // Zwei Wochen gleiches Gewicht, geplant sind +0,25 kg pro Woche: Es fehlen Kalorien.
        runBlocking {
            for (daysAgo in 1L..13L) fitness.saveBodyWeight(BodyWeightEntry(date = today.minusDays(daysAgo), weightKg = 75.0))
        }
        for (daysAgo in 1L..6L) log("Pasta", 3800.0, date = today.minusDays(daysAgo))

        val suggestion = awaitState { it.trend?.suggestion != null }.trend!!.suggestion!!
        assertTrue(suggestion.newTargetKcal > NutritionGoals.DEFAULT_KCAL)

        vm.acceptSuggestion(suggestion)
        val state = awaitState { it.goals.kcalTarget == suggestion.newTargetKcal }
        assertNull(state.trend!!.suggestion)
        // Statt eines neuen Vorschlags steht da, wann das Ziel wieder geprüft wird.
        assertTrue(trendHint(state.trend!!, state.goals, today)!!.startsWith("Nächste Prüfung deines Ziels"))
    }
}
