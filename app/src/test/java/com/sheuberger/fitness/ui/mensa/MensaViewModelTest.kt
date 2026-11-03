package com.sheuberger.fitness.ui.mensa

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MensaViewModelTest {

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
}
