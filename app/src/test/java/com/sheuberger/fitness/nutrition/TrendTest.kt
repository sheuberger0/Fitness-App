package com.sheuberger.fitness.nutrition

import com.sheuberger.fitness.data.BodyWeightEntry
import com.sheuberger.fitness.data.DayTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Prüft die Wochen-Auswertung: Durchschnittsgewicht, Änderung pro Woche, Kalorien und den Vorschlag für ein
 * neues Kalorienziel. "Heute" ist im ganzen Test Donnerstag, der 5. November 2026.
 */
class TrendTest {

    private val today = LocalDate.of(2026, 11, 5)
    private val goals = NutritionGoals(kcalTarget = 3800, weeklyGoalKg = 0.25)

    private fun weight(daysAgo: Long, kg: Double) = BodyWeightEntry(date = today.minusDays(daysAgo), weightKg = kg)

    private fun day(daysAgo: Long, kcal: Double) = DayTotal(today.minusDays(daysAgo), kcal, null)

    /** Je drei Wiegungen in der Vorwoche (vor 13, 10 und 7 Tagen) und in dieser Woche (vor 6, 3 Tagen und heute). */
    private fun weeks(lastWeek: Double, thisWeek: Double) = listOf(
        weight(13, lastWeek), weight(10, lastWeek), weight(7, lastWeek),
        weight(6, thisWeek), weight(3, thisWeek), weight(0, thisWeek),
    )

    /** Einträge an den letzten [days] Tagen vor heute (1 = gestern), je [kcal]. */
    private fun logged(days: Int, kcal: Double = 3700.0) = (1..days).map { day(it.toLong(), kcal) }

    private fun summarize(
        weights: List<BodyWeightEntry>,
        totals: List<DayTotal> = logged(7),
        goals: NutritionGoals = this.goals,
    ) = Trend.summarize(weights, totals, goals, today)

    // --- Gewicht ---

    @Test
    fun durchschnittDerLetztenSiebenTage_eineWiegungReicht() {
        assertEquals(80.0, summarize(listOf(weight(2, 80.0))).avgWeightKg!!, 1e-9)
        // Heute und vor 6 Tagen zählen mit, vor 7 Tagen nicht mehr.
        val summary = summarize(listOf(weight(0, 81.0), weight(6, 80.0), weight(7, 70.0)))
        assertEquals(80.5, summary.avgWeightKg!!, 1e-9)
    }

    @Test
    fun ohneWiegungDieseWoche_keinDurchschnitt() {
        val summary = summarize(listOf(weight(7, 80.0), weight(20, 79.0)))
        assertNull(summary.avgWeightKg)
        assertNull(summary.weeklyChangeKg)
    }

    @Test
    fun wochenAenderung_dieseWocheMinusVorwoche() {
        val weights = listOf(
            weight(13, 80.0), weight(10, 80.2), weight(7, 80.4), // Durchschnitt 80,2
            weight(6, 80.5), weight(3, 80.6), weight(0, 80.7), // Durchschnitt 80,6
        )
        assertEquals(0.4, summarize(weights).weeklyChangeKg!!, 1e-9)
    }

    @Test
    fun wochenAenderung_brauchtDreiWiegungenInJederWoche() {
        val full = weeks(lastWeek = 80.0, thisWeek = 80.3)
        assertNotNull(summarize(full).weeklyChangeKg)
        // Nur zwei Wiegungen in der Vorwoche bzw. in dieser Woche: Das schwankt zu stark.
        assertNull(summarize(full - full.first()).weeklyChangeKg)
        assertNull(summarize(full - full.last()).weeklyChangeKg)
        // Wiegungen vor mehr als 13 Tagen zählen nicht zur Vorwoche.
        assertNull(summarize(full - full.first() + weight(14, 80.0)).weeklyChangeKg)
    }

    // --- Kalorien ---

    @Test
    fun aufnahme_mittelDerTageMitEintraegen_ohneHeute() {
        val totals = listOf(
            day(0, 1200.0), // heute: noch nicht vorbei, zählt nicht
            day(1, 3600.0),
            day(3, 4000.0),
            day(7, 3800.0),
            day(8, 9999.0), // vor 8 Tagen: zu alt
        )
        val summary = summarize(listOf(weight(0, 80.0)), totals)
        assertEquals(3800.0, summary.avgIntakeKcal!!, 1e-9)
        assertEquals(3, summary.loggedDays)
    }

    @Test
    fun ohneEintraege_keineAufnahme() {
        val summary = summarize(listOf(weight(0, 80.0)), totals = listOf(day(0, 2000.0)))
        assertNull(summary.avgIntakeKcal)
        assertEquals(0, summary.loggedDays)
    }

    // --- Vorschlag ---

