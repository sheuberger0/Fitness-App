package com.sheuberger.fitness

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sheuberger.fitness.ui.FitnessAppRoot
import com.sheuberger.fitness.ui.theme.AppColorScheme
import com.sheuberger.fitness.ui.theme.FitnessTheme
import com.sheuberger.fitness.ui.theme.LightBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeSettings = (application as FitnessApplication).themeSettings
        // Schon vor dem ersten Bild: so blitzt beim Start nichts hell auf, wenn ein dunkles Schema gewählt ist.
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        applyWindowColors(themeSettings.scheme.value, systemNight)
        setContent {
            val scheme by themeSettings.scheme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(scheme, systemDark) { applyWindowColors(scheme, systemDark) }
            FitnessTheme(scheme = scheme, systemDark = systemDark) {
                FitnessAppRoot(colorScheme = scheme, onColorSchemeChange = themeSettings::select)
            }
        }
    }

    /**
     * Statusleiste, Navigationsleiste, Fensterhintergrund und Startbildschirm passend zum Farbschema.
     *
     * Nötig, weil fast alle Schemata immer dunkel sind, auch wenn das Handy hell eingestellt ist. Ohne das
     * wären die Symbole der Statusleiste (Uhrzeit, Akku) dunkel auf dunklem Grund und damit unsichtbar.
     */
    private fun applyWindowColors(scheme: AppColorScheme, systemDark: Boolean) {
        val dark = scheme.isDark(systemDark)
        if (dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(DarkNavigationScrim),
            )
        } else {
            enableEdgeToEdge()
        }
        val background = if (dark) scheme.dark.background else LightBackground
        window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
        // Ab Android 13 merkt sich das System, in welcher Farbe der Startbildschirm beim nächsten Öffnen erscheint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            splashScreen.setSplashScreenTheme(if (scheme.followsSystem) 0 else R.style.Theme_Fitness_Dunkel)
        }
    }

    private companion object {
        // Gleiche Abdunkelung hinter der 3-Tasten-Navigation wie bei enableEdgeToEdge() im Dark Mode.
        val DarkNavigationScrim = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
