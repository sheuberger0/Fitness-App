package com.sheuberger.fitness.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.Exercise
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.components.dailyBest
import com.sheuberger.fitness.ui.components.latest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine Zeile in der Übungsliste: die Übung plus das, was rechts daneben angezeigt wird. */
data class ExerciseRowState(
    val exercise: Exercise,
    val latest: ExerciseLog?,
    /** Bestes Gewicht der letzten Trainingstage, für die kleine Sparkline. */
    val trend: List<Double>,
)

/** Alle Übungen einer Muskelgruppe. */
data class ExerciseSection(val group: String, val rows: List<ExerciseRowState>)

data class ExercisesUiState(
    val loading: Boolean = true,
    val selectedGroup: String? = null,
    val sections: List<ExerciseSection> = emptyList(),
    /** Gibt es überhaupt Übungen (unabhängig von Suche und Filter)? */
    val hasExercises: Boolean = false,
    /** Alle Namen in Kleinbuchstaben, um doppelte Namen schon beim Tippen zu erkennen. */
    val existingNames: Set<String> = emptySet(),
)

class ExercisesViewModel(private val repository: FitnessRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedGroup = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExercisesUiState> = combine(
        repository.exercises,
        repository.exerciseLogs,
        query,
        selectedGroup,
    ) { exercises, logs, query, group ->
        val logsByExercise = logs.groupBy { it.exerciseId }
        val search = query.trim()

        val rows = exercises
            .filter { group == null || groupOf(it) == group }
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
            .map { exercise ->
                val exerciseLogs = logsByExercise[exercise.id].orEmpty()
                ExerciseRowState(
                    exercise = exercise,
                    latest = exerciseLogs.latest(),
                    trend = exerciseLogs.dailyBest().takeLast(SparklineDays).map { it.value },
                )
            }

        // Reihenfolge der Abschnitte wie in der Liste der Muskelgruppen, nicht alphabetisch.
        val sections = rows
            .groupBy { groupOf(it.exercise) }
            .map { (name, groupRows) -> ExerciseSection(name, groupRows) }
            .sortedBy { MuscleGroups.indexOf(it.group) }

        ExercisesUiState(
            loading = false,
            selectedGroup = group,
            sections = sections,
            hasExercises = exercises.isNotEmpty(),
            existingNames = exercises.map { it.name.lowercase() }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun selectGroup(group: String?) {
        selectedGroup.value = group
    }

    fun createExercise(name: String, muscleGroup: String?) {
        viewModelScope.launch { repository.createExercise(name, muscleGroup) }
    }

    private companion object {
        const val SparklineDays = 12
    }
}

/** Übungen ohne (oder mit unbekannter) Muskelgruppe landen unter "Sonstige". */
internal fun groupOf(exercise: Exercise): String =
    exercise.muscleGroup?.takeIf { it in MuscleGroups } ?: "Sonstige"
