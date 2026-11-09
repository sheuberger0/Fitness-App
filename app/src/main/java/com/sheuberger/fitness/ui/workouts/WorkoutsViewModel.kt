package com.sheuberger.fitness.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.Exercise
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.Workout
import com.sheuberger.fitness.data.WorkoutExercise
import com.sheuberger.fitness.rotation.Rotation
import com.sheuberger.fitness.rotation.RotationOverride
import com.sheuberger.fitness.rotation.RotationSettings
import com.sheuberger.fitness.rotation.RotationSlot
import com.sheuberger.fitness.ui.nutrition.defaultViewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ein Workout mit allem, was die Karte in der Liste anzeigt. */
data class WorkoutSummary(
    val workout: Workout,
    val exerciseNames: List<String>,
    /**
     * Wann das Workout zuletzt trainiert wurde. Für Workouts der Reihenfolge der erkannte Trainingstag (siehe
     * [Rotation]), für alle anderen das neueste Datum, an dem für eine seiner Übungen etwas eingetragen wurde.
     */
    val lastTrained: LocalDate?,
)

/** Ein Abschnitt im Streifen, der auf der Karte die ganze Reihenfolge zeigt. */
data class RotationStep(val workoutId: Long, val name: String)

/**
 * Die Karte oben in der Workout-Liste.
 *
 * [trainedToday] false: "Als Nächstes" mit dem Workout, das dran ist. true: "Heute", du hast [workout] heute
 * schon angefangen. Die Karte bleibt dann bis Mitternacht dabei, statt mitten im Training zum nächsten Workout
 * zu springen.
 */
data class NextWorkout(
    val workout: WorkoutSummary,
    val trainedToday: Boolean,
    /** Wie viele Übungen dieses Workouts heute schon eingetragen sind (nur bei [trainedToday]). */
    val doneToday: Int,
    /** Die heutigen Einträge, an denen das Training erkannt wurde ("Heute war ein anderes Workout"). */
    val todayLogIds: List<Long>,
    val steps: List<RotationStep>,
    /** Das zuletzt trainierte Workout der Reihenfolge und wann, für "Zuletzt: Lower A · vor 2 Tagen". */
    val lastWorkoutName: String?,
    val lastDate: LocalDate?,
)

data class WorkoutsUiState(
    /** Erst die Workouts der Reihenfolge in ihrer Folge, dann alle anderen, das neueste zuerst. */
    val workouts: List<WorkoutSummary> = emptyList(),
    /** null, solange keine Reihenfolge festgelegt ist. */
    val next: NextWorkout? = null,
    /** Die Workouts der Reihenfolge, in ihrer Folge. */
    val rotation: List<Workout> = emptyList(),
    /** Hinweis zum Einrichten: noch keine Reihenfolge, mindestens zwei Workouts und nicht weggeklickt. */
    val showRotationHint: Boolean = false,
    // Solange noch keine Daten da sind, zeigen wir keinen leeren Zustand an (sonst "blitzt" er kurz auf).
    val loading: Boolean = true,
)

class WorkoutsViewModel(
    private val repository: FitnessRepository,
    private val rotationSettings: RotationSettings,
    private val today: () -> LocalDate = LocalDate::now,
    scope: CoroutineScope = defaultViewModelScope(),
) : ViewModel(scope) {

    val uiState: StateFlow<WorkoutsUiState> = combine(
        repository.workouts,
        repository.workoutLinks,
        repository.exercises,
        repository.exerciseLogs,
        combine(rotationSettings.override, rotationSettings.setupHintDismissed, ::Pair),
    ) { workouts, links, exercises, logs, (override, hintDismissed) ->
        buildWorkoutsState(workouts, links, exercises, logs, override, hintDismissed, today())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutsUiState(),
    )

    /** Legt ein Workout an und meldet die neue ID zurück, damit der Screen direkt dorthin navigieren kann. */
    fun createWorkout(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createWorkout(name)) }
    }

    fun renameWorkout(workout: Workout, name: String) {
        viewModelScope.launch { repository.renameWorkout(workout, name) }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch { repository.deleteWorkout(workout) }
    }

    // --- Trainingsreihenfolge ---

    fun saveRotation(workoutIds: List<Long>) {
        viewModelScope.launch { repository.setRotation(workoutIds) }
    }

    /** Lässt das Workout aus, das gerade dran ist: Dann ist das danach dran. */
    fun skip() {
        val state = uiState.value
        val current = state.next?.takeIf { !it.trainedToday } ?: return
        val ids = state.rotation.map { it.id }
        val index = ids.indexOf(current.workout.workout.id)
        if (index >= 0) chooseNext(ids[(index + 1) % ids.size])
    }

    /** Legt von Hand fest, welches Workout als Nächstes dran ist. Das nächste Training ersetzt die Wahl wieder. */
    fun chooseNext(workoutId: Long) {
        viewModelScope.launch {
            rotationSettings.setOverride(RotationOverride(workoutId, today(), repository.latestLogId()))
        }
    }

    /** "Heute war ein anderes Workout": Die heutigen Einträge gehören ab jetzt zu [workoutId]. */
    fun setTodayWorkout(workoutId: Long) {
        val logIds = uiState.value.next?.takeIf { it.trainedToday }?.todayLogIds ?: return
        viewModelScope.launch { repository.assignLogsToWorkout(logIds, workoutId) }
    }

    fun dismissRotationHint() = rotationSettings.dismissSetupHint()
}

