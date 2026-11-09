package com.sheuberger.fitness.rotation

import com.sheuberger.fitness.data.ExerciseLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RotationTest {

    // Simons Plan: Upper A, Lower A, Upper B, Lower B. Bankdrücken steckt in beiden Upper-Workouts,
    // Kniebeugen in beiden Lower-Workouts.
    private val upperA = 1L
    private val lowerA = 2L
    private val upperB = 3L
    private val lowerB = 4L
    private val bauch = 9L // ein Workout außerhalb der Reihenfolge

    private val bankdruecken = 10L
    private val rudern = 11L
    private val schulterdruecken = 12L
    private val klimmzuege = 13L
    private val seitheben = 14L
    private val kniebeugen = 20L
    private val beinstrecker = 21L
    private val kreuzheben = 22L
    private val crunches = 30L // in keinem Workout der Reihenfolge

    private val slots = listOf(
        RotationSlot(upperA, setOf(bankdruecken, rudern, schulterdruecken)),
        RotationSlot(lowerA, setOf(kniebeugen, beinstrecker)),
        RotationSlot(upperB, setOf(bankdruecken, klimmzuege, seitheben)),
        RotationSlot(lowerB, setOf(kniebeugen, kreuzheben)),
    )

    private var nextId = 1L

    /** Ein Satz am [day]. November 2026. Die IDs steigen wie in der Datenbank mit jedem Aufruf. */
    private fun log(exercise: Long, day: Int, workout: Long? = null) =
        ExerciseLog(id = nextId++, exerciseId = exercise, date = date(day), weightKg = 50.0, workoutId = workout)

    private fun date(day: Int): LocalDate = LocalDate.of(2026, 11, day)

    private fun next(logs: List<ExerciseLog>, override: RotationOverride? = null): Long =
        Rotation.compute(slots, logs, override)!!.next

    @Test
    fun ohneReihenfolge_gibtEsKeinErgebnis() {
        assertNull(Rotation.compute(emptyList(), listOf(log(bankdruecken, 2)), null))
    }

    @Test
    fun ohneEintraege_istDasErsteDran() {
        val state = Rotation.compute(slots, emptyList(), null)!!
        assertEquals(upperA, state.next)
        assertNull(state.lastDay)
    }

    @Test
    fun nachJedemTraining_kommtDasNaechste_undNachDemLetztenWiederDasErste() {
        val logs = mutableListOf(log(rudern, 2, upperA))
        assertEquals(lowerA, next(logs))
        logs += log(beinstrecker, 4, lowerA)
        assertEquals(upperB, next(logs))
        logs += log(klimmzuege, 6, upperB)
        assertEquals(lowerB, next(logs))
        logs += log(kreuzheben, 9, lowerB)
        assertEquals(upperA, next(logs))
    }

    @Test
    fun alteEintraegeOhneWorkout_werdenAnDenUebungenErkannt() {
        // So sehen alle Einträge von vor App 2.3 aus: kein Workout dabei.
        val logs = listOf(
            log(bankdruecken, 2), log(rudern, 2), log(schulterdruecken, 2), // Upper A
            log(kniebeugen, 4), log(beinstrecker, 4), // Lower A
            log(bankdruecken, 6), log(klimmzuege, 6), log(seitheben, 6), // Upper B
        )
        val state = Rotation.compute(slots, logs, null)!!
        assertEquals(lowerB, state.next)
        assertEquals(TrainingDay(date(6), upperB, listOf(6L, 7L, 8L)), state.lastDay)
    }

    @Test
    fun anDerZahlDerUebungen_nichtAnDerReihenfolge() {
        // Dran war Lower A, trainiert wurde Lower B (Kniebeugen + Kreuzheben): Lower B gewinnt 2 zu 1.
        val logs = listOf(log(rudern, 2), log(kniebeugen, 4), log(kreuzheben, 4))
        assertEquals(upperA, next(logs))
    }

    @Test
    fun gleichstand_gewinntDasWorkoutDasDranWar() {
        // Nur Bankdrücken: passt zu Upper A und Upper B. Dran war Upper B, also war es Upper B.
        val logs = listOf(log(rudern, 2), log(beinstrecker, 4), log(bankdruecken, 6))
        assertEquals(lowerB, next(logs))
        // Ganz am Anfang ist Upper A dran.
        assertEquals(lowerA, next(listOf(log(bankdruecken, 2))))
    }

    @Test
    fun gleichstand_ohneDasFaellige_gewinntDasNaechsteDanach() {
        // Dran war Lower A. Bankdrücken passt zu Upper A und Upper B; Upper B kommt nach Lower A zuerst.
        val logs = listOf(log(rudern, 2), log(bankdruecken, 4))
        assertEquals(lowerB, next(logs))
    }

    @Test
    fun imWorkoutEingetragen_gehtVorDerErkennungAnDenUebungen() {
        // Zwei Übungen sähen nach Upper A aus, aber das Bankdrücken wurde in Upper B eingetragen.
        val logs = listOf(log(rudern, 2), log(schulterdruecken, 2), log(bankdruecken, 2, upperB))
        assertEquals(lowerB, next(logs))
    }

    @Test
    fun imWorkoutEingetragen_zaehlenUebungen_nichtSaetze() {
        // Drei Sätze Bankdrücken in Upper A, aber zwei verschiedene Übungen in Upper B.
        val logs = listOf(
            log(bankdruecken, 2, upperA), log(bankdruecken, 2, upperA), log(bankdruecken, 2, upperA),
            log(klimmzuege, 2, upperB), log(seitheben, 2, upperB),
        )
        assertEquals(lowerB, next(logs))
    }

    @Test
    fun andereWorkoutsUndUebungen_zaehlenNicht() {
        val logs = listOf(
            log(rudern, 2, upperA),
            // Kniebeugen im Bauch-Workout eingetragen: gehört zu dem, nicht zu Lower A oder Lower B.
            log(kniebeugen, 4, bauch),
            // Eine Übung, die in keinem Workout der Reihenfolge vorkommt.
            log(crunches, 5),
        )
        val state = Rotation.compute(slots, logs, null)!!
        assertEquals(lowerA, state.next)
        assertEquals(date(2), state.lastDay?.date)
    }

    @Test
    fun ausDerReihenfolgeGenommen_zaehltNichtMehr() {
        val withoutLowerB = slots.filter { it.workoutId != lowerB }
        val logs = listOf(log(bankdruecken, 2, upperB), log(kniebeugen, 4, lowerB), log(kreuzheben, 4, lowerB))
        // Ohne Lower B ist nach Upper B wieder Upper A dran; der Lower-B-Tag zählt nicht mehr.
        assertEquals(upperA, Rotation.compute(withoutLowerB, logs, null)!!.next)
    }

    @Test
    fun zuletztTrainiert_proWorkout() {
        val logs = listOf(
            log(rudern, 2), log(beinstrecker, 4), log(klimmzuege, 6), log(kreuzheben, 9), log(rudern, 11),
        )
        val state = Rotation.compute(slots, logs, null)!!
        assertEquals(
            mapOf(upperA to date(11), lowerA to date(4), upperB to date(6), lowerB to date(9)),
            state.lastTrained,
        )
    }

    // --- Von Hand gewählt ---

    @Test
    fun ueberspringen_giltBisZumNaechstenTraining() {
        val logs = mutableListOf(log(rudern, 2, upperA))
        // Lower A war dran, am 3. übersprungen: Upper B ist dran.
        val skip = RotationOverride(upperB, date(3), afterLogId = 1)
        assertEquals(upperB, next(logs, skip))
        // Am 5. Upper B trainiert: Danach geht es normal weiter.
        logs += log(klimmzuege, 5, upperB)
        assertEquals(lowerB, next(logs, skip))
    }

    @Test
    fun trainingAmSelbenTagNachDerWahl_gehtVor() {
        val logs = mutableListOf(log(rudern, 2, upperA))
        // Morgens zu Hause übersprungen, abends im Gym Upper B trainiert (neuere ID als die Wahl).
        val skip = RotationOverride(upperB, date(3), afterLogId = 1)
        logs += log(klimmzuege, 3, upperB)
        val state = Rotation.compute(slots, logs, skip)!!
        assertEquals(lowerB, state.next)
        assertEquals(TrainingDay(date(3), upperB, listOf(2L)), state.lastDay)
    }

    @Test
    fun wahlNachDemTrainingAmSelbenTag_bleibt() {
        val logs = listOf(log(rudern, 3, upperA))
        // Nach dem Training gewählt: Die Wahl ist neuer als alle Sätze des Tages.
        val choice = RotationOverride(lowerB, date(3), afterLogId = 1)
        val state = Rotation.compute(slots, logs, choice)!!
        assertEquals(lowerB, state.next)
        assertEquals(upperA, state.lastDay?.workoutId)
    }

    @Test
    fun nachgetragenerSatzFuerEinenFruherenTag_ersetztDieWahlNicht() {
        val logs = mutableListOf(log(rudern, 2, upperA))
        val skip = RotationOverride(upperB, date(4), afterLogId = 1)
        // Am 4. für den 1. November einen vergessenen Satz nachgetragen: neuere ID, aber älteres Datum.
        logs += log(beinstrecker, 1, lowerA)
        assertEquals(upperB, next(logs, skip))
    }

    @Test
    fun wahlFuerEinWorkoutAusserhalbDerReihenfolge_wirdIgnoriert() {
        val logs = listOf(log(rudern, 2, upperA))
        assertEquals(lowerA, next(logs, RotationOverride(bauch, date(3), afterLogId = 1)))
    }
}
