package com.sheuberger.fitness.ui.components

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstest für "die Gewichtskurve erscheint erst nach Klick auf ein Zeitintervall".
 *
 * Compose zeichnet eine Fläche nur dann neu, wenn sich ein Zustand ändert, den sie beim letzten Zeichnen GELESEN hat.
 * Der Graph prüft beim Zeichnen zuerst, ob seine Startanimation schon läuft. War das eine gewöhnliche Variable,
 * bekam Compose den Start nicht mit: Lief das erste Zeichnen vor dem Start (z. B. in einer Liste, die ihre
 * Einträge erst beim Layout aufbaut), blieb der Graph leer, bis etwas anderes ihn neu zeichnete.
 */
class LineChartTest {

    @Test
    fun drawingNoticesWhenTheAnimationStarts() {
        val animation = ChartAnimation()
        val reads = mutableSetOf<Any>()
        // So liest auch die Zeichenphase: Snapshot.observe meldet jeden beobachteten Zustand, der gelesen wird.
        Snapshot.observe(readObserver = { reads += it }) {
            animation.started
            animation.currentGrid
            animation.previousGrid
        }
        assertTrue("started, currentGrid und previousGrid müssen beobachtet werden", reads.size >= 3)
    }
}
