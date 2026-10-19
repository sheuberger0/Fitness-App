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
    // Auf dem hellen Dunkel-Akzent ist dunkle Schrift lesbar (5,3:1), weiße nicht (3,1:1). So sind alle
    // Material-Bausteine mit primary/onPrimary (z. B. der DatePicker) automatisch gut lesbar.
    // Unsere Buttons mit weißer Schrift nutzen dafür die tiefere Füllung aus [LocalAccentFill].
    onPrimary = DarkBackground,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkOnAccentContainer,
    inversePrimary = AccentInverseForDark,
    secondary = AccentDark,
    onSecondary = DarkBackground,
    secondaryContainer = DarkAccentContainer,
    onSecondaryContainer = DarkOnAccentContainer,
    tertiary = AccentDark,
    onTertiary = DarkBackground,
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
    onError = DarkBackground,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = LightOnAccentContainer,
    inversePrimary = AccentInverseForLight,
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
 * Farben für gefüllte Akzent-Flächen mit weißer Schrift (Primär-Button, FAB, "+"-Kreis).
 * Getrennt von `colorScheme.primary`, weil eine einzige Farbe im dunklen Theme nicht beides kann:
 * als Text auf Fast-Schwarz lesbar sein UND weiße Schrift auf sich tragen.
 */
@Immutable
data class AccentFillColors(val container: Color, val content: Color)

val LocalAccentFill = staticCompositionLocalOf { AccentFillColors(AccentFill, Color.White) }

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
        LocalAccentFill provides AccentFillColors(AccentFill, Color.White),
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