/** Baut aus den Daten den Zustand des Screens. Ohne Android und Datenbank, damit er sich direkt testen lässt. */
internal fun buildWorkoutsState(
    workouts: List<Workout>,
    links: List<WorkoutExercise>,
    exercises: List<Exercise>,
    logs: List<ExerciseLog>,
    override: RotationOverride?,
    hintDismissed: Boolean,
    today: LocalDate,
): WorkoutsUiState {
    // Einmal Nachschlage-Tabellen bauen, statt für jedes Workout alle Listen zu durchsuchen.
    val exerciseById = exercises.associateBy { it.id }
    val lastDateByExercise = logs.groupBy { it.exerciseId }.mapValues { (_, l) -> l.maxOf { it.date } }
    val linksByWorkout = links.groupBy { it.workoutId }.mapValues { (_, l) -> l.sortedBy { it.position } }

    val rotation = workouts
        .filter { it.rotationPosition != null }
        .sortedWith(compareBy({ it.rotationPosition }, { it.id }))
    val slots = rotation.map { workout ->
        RotationSlot(workout.id, linksByWorkout[workout.id].orEmpty().mapTo(HashSet()) { it.exerciseId })
    }
    val rotationState = Rotation.compute(slots, logs, override)

    val summaries = workouts.associate { workout ->
        val ordered = linksByWorkout[workout.id].orEmpty()
        // Für Workouts der Reihenfolge der erkannte Tag: Teilen sich Upper A und Upper B Übungen, zeigte das
        // neueste Datum der Übungen sonst bei beiden dasselbe Training an.
        val lastTrained = if (workout.rotationPosition != null) {
            rotationState?.lastTrained?.get(workout.id)
        } else {
            ordered.mapNotNull { lastDateByExercise[it.exerciseId] }.maxOrNull()
        }
        workout.id to WorkoutSummary(
            workout = workout,
            exerciseNames = ordered.mapNotNull { exerciseById[it.exerciseId]?.name },
            lastTrained = lastTrained,
        )
    }

    val next = rotationState?.let { state ->
        val todayDay = state.lastDay?.takeIf { it.date == today }
        val workoutId = todayDay?.workoutId ?: state.next
        val loggedToday = logs.filter { it.date == today }.mapTo(HashSet()) { it.exerciseId }
        NextWorkout(
            workout = summaries.getValue(workoutId),
            trainedToday = todayDay != null,
            doneToday = if (todayDay != null) linksByWorkout[workoutId].orEmpty().count { it.exerciseId in loggedToday } else 0,
            todayLogIds = todayDay?.logIds.orEmpty(),
            steps = rotation.map { RotationStep(it.id, it.name) },
            lastWorkoutName = state.lastDay?.let { summaries[it.workoutId]?.workout?.name },
            lastDate = state.lastDay?.date,
        )
    }

    return WorkoutsUiState(
        // Die übrigen behalten die Sortierung aus der Datenbank (neueste zuerst).
        workouts = (rotation + workouts.filter { it.rotationPosition == null }).map { summaries.getValue(it.id) },
        next = next,
        rotation = rotation,
        showRotationHint = rotation.isEmpty() && workouts.size >= 2 && !hintDismissed,
        loading = false,
    )
}