    @Test
    fun vorschlag_beispielAusDemPlan() {
        // Ziel +0,25 kg pro Woche, tatsächlich +0,05: Es fehlen 0,2 kg · 7700 kcal / 7 Tage = 220 kcal pro Tag.
        val suggestion = summarize(weeks(lastWeek = 80.0, thisWeek = 80.05)).suggestion!!
        assertEquals(4020, suggestion.newTargetKcal)
        assertEquals(220, suggestion.deltaKcal)
        assertEquals(0.05, suggestion.actualWeeklyChangeKg, 1e-9)
        assertEquals(0.25, suggestion.goalWeeklyChangeKg, 1e-9)
    }

    @Test
    fun vorschlag_nurMitFuenfTagenMitEintraegen() {
        val weights = weeks(lastWeek = 80.0, thisWeek = 80.05)
        assertNull(summarize(weights, logged(4)).suggestion)
        assertEquals(4020, summarize(weights, logged(5)).suggestion!!.newTargetKcal)
    }

    @Test
    fun vorschlag_ohneWochenAenderungKeiner() {
        assertNull(summarize(listOf(weight(0, 80.0), weight(8, 80.0)), logged(7)).suggestion)
    }

    @Test
    fun vorschlag_erstVierzehnTageNachDerLetztenPruefung() {
        val weights = weeks(lastWeek = 80.0, thisWeek = 80.05)
        assertNull(summarize(weights, goals = goals.copy(lastReview = today.minusDays(13))).suggestion)
        assertNull(summarize(weights, goals = goals.copy(lastReview = today)).suggestion)
        assertEquals(220, summarize(weights, goals = goals.copy(lastReview = today.minusDays(14))).suggestion!!.deltaKcal)
    }

    @Test
    fun vorschlag_hoechstensDreihundertKalorienAufEinmal() {
        // Abgenommen statt zugenommen: 0,75 kg fehlen pro Woche, das wären 825 kcal pro Tag.
        val more = summarize(weeks(lastWeek = 80.0, thisWeek = 79.5)).suggestion!!
        assertEquals(300, more.deltaKcal)
        assertEquals(4100, more.newTargetKcal)
        // Viel zu schnell zugenommen: −825 kcal, begrenzt auf −300.
        val less = summarize(weeks(lastWeek = 80.0, thisWeek = 81.0)).suggestion!!
        assertEquals(-300, less.deltaKcal)
        assertEquals(3500, less.newTargetKcal)
    }

    @Test
    fun vorschlag_aufZehnKalorienGerundet() {
        // Es fehlen 0,13 kg pro Woche: 143 kcal pro Tag, gerundet 140.
        val suggestion = summarize(weeks(lastWeek = 80.0, thisWeek = 80.12)).suggestion!!
        assertEquals(140, suggestion.deltaKcal)
        assertEquals(3940, suggestion.newTargetKcal)
    }

    @Test
    fun vorschlag_keinerUnterFuenfzigKalorien() {
        // Es fehlen 0,04 kg pro Woche: 44 kcal pro Tag, gerundet 40. So genau misst niemand.
        assertNull(summarize(weeks(lastWeek = 80.0, thisWeek = 80.21)).suggestion)
        // Genau im Ziel: nichts zu ändern.
        assertNull(summarize(weeks(lastWeek = 80.0, thisWeek = 80.25)).suggestion)
    }

    @Test
    fun vorschlag_neuesZielBleibtImErlaubtenBereich() {
        val gaining = weeks(lastWeek = 80.0, thisWeek = 79.5) // +300 kcal gewünscht
        val atMax = summarize(gaining, goals = goals.copy(kcalTarget = 5900)).suggestion!!
        assertEquals(NutritionGoals.MAX_KCAL, atMax.newTargetKcal)
        assertEquals(100, atMax.deltaKcal)
        // Schon an der Grenze: Es gibt nichts vorzuschlagen.
        assertNull(summarize(gaining, goals = goals.copy(kcalTarget = NutritionGoals.MAX_KCAL)).suggestion)

        val losing = weeks(lastWeek = 80.0, thisWeek = 81.0) // −300 kcal gewünscht
        val atMin = summarize(losing, goals = goals.copy(kcalTarget = 1300)).suggestion!!
        assertEquals(NutritionGoals.MIN_KCAL, atMin.newTargetKcal)
        assertEquals(-100, atMin.deltaKcal)
    }

    @Test
    fun vorschlag_auchBeimAbnehmen() {
        // Ziel −0,5 kg pro Woche, tatsächlich −0,2: 0,3 kg · 1100 = 330, begrenzt auf −300.
        val cut = goals.copy(kcalTarget = 2500, weeklyGoalKg = -0.5)
        val suggestion = summarize(weeks(lastWeek = 80.0, thisWeek = 79.8), goals = cut).suggestion!!
        assertEquals(-300, suggestion.deltaKcal)
        assertEquals(2200, suggestion.newTargetKcal)
    }
}
