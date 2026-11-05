package com.sheuberger.fitness.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

// Zuordnung der Palette zu den Material-3-Rollen. So können alle Screens einfach
// MaterialTheme.colorScheme.xyz verwenden und bekommen automatisch die richtige Farbe für Hell/Dunkel:
//   background/surface        = Seitenhintergrund (warmes Papier bzw. dunkler Grund des Farbschemas)
//   surfaceContainerLow       = Karten, Sheets, Dialoge
//   surfaceContainer/High/Highest = Flächen in Karten (Felder, Chips, Tonal-Buttons), von dezent bis kräftig
//   primary / primaryContainer = Akzent bzw. seine zarte Variante (Auswahl, Indikator)
//   outlineVariant            = feine Trennlinien ("Hairlines")

/** Baut aus einer dunklen Palette (siehe ColorSchemes.kt) das Material-Farbschema. */
internal fun DarkPalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    // Material-Bausteine mit primary/onPrimary (z. B. der DatePicker) bekommen so lesbare Schrift auf dem Akzent.
    // Unsere Buttons nutzen dafür die eigene Füllung aus [LocalAccentFill].
    onPrimary = onAccent,
    primaryContainer = accentContainer,
    onPrimaryContainer = onAccentContainer,
    inversePrimary = inverseAccent,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = accentContainer,
    onSecondaryContainer = onAccentContainer,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = accentContainer,
    onTertiaryContainer = onAccentContainer,
    background = background,
    onBackground = onSurface,
    surface = background,
    onSurface = onSurface,
    surfaceVariant = containerHigh,
    onSurfaceVariant = onSurfaceVariant,
    // Keine Akzent-Tönung bei erhöhten Flächen: Material würde sonst alles leicht einfärben.
    surfaceTint = Color.Transparent,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    surfaceDim = dim,
    surfaceBright = containerHighest,
    surfaceContainerLowest = dim,
    surfaceContainerLow = card,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = containerHighest,
    outline = outline,
    outlineVariant = containerHighest,
    error = error,
    onError = background,
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

/** Zusätzliche Farben, die Material 3 nicht kennt: Fortschritt (grün) und Rückschritt (rot bzw. bernstein). */
@Immutable
data class TrendColors(val positive: Color, val negative: Color)

val LocalTrendColors = staticCompositionLocalOf { TrendColors(PositiveDark, NegativeDark) }

/**
 * Farben für gefüllte Akzent-Flächen (Primär-Button, FAB, "+"-Kreis) und die Schrift darauf.
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

/**
 * Das Theme der App.
 *
 * [scheme] ist das in den Einstellungen gewählte Farbschema. Das Original (Terrakotta) ist hell oder dunkel wie das
 * Handy ([systemDark]), alle anderen Schemata sind immer dunkel. Beim Wechsel gleiten alle Farben in 0,3 Sekunden
 * zum neuen Schema, statt hart umzuspringen.
 */
@Composable
fun FitnessTheme(
    scheme: AppColorScheme = AppColorScheme.Default,
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Absichtlich keine "Dynamic Colors" (Farben vom Hintergrundbild),
    // damit die App überall gleich und markant aussieht.
    val darkTheme = scheme.isDark(systemDark)
    val palette = animatedPalette(scheme.dark)
    // remember: Ohne Änderung bleibt es dasselbe Objekt, so wird die App nicht unnötig neu aufgebaut.
    val colors = remember(darkTheme, palette) { if (darkTheme) palette.toColorScheme() else LightColors }
    val trend = if (darkTheme) TrendColors(palette.positive, palette.negative) else TrendColors(PositiveLight, NegativeLight)
    val fill = if (darkTheme) AccentFillColors(palette.accentFill, palette.onAccentFill) else AccentFillColors(AccentFill, Color.White)
    CompositionLocalProvider(
        LocalTrendColors provides trend,
        LocalAccentFill provides fill,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}

private val PaletteSpec = tween<Color>(durationMillis = 300, easing = FastOutSlowInEasing)

@Composable
private fun Color.animated(): Color = animateColorAsState(this, PaletteSpec, label = "Farbschema").value

/** Jede Farbe der Palette läuft für sich zum Ziel; ein Wechsel mitten in der Bewegung setzt dort an, wo sie gerade ist. */
@Composable
private fun animatedPalette(target: DarkPalette) = DarkPalette(
    background = target.background.animated(),
    dim = target.dim.animated(),
    card = target.card.animated(),
    container = target.container.animated(),
    containerHigh = target.containerHigh.animated(),
    containerHighest = target.containerHighest.animated(),
    outline = target.outline.animated(),
    onSurface = target.onSurface.animated(),
    onSurfaceVariant = target.onSurfaceVariant.animated(),
    inverseSurface = target.inverseSurface.animated(),
    inverseOnSurface = target.inverseOnSurface.animated(),
    accent = target.accent.animated(),
    onAccent = target.onAccent.animated(),
    accentFill = target.accentFill.animated(),
    onAccentFill = target.onAccentFill.animated(),
    accentContainer = target.accentContainer.animated(),
    onAccentContainer = target.onAccentContainer.animated(),
    inverseAccent = target.inverseAccent.animated(),
    positive = target.positive.animated(),
    negative = target.negative.animated(),
    error = target.error.animated(),
)

/**
 * Ressourcen (z. B. Bilder) passend zum Theme der App statt zum Handy.
 *
 * Android wählt Dateien aus den "night"-Ordnern nur, wenn das HANDY dunkel ist. Ist das Handy hell, die App aber
 * dunkel (alle Farbschemata außer Terrakotta), liefert diese Funktion trotzdem die dunklen Varianten. Dafür baut
 * sie eine Kopie der Konfiguration, in der der Nachtmodus passend zur App gesetzt ist.
 */
@Composable
fun themedResources(): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = LocalDarkTheme.current
    return remember(context, configuration, dark) {
        val systemNight = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (dark == systemNight) {
            context.resources
        } else {
            val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val themed = Configuration(configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            context.createConfigurationContext(themed).resources
        }
    }
}
