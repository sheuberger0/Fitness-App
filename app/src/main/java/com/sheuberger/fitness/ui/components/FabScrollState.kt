package com.sheuberger.fitness.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Klappt den Floating Action Button beim Runterscrollen zu einem Kreis ein und beim Hochscrollen
 * wieder aus (wie in Gmail). Wird per `Modifier.nestedScroll(...)` über der Liste eingehängt: Als
 * NestedScrollConnection bekommt sie jede Scroll-Bewegung mitgeteilt, verbraucht selbst aber nichts.
 * Gezählt wird nur, was die Liste wirklich gescrollt hat. Eine kurze Liste, die gar nicht scrollen
 * kann, lässt den FAB also ausgeklappt.
 */
@Stable
class FabScrollState : NestedScrollConnection {
    var expanded by mutableStateOf(true)
        private set

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Negativ = Inhalt wandert nach oben = Nutzer scrollt nach unten.
        // Kleine Schwelle, damit leichtes Zittern des Fingers den Button nicht hin- und herspringen lässt.
        if (consumed.y < -1f) expanded = false else if (consumed.y > 1f) expanded = true
        return Offset.Zero
    }
}

@Composable
fun rememberFabScrollState(): FabScrollState = remember { FabScrollState() }
