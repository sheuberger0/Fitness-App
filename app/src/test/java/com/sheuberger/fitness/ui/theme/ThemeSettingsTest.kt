package com.sheuberger.fitness.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("darstellung", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun startsWithTheOriginal() {
        assertEquals(AppColorScheme.Terrakotta, ThemeSettings(context).scheme.value)
    }

    @Test
    fun choiceIsVisibleAtOnceAndSurvivesARestart() {
        val settings = ThemeSettings(context)
        settings.select(AppColorScheme.SmaragdGold)
        assertEquals(AppColorScheme.SmaragdGold, settings.scheme.value)
        // Neues Objekt = so, als wäre die App neu gestartet worden.
        assertEquals(AppColorScheme.SmaragdGold, ThemeSettings(context).scheme.value)
    }

    @Test
    fun unknownStoredValueFallsBackToTheOriginal() {
        // Zum Beispiel ein Schema, das es in einer späteren Version nicht mehr gibt.
        context.getSharedPreferences("darstellung", Context.MODE_PRIVATE).edit()
            .putString("farbschema", "neon_pink").commit()
        assertEquals(AppColorScheme.Terrakotta, ThemeSettings(context).scheme.value)
    }
}
