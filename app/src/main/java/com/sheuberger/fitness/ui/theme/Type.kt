package com.sheuberger.fitness.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

// "tnum" = tabellarische Ziffern: Alle Ziffern sind gleich breit,
// so springen Zahlen beim Wechseln nicht hin und her.
private const val TabularNumbers = "tnum"

// Überschriften in einer Serifenschrift wie in der Claude-App: wirkt ruhig und hochwertig.
// FontFamily.Serif ist auf jedem Android-Gerät vorhanden (Noto Serif), braucht also keine eigene Schriftdatei.
// Fließtext, Beschriftungen und große Zahlen bleiben serifenlos, weil sie so besser lesbar sind.
private val Headline = FontFamily.Serif

val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TabularNumbers,
    ),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers),
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers),
    // Bildschirm-Titel (z. B. "Workouts") und Titel von Sheets/Dialogen: Serif, normales Gewicht.
    headlineLarge = base.headlineLarge.copy(
        fontFamily = Headline,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = Headline,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNumbers),
    titleLarge = base.titleLarge.copy(fontFamily = Headline, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFeatureSettings = TabularNumbers),
    bodyMedium = base.bodyMedium.copy(fontFeatureSettings = TabularNumbers),
    bodySmall = base.bodySmall.copy(fontFeatureSettings = TabularNumbers),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFeatureSettings = TabularNumbers),
    labelSmall = base.labelSmall.copy(fontFeatureSettings = TabularNumbers),
)

/** Stil für kleine Abschnittsüberschriften wie "VERLAUF". */
val SectionLabelStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.8.sp,
)
