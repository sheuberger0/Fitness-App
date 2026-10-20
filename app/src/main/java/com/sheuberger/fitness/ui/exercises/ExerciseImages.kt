package com.sheuberger.fitness.ui.exercises

import androidx.annotation.DrawableRes
import com.sheuberger.fitness.R

/**
 * Bilder zu den Übungen, zugeordnet über den Namen (Groß-/Kleinschreibung egal). Eine eigene Übung mit
 * gleichem Namen bekommt so automatisch dasselbe Bild.
 *
 * Jedes Bild liegt zweimal unter gleichem Namen vor: in `drawable-nodpi` (hell, Original auf Weiß) und in
 * `drawable-night-nodpi` (dunkel, freigestellt und gedämpft). Android nimmt im dunklen Modus von selbst die
 * Datei aus dem "night"-Ordner, passend zur Systemeinstellung, nach der sich auch das Theme richtet.
 * Neue Bilder kommen über `tools/uebungsbilder/verarbeiten.py` dazu (siehe README dort).
 */
private val ExerciseImages: Map<String, Int> = mapOf(
    // Bis es ein Bild mit flacher Bank gibt (exercise_bankdruecken), zeigt Bankdrücken das Schrägbank-Bild.
    "bankdrücken" to R.drawable.exercise_schraegbankdruecken,
    "schrägbankdrücken" to R.drawable.exercise_schraegbankdruecken,
    "kurzhantel-bankdrücken" to R.drawable.exercise_kurzhantel_bankdruecken,
    "kreuzheben" to R.drawable.exercise_kreuzheben,
    "latzug" to R.drawable.exercise_latzug,
    "kabelrudern" to R.drawable.exercise_kabelrudern,
    "seitheben" to R.drawable.exercise_seitheben,
)

/** Das Bild zu einer Übung oder null, wenn es für sie (noch) keins gibt. */
@DrawableRes
fun exerciseImageFor(name: String): Int? = ExerciseImages[name.trim().lowercase()]
