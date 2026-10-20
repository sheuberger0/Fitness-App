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
    // Brust
    "bankdrücken" to R.drawable.exercise_bankdruecken,
    "schrägbankdrücken" to R.drawable.exercise_schraegbankdruecken,
    "kurzhantel-bankdrücken" to R.drawable.exercise_kurzhantel_bankdruecken,
    "butterfly (maschine)" to R.drawable.exercise_butterfly,
    "brustpresse" to R.drawable.exercise_brustpresse,
    "kabel-crossover" to R.drawable.exercise_kabel_crossover,
    "bankdrücken (multipresse)" to R.drawable.exercise_bankdruecken_multipresse,
    // Rücken
    "kreuzheben" to R.drawable.exercise_kreuzheben,
    "klimmzüge" to R.drawable.exercise_klimmzuege,
    "klimmzüge (assistiert)" to R.drawable.exercise_klimmzuege_assistiert,
    "latzug" to R.drawable.exercise_latzug,
    "langhantelrudern" to R.drawable.exercise_langhantelrudern,
    "kabelrudern" to R.drawable.exercise_kabelrudern,
    "rudern (maschine)" to R.drawable.exercise_rudern_maschine,
    "t-bar-rudern" to R.drawable.exercise_t_bar_rudern,
    "überzüge (kabel)" to R.drawable.exercise_ueberzuege_kabel,
    "rückenstrecker" to R.drawable.exercise_rueckenstrecker,
    // Schultern
    "schulterdrücken" to R.drawable.exercise_schulterdruecken,
    "schulterpresse" to R.drawable.exercise_schulterpresse,
    "seitheben" to R.drawable.exercise_seitheben,
    "seitheben (maschine)" to R.drawable.exercise_seitheben_maschine,
    "reverse butterfly" to R.drawable.exercise_reverse_butterfly,
    "face pulls" to R.drawable.exercise_face_pulls,
    "shrugs" to R.drawable.exercise_shrugs,
    // Beine
    "kniebeugen" to R.drawable.exercise_kniebeugen,
    "kniebeugen (multipresse)" to R.drawable.exercise_kniebeugen_multipresse,
    "hackenschmidt" to R.drawable.exercise_hackenschmidt,
    "ausfallschritte" to R.drawable.exercise_ausfallschritte,
    "rumänisches kreuzheben" to R.drawable.exercise_rumaenisches_kreuzheben,
    "beinstrecker" to R.drawable.exercise_beinstrecker,
    "beinbeuger" to R.drawable.exercise_beinbeuger,
    "beinbeuger sitzend" to R.drawable.exercise_beinbeuger_sitzend,
    "hip thrust" to R.drawable.exercise_hip_thrust,
    "adduktoren (maschine)" to R.drawable.exercise_adduktoren_maschine,
    "abduktoren (maschine)" to R.drawable.exercise_abduktoren_maschine,
    "kickbacks (kabel)" to R.drawable.exercise_kickbacks_kabel,
    "wadenheben" to R.drawable.exercise_wadenheben,
    "wadenheben sitzend" to R.drawable.exercise_wadenheben_sitzend,
    // Bizeps
    "bizepscurls" to R.drawable.exercise_bizepscurls,
    "hammercurls" to R.drawable.exercise_hammercurls,
    "scottcurls (maschine)" to R.drawable.exercise_scottcurls_maschine,
    "bizepscurls (kabel)" to R.drawable.exercise_bizepscurls_kabel,
    // Trizeps
    "trizepsdrücken (kabel)" to R.drawable.exercise_trizepsdruecken_kabel,
    "trizeps über kopf (kabel)" to R.drawable.exercise_trizeps_ueber_kopf_kabel,
    "french press" to R.drawable.exercise_french_press,
    "dips" to R.drawable.exercise_dips,
    "dips (maschine)" to R.drawable.exercise_dips_maschine,
    // Bauch
    "kabel-crunches" to R.drawable.exercise_kabel_crunches,
    "bauchmaschine" to R.drawable.exercise_bauchmaschine,
    "beinheben hängend" to R.drawable.exercise_beinheben_haengend,
)

/** Das Bild zu einer Übung oder null, wenn es für sie (noch) keins gibt. */
@DrawableRes
fun exerciseImageFor(name: String): Int? = ExerciseImages[name.trim().lowercase()]
