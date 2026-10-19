package com.sheuberger.fitness.ui.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.Exercise
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.Workout
import com.sheuberger.fitness.ui.components.dailyBest
import com.sheuberger.fitness.ui.components.latest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Eine Übung im Workout mit ihrem letzten Eintrag und dem Mini-Verlauf. */
data class WorkoutExerciseRow(
    val exercise: Exercise,
    val latest: ExerciseLog?,
    /** Bestwerte der letzten Trainingstage, älteste zuerst (für die Sparkline). */
    val recentBests: List<Double>,
)

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val exercises: List<WorkoutExerciseRow> = emptyList(),
    /** Die ganze Übungsbibliothek, für die Auswahl beim Hinzufügen. */
    val library: List<Exercise> = emptyList(),
    val loading: Boolean = true,
    /** true, sobald das Workout nicht mehr existiert (z. B. gerade gelöscht). */
    val deleted: Boolean = false,
)

class WorkoutDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FitnessRepository,
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle.get<Long>("workoutId"))

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        repository.workout(workoutId),
        repository.workoutExercises(workoutId),
        repository.exercises,
        repository.exerciseLogs,
    ) { workout, exercises, library, logs ->
        val logsByExercise = logs.groupBy { it.exerciseId }
        WorkoutDetailUiState(
            workout = workout,
            exercises = exercises.map { exercise ->
                val exerciseLogs = logsByExercise[exercise.id].orEmpty()
                WorkoutExerciseRow(
                    exercise = exercise,
                    latest = exerciseLogs.latest(),
                    recentBests = exerciseLogs.dailyBest().takeLast(SPARKLINE_POINTS).map { it.value },
                )
            },
            library = library,
            loading = false,
            deleted = workout == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutDetailUiState(),
    )

    fun rename(name: String) {
        val workout = uiState.value.workout ?: return
        viewModelScope.launch { repository.renameWorkout(workout, name) }
    }

    /** Nach dem Löschen meldet der Flow `deleted = true` und der Screen navigiert zurück. */
    fun delete() {
        val workout = uiState.value.workout ?: return
        viewModelScope.launch { repository.deleteWorkout(workout) }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch { repository.addExerciseToWorkout(workoutId, exerciseId) }
    }

    /** Legt eine neue Übung an (oder nimmt die gleichnamige vorhandene) und fügt sie hinzu. */
    fun createAndAddExercise(name: String) {
        viewModelScope.launch {
            val id = repository.createExercise(name, null)
            // Doppelte Verknüpfungen ignoriert die Datenbank ohnehin (OnConflictStrategy.IGNORE).
            repository.addExerciseToWorkout(workoutId, id)
        }
    }

    fun removeExercise(exerciseId: Long) {
        viewModelScope.launch { repository.removeExerciseFromWorkout(workoutId, exerciseId) }
    }

    fun moveExercise(exerciseId: Long, up: Boolean) {
        viewModelScope.launch { repository.moveExercise(workoutId, exerciseId, up) }
    }

    fun logWeight(exerciseId: Long, date: LocalDate, weightKg: Double, reps: Int?) {
        viewModelScope.launch {
            repository.saveLog(ExerciseLog(exerciseId = exerciseId, date = date, weightKg = weightKg, reps = reps))
        }
    }

    private companion object {
        const val SPARKLINE_POINTS = 10
    }
}
