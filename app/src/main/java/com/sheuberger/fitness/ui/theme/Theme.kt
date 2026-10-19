package com.sheuberger.fitness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Zuordnung der Palette zu den Material-3-Rollen. So können alle Screens einfach
// MaterialTheme.colorScheme.xyz verwenden und bekommen automatisch die richtige Farbe für Hell/Dunkel:
//   background/surface        = Seitenhintergrund (warmes Papier bzw. warmes Fast-Schwarz)
//   surfaceContainerLow       = Karten, Sheets, Dialoge
//   surfaceContainer/High/Highest = Flächen in Karten (Felder, Chips, Tonal-Buttons), von hell nach kräftig
//   primary / primaryContainer = Akzent "Clay" bzw. seine zarte Variante (Auswahl, Indikator)
//   outlineVariant            = feine Trennlinien ("Hairlines")
private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkOnAccentContainer,
    inversePrimary = AccentLight,
    secondary = AccentDark,
    onSecondary = Color.White,
    secondaryContainer = DarkAccentContainer,
    onSecondaryContainer = DarkOnAccentContainer,
    tertiary = AccentDark,
    onTertiary = Color.White,
    tertiaryContainer = DarkAccentContainer,
    onTertiaryContainer = DarkOnAccentContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    // Keine Akzent-Tönung bei erhöhten Flächen: Material würde sonst alles leicht orange färben.
    surfaceTint = Color.Transparent,
    inverseSurface = LightContainerHigh,
    inverseOnSurface = LightOnSurface,
    surfaceDim = Color(0xFF1A1918),
    surfaceBright = DarkContainerHighest,
    surfaceContainerLowest = Color(0xFF1A1918),
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDark,
    onError = Color.White,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = LightOnAccentContainer,
    inversePrimary = AccentDark,
    secondary = AccentLight,
    onSecondary = Color.White,
    secondaryContainer = LightAccentContainer,
    onSecondaryContainer = LightOnAccentContainer,
    tertiary = AccentLight,
    onTertiary = Color.White,
    tertiaryContainer = LightAccentContainer,
    onTertiaryContainer = LightOnAccentContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkContainerHigh,
    inverseOnSurface = DarkOnSurface,
    surfaceDim = LightContainerHighest,
    surfaceBright = LightCard,
    surfaceContainerLowest = LightCard,
    surfaceContainerLow = LightCard,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    scrim = Color.Black,
)

/** Zusätzliche Farben, die Material 3 nicht kennt: Fortschritt (grün) und Rückschritt (rot). */
@Immutable
data class TrendColors(val positive: Color, val negative: Color)

val LocalTrendColors = staticCompositionLocalOf { TrendColors(PositiveDark, NegativeDark) }

/**
 * true im dunklen Theme. Praktisch für kleine Unterschiede, z. B. eine feine Kartenkante,
 * die nur im hellen Theme nötig ist.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun FitnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Absichtlich keine "Dynamic Colors" (Farben vom Hintergrundbild),
    // damit die App überall gleich und markant aussieht.
    val trend = if (darkTheme) TrendColors(PositiveDark, NegativeDark) else TrendColors(PositiveLight, NegativeLight)
    CompositionLocalProvider(
        LocalTrendColors provides trend,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
