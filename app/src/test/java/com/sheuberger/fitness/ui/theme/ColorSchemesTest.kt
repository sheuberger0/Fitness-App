package com.sheuberger.fitness.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Prüft alle Farbschemata auf Lesbarkeit, damit ein neues oder geändertes Schema nicht aus Versehen
 * schlecht lesbare Schrift bekommt.
 *
 * Maßstab ist der Kontrast nach WCAG: (hellere Leuchtdichte + 0,05) / (dunklere + 0,05). 1:1 heißt "gleiche Farbe",
 * 21:1 ist Schwarz auf Weiß. Normale Schrift braucht mindestens 4,5:1, so lautet die Stufe AA der Richtlinie.
 */
class ColorSchemesTest {

    @Test
    fun idsNeverChange() {
        // Die id steht in den Einstellungen auf dem Handy. Wird sie umbenannt, ist nach dem Update die Auswahl weg.
        // Neue Schemata hier hinten anhängen, bestehende nie ändern.
        val ids = AppColorScheme.entries.map { it.id }.toSet()
        val released = setOf(
            "terrakotta", "koralle", "himbeere", "rubin", "burgund", "maroon", "bordeaux_gold", "ochsenblut_creme",
            "bernstein", "sand", "limette", "smaragd", "dunkles_smaragd", "smaragd_gold", "salbei", "tuerkis",
            "himmelblau", "lavendel",
        )
        assertTrue("Fehlende id: ${released - ids}", ids.containsAll(released))
        assertEquals("Jede id nur einmal", AppColorScheme.entries.size, ids.size)
    }

    @Test
    fun unknownOrMissingIdGivesTheOriginal() {
        assertEquals(AppColorScheme.Terrakotta, AppColorScheme.fromId(null))
        assertEquals(AppColorScheme.Terrakotta, AppColorScheme.fromId("gibt_es_nicht"))
        assertEquals(AppColorScheme.Burgund, AppColorScheme.fromId("burgund"))
    }

    @Test
    fun onlyTheOriginalFollowsThePhone() {
        assertTrue(AppColorScheme.Terrakotta.followsSystem)
        assertFalse(AppColorScheme.Terrakotta.isDark(systemDark = false))
        assertTrue(AppColorScheme.Terrakotta.isDark(systemDark = true))
        AppColorScheme.entries.filter { it != AppColorScheme.Terrakotta }.forEach {
            assertTrue("${it.title} soll immer dunkel sein", it.isDark(systemDark = false))
        }
    }

    @Test
    fun originalKeepsItsColors() {
        // Wer beim Original bleibt, soll nach dem Update genau die bisherigen Farben sehen.
        val colors = AppColorScheme.Terrakotta.dark.toColorScheme()
        assertEquals(Color(0xFF1F1E1D), colors.background)
        assertEquals(Color(0xFF262624), colors.surfaceContainerLow)
        assertEquals(Color(0xFFD97757), colors.primary)
        assertEquals(Color(0xFF4A2E24), colors.primaryContainer)
        assertEquals(Color(0xFF3A3936), colors.outlineVariant)
        assertEquals(Color(0xFFF0735F), colors.error)
    }

    @Test
    fun everySchemeIsReadable() {
        val problems = AppColorScheme.entries.flatMap { scheme ->
            val p = scheme.dark
            listOf(
                Check("Akzent auf Hintergrund", p.accent, p.background, 4.5),
                Check("Akzent auf Karte", p.accent, p.card, 4.5),
                Check("Schrift auf gefülltem Button", p.onAccentFill, p.accentFill, 4.5),
                Check("Schrift auf Akzent-Fläche", p.onAccentContainer, p.accentContainer, 4.5),
                Check("Schrift auf Akzent", p.onAccent, p.accent, 4.5),
                Check("Text auf Hintergrund", p.onSurface, p.background, 7.0),
                Check("Text auf Karte", p.onSurface, p.card, 7.0),
                Check("Zweittext auf Karte", p.onSurfaceVariant, p.card, 4.5),
                Check("Zweittext auf Feld", p.onSurfaceVariant, p.containerHigh, 4.5),
                Check("Fortschritt auf Karte", p.positive, p.card, 4.5),
                Check("Rückschritt auf Karte", p.negative, p.card, 4.5),
                Check("Löschen auf Karte", p.error, p.card, 4.5),
                Check("Text der Snackbar", p.inverseOnSurface, p.inverseSurface, 4.5),
                Check("Akzent in der Snackbar", p.inverseAccent, p.inverseSurface, 4.5),
            ).filter { it.ratio < it.minimum }.map { "${scheme.title}: ${it.label} ${"%.2f".format(it.ratio)}:1" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun filledButtonsStandOutFromTheBackground() {
        // Bei den dunkelroten und dunkelgrünen Schemata ist die Button-Fläche bewusst dunkel (weiße Schrift darauf).
        // Erkennbar bleibt sie trotzdem: mindestens 2:1 gegen den Hintergrund, dazu der Farbton.
        AppColorScheme.entries.forEach {
            val ratio = contrast(it.dark.accentFill, it.dark.background)
            assertTrue("${it.title}: Button-Fläche nur ${"%.2f".format(ratio)}:1", ratio >= 2.0)
        }
    }

    private class Check(val label: String, foreground: Color, background: Color, val minimum: Double) {
        val ratio = contrast(foreground, background)
    }

    companion object {
        /** Relative Leuchtdichte nach WCAG: Wie hell wirkt die Farbe? 0 = Schwarz, 1 = Weiß. */
        private fun luminance(color: Color): Double {
            val argb = color.toArgb()
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                // sRGB ist "gamma-kodiert": erst zurück in lineares Licht rechnen.
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            // Grün wirkt für das Auge am hellsten, Blau am dunkelsten.
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
    }
}
