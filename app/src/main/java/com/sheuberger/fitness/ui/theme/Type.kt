package com.sheuberger.fitness.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

// "tnum" = tabellarische Ziffern: Alle Ziffern sind gleich breit,
// so springen Zahlen beim Wechseln nicht hin und her.
private const val TabularNumbers = "tnum"

val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TabularNumbers,
    ),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
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
    letterSpacing = 1.sp,
)
