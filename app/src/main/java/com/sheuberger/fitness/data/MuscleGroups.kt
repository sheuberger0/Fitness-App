package com.sheuberger.fitness.data

/** Muskelgruppen, nach denen die Übungsbibliothek gruppiert wird. */
val MuscleGroups = listOf(
    "Brust",
    "Rücken",
    "Schultern",
    "Beine",
    "Bizeps",
    "Trizeps",
    "Bauch",
    "Ganzkörper",
    "Sonstige",
)

internal val DefaultExercises = listOf(
    "Bankdrücken" to "Brust",
    "Schrägbankdrücken" to "Brust",
    "Kurzhantel-Bankdrücken" to "Brust",
    "Butterfly (Maschine)" to "Brust",
    "Kreuzheben" to "Rücken",
    "Klimmzüge" to "Rücken",
    "Latzug" to "Rücken",
    "Langhantelrudern" to "Rücken",
    "Kabelrudern" to "Rücken",
    "Schulterdrücken" to "Schultern",
    "Seitheben" to "Schultern",
    "Face Pulls" to "Schultern",
    "Kniebeugen" to "Beine",
    "Beinpresse" to "Beine",
    "Rumänisches Kreuzheben" to "Beine",
    "Ausfallschritte" to "Beine",
    "Beinstrecker" to "Beine",
    "Beinbeuger" to "Beine",
    "Hip Thrust" to "Beine",
    "Wadenheben" to "Beine",
    "Bizepscurls" to "Bizeps",
    "Hammercurls" to "Bizeps",
    "Trizepsdrücken (Kabel)" to "Trizeps",
    "French Press" to "Trizeps",
    "Dips" to "Trizeps",
    "Kabel-Crunches" to "Bauch",
)
