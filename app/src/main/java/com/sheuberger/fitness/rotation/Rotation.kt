package com.sheuberger.fitness.rotation

import com.sheuberger.fitness.data.ExerciseLog
import java.time.LocalDate

// Trainingsreihenfolge: Welches Workout ist als Nächstes dran?
//
// Die App speichert keine "Trainingseinheiten", die man starten und beenden müsste. Sie schaut sich stattdessen
// deine Gewichtseinträge an und spielt deine Trainingstage der Reihe nach durch, wie beim Durchblättern eines
// Trainingstagebuchs:
//
// 1. Für jeden Tag mit Einträgen wird bestimmt, welches Workout der Reihenfolge das war. Hast du im Workout
//    eingetragen, steht es beim Eintrag dabei ([ExerciseLog.workoutId]). Sonst gewinnt das Workout, von dessen
//    Übungen du an dem Tag die meisten eingetragen hast. Bei Gleichstand (z. B. nur Bankdrücken, das in
//    Upper A und Upper B vorkommt) gewinnt das Workout, das sowieso dran war.
// 2. Nach jedem Trainingstag ist das Workout danach dran. Nach dem letzten geht es wieder mit dem ersten los.
// 3. "Überspringen" und "Anderes Workout wählen" legen das nächste Workout von Hand fest ([RotationOverride]).
//    Das gilt, bis du danach wieder trainierst.
//
// Weil alles jedes Mal neu aus den Einträgen berechnet wird, kann nichts aus dem Takt geraten: Löschst du einen
// Eintrag oder trägst einen vergessenen Satz nach, stimmt das Ergebnis sofort wieder. Und es funktioniert auch
// mit allen Einträgen von vor App 2.3, die noch kein Workout kennen.

/** Ein Workout der Reihenfolge und die Übungen, an denen die App es erkennt. */
data class RotationSlot(val workoutId: Long, val exerciseIds: Set<Long>)

/**
 * Von Hand gewähltes nächstes Workout ("Überspringen", "Anderes Workout wählen").
 *
 * [afterLogId] ist die ID des neuesten Eintrags in dem Moment, in dem du gewählt hast. Einträge haben nur ein
 * Datum, keine Uhrzeit. Ob du an einem Tag erst gewählt und dann trainiert hast oder umgekehrt, verraten deshalb
 * die IDs: Die Datenbank vergibt sie aufsteigend, ein Satz mit größerer ID kam also nach der Wahl.
 */
data class RotationOverride(val workoutId: Long, val date: LocalDate, val afterLogId: Long)

/** Ein Tag, an dem du ein Workout der Reihenfolge trainiert hast, mit den Einträgen, an denen er erkannt wurde. */
data class TrainingDay(val date: LocalDate, val workoutId: Long, val logIds: List<Long>)

data class RotationState(
    /** Das Workout, das als Nächstes dran ist. */
    val next: Long,
    /** Der letzte erkannte Trainingstag, null ohne passende Einträge. */
    val lastDay: TrainingDay?,
    /** Wann jedes Workout der Reihenfolge zuletzt trainiert wurde (fehlt, wenn noch nie). */
    val lastTrained: Map<Long, LocalDate>,
)

object Rotation {

    /**
     * Spielt alle Trainingstage durch und sagt, welches Workout als Nächstes dran ist.
     *
     * @param slots die Workouts der Reihenfolge, in ihrer Reihenfolge. Leer: keine Reihenfolge, Ergebnis null.
     * @param logs alle Gewichtseinträge (die Reihenfolge der Liste ist egal).
     */
    fun compute(slots: List<RotationSlot>, logs: List<ExerciseLog>, override: RotationOverride?): RotationState? {
        if (slots.isEmpty()) return null
        val order = slots.map { it.workoutId }
        val position = order.withIndex().associate { (index, id) -> id to index }
        val knownExercises = slots.flatMapTo(HashSet()) { it.exerciseIds }

        // Welche Einträge zählen: im Workout der Reihenfolge eingetragen, oder ohne Workout und mit einer Übung,
        // die in der Reihenfolge vorkommt. In einem anderen Workout eingetragene Sätze gehören zu dem und zählen
        // nicht, auch wenn die Übung zufällig auch in Upper A vorkommt.
        val days = logs
            .filter { log ->
                val workout = log.workoutId
                if (workout != null) workout in position else log.exerciseId in knownExercises
            }
            .groupBy { it.date }
            .map { (date, dayLogs) -> Event.Day(date, dayLogs) }

        val events = buildList<Event> {
            addAll(days)
            // Eine Wahl von Hand für ein Workout, das nicht mehr in der Reihenfolge ist, wird ignoriert.
            if (override != null && override.workoutId in position) add(Event.Manual(override))
        }.sortedWith(compareBy<Event>({ it.date }, { it.seq }, { it.rank }))

        var next = order.first()
        var lastDay: TrainingDay? = null
        val lastTrained = HashMap<Long, LocalDate>()
        for (event in events) {
            when (event) {
                is Event.Day -> {
                    val workout = identify(event.logs, slots, position, due = next)
                    lastDay = TrainingDay(event.date, workout, event.logs.map { it.id })
                    lastTrained[workout] = event.date
                    next = order[(position.getValue(workout) + 1) % order.size]
                }
                is Event.Manual -> next = event.override.workoutId
            }
        }
        return RotationState(next = next, lastDay = lastDay, lastTrained = lastTrained)
    }

    /** Welches Workout der Reihenfolge war dieser Tag? [due] war an dem Tag dran. */
    private fun identify(
        dayLogs: List<ExerciseLog>,
        slots: List<RotationSlot>,
        position: Map<Long, Int>,
        due: Long,
    ): Long {
        // 1. Im Workout eingetragen: Das Workout steht beim Eintrag. Gezählt werden Übungen, nicht Sätze.
        val explicit = dayLogs
            .mapNotNull { log -> log.workoutId?.let { it to log.exerciseId } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, exercises) -> exercises.toSet().size }
        // 2. Sonst an den Übungen erkennen: Welches Workout enthält die meisten der eingetragenen Übungen?
        val scores = explicit.ifEmpty {
            val logged = dayLogs.mapTo(HashSet()) { it.exerciseId }
            slots.associate { slot -> slot.workoutId to slot.exerciseIds.count { it in logged } }
                .filterValues { it > 0 }
        }
        // 3. Gleichstand: das Workout, das dran war, sonst das, das als Erstes danach käme.
        val best = scores.values.max()
        val size = position.size
        val duePosition = position.getValue(due)
        return scores.filterValues { it == best }.keys
            .minBy { (position.getValue(it) - duePosition + size) % size }
    }

    /** Ein Trainingstag oder eine Wahl von Hand, in der Reihenfolge, in der sie passiert sind. */
    private sealed interface Event {
        val date: LocalDate

        /** Innerhalb eines Tages: die ID des neuesten Eintrags davor. */
        val seq: Long

        /** Bei Gleichstand zuerst der Trainingstag, dann die Wahl von Hand. */
        val rank: Int

        class Day(override val date: LocalDate, val logs: List<ExerciseLog>) : Event {
            override val seq: Long = logs.maxOf { it.id }
            override val rank = 0
        }

        class Manual(val override: RotationOverride) : Event {
            override val date: LocalDate = override.date
            override val seq: Long = override.afterLogId
            override val rank = 1
        }
    }
}
