package com.sheuberger.fitness.ui.theme

import androidx.compose.ui.graphics.Color

// Warme, ruhige Palette nach dem Vorbild der Claude-App: helle Papier-Töne statt kaltem Grau,
// dazu genau eine Akzentfarbe (Ton/Terrakotta). Farbe wird nur dort eingesetzt, wo sie etwas bedeutet.

// Akzent ("Clay"). Im dunklen Theme etwas heller, damit er auf dem dunklen Grund gleich kräftig wirkt.
val AccentLight = Color(0xFFC96442)
val AccentDark = Color(0xFFD97757)
val LightAccentContainer = Color(0xFFF5E1D8)
val LightOnAccentContainer = Color(0xFF6E2E18)
val DarkAccentContainer = Color(0xFF4A2E24)
val DarkOnAccentContainer = Color(0xFFF5D5C7)

// Fortschritt/Rückschritt: etwas weicher und wärmer als reines Grün/Rot, damit sie zur Palette passen.
// Das Rot ist bewusst röter als der Akzent, damit man "schlechter" nicht mit "ausgewählt" verwechselt.
val PositiveLight = Color(0xFF2F8F5B)
val PositiveDark = Color(0xFF5CC48A)
val NegativeLight = Color(0xFFC23B32)
val NegativeDark = Color(0xFFF0735F)

// Fehler-/Lösch-Farbe für Buttons (im Dunklen etwas satter, damit weiße Schrift lesbar bleibt).
val ErrorLight = Color(0xFFC23B32)
val ErrorDark = Color(0xFFE0574B)

// Helles Theme
val LightBackground = Color(0xFFFAF9F5)
val LightCard = Color(0xFFFFFFFF)
val LightContainer = Color(0xFFF5F3EC)
val LightContainerHigh = Color(0xFFF0EEE6)
val LightContainerHighest = Color(0xFFE8E6DC)
val LightOnSurface = Color(0xFF1F1E1D)
val LightOnSurfaceVariant = Color(0xFF73726C)
val LightOutline = Color(0xFFCFCCC0)
val LightOutlineVariant = Color(0xFFE5E3DA)
val LightErrorContainer = Color(0xFFF9DEDA)
val LightOnErrorContainer = Color(0xFF5F150F)

// Dunkles Theme
val DarkBackground = Color(0xFF1F1E1D)
val DarkCard = Color(0xFF262624)
val DarkContainer = Color(0xFF2B2A28)
val DarkContainerHigh = Color(0xFF30302E)
val DarkContainerHighest = Color(0xFF3A3936)
val DarkOnSurface = Color(0xFFF5F4EE)
val DarkOnSurfaceVariant = Color(0xFFA3A29B)
val DarkOutline = Color(0xFF57564F)
val DarkOutlineVariant = Color(0xFF3A3936)
val DarkErrorContainer = Color(0xFF4D1F1A)
val DarkOnErrorContainer = Color(0xFFF9D2CC)
