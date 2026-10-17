package com.sheuberger.fitness.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.Workout
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
    /** Neuestes Datum, an dem für eine der Übungen ein Gewicht eingetragen wurde. */
    val lastTrained: LocalDate?,
)

data class WorkoutsUiState(
    val workouts: List<WorkoutSummary> = emptyList(),
    // Solange noch keine Daten da sind, zeigen wir keinen leeren Zustand an (sonst "blitzt" er kurz auf).
    val loading: Boolean = true,
)

class WorkoutsViewModel(private val repository: FitnessRepository) : ViewModel() {

    val uiState: StateFlow<WorkoutsUiState> = combine(
        repository.workouts,
        repository.workoutLinks,
        repository.exercises,
        repository.exerciseLogs,
    ) { workouts, links, exercises, logs ->
        // Einmal Nachschlage-Tabellen bauen, statt für jedes Workout alle Listen zu durchsuchen.
        val exerciseById = exercises.associateBy { it.id }
        val lastDateByExercise = logs.groupBy { it.exerciseId }.mapValues { (_, l) -> l.maxOf { it.date } }
        val linksByWorkout = links.groupBy { it.workoutId }

        WorkoutsUiState(
            workouts = workouts.map { workout ->
                val ordered = linksByWorkout[workout.id].orEmpty().sortedBy { it.position }
                WorkoutSummary(
                    workout = workout,
                    exerciseNames = ordered.mapNotNull { exerciseById[it.exerciseId]?.name },
                    lastTrained = ordered.mapNotNull { lastDateByExercise[it.exerciseId] }.maxOrNull(),
                )
            },
            loading = false,
        )
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
}
