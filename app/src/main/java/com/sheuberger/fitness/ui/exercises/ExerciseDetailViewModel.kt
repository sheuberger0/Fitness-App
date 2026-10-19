package com.sheuberger.fitness.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheuberger.fitness.data.Exercise
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.ui.components.ChartPoint
import com.sheuberger.fitness.ui.components.TimeRange
import com.sheuberger.fitness.ui.components.dailyBest
import com.sheuberger.fitness.ui.components.inRange
import com.sheuberger.fitness.ui.components.latest
import com.sheuberger.fitness.ui.components.personalBest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Alle Einträge eines Tages, so wie sie im Verlauf erscheinen. */
data class LogDay(val date: LocalDate, val logs: List<ExerciseLog>)

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val logs: List<ExerciseLog> = emptyList(),
    /** Bestes Gewicht pro Tag über die ganze Zeit. */
    val points: List<ChartPoint> = emptyList(),
    /** Dieselben Punkte, aber nur im gewählten Zeitraum. */
    val rangePoints: List<ChartPoint> = emptyList(),
    val range: TimeRange = TimeRange.ThreeMonths,
    val latest: ExerciseLog? = null,
    val personalBest: ExerciseLog? = null,
    /** Verlauf: neuester Tag zuerst, innerhalb eines Tages in der Reihenfolge der Eingabe. */
    val history: List<LogDay> = emptyList(),
    /** Namen aller anderen Übungen (klein geschrieben), um doppelte Namen zu verhindern. */
    val otherNames: Set<String> = emptySet(),
) {
    /** Die Übung wurde gelöscht (z. B. auf diesem oder einem anderen Screen). */
    val deleted: Boolean get() = !loading && exercise == null
}

class ExerciseDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FitnessRepository,
) : ViewModel() {
    private val exerciseId: Long = checkNotNull(savedStateHandle.get<Long>("exerciseId"))
    private val range = MutableStateFlow(TimeRange.ThreeMonths)

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        repository.exercise(exerciseId),
        repository.logsFor(exerciseId),
        repository.exercises,
        range,
    ) { exercise, logs, allExercises, range ->
        val points = logs.dailyBest()
        ExerciseDetailUiState(
            loading = false,
            exercise = exercise,
            logs = logs,
            points = points,
            rangePoints = points.inRange(range),
            range = range,
            latest = logs.latest(),
            personalBest = logs.personalBest(),
            history = logs
                .groupBy { it.date }
                .map { (date, dayLogs) -> LogDay(date, dayLogs.sortedBy { it.id }) }
                .sortedByDescending { it.date },
            otherNames = allExercises
                .filter { it.id != exerciseId }
                .map { it.name.lowercase() }
                .toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun setRange(value: TimeRange) {
        range.value = value
    }

    /** [existing] ist null für einen neuen Eintrag, sonst der Eintrag, der bearbeitet wird. */
    fun saveLog(existing: ExerciseLog?, date: LocalDate, weightKg: Double, reps: Int?) {
        viewModelScope.launch {
            repository.saveLog(
                ExerciseLog(
                    id = existing?.id ?: 0,
                    exerciseId = exerciseId,
                    date = date,
                    weightKg = weightKg,
                    reps = reps,
                ),
            )
        }
    }

    fun deleteLog(log: ExerciseLog) {
        viewModelScope.launch { repository.deleteLog(log) }
    }

    /** Meldet über [onResult], ob das Speichern geklappt hat (false = Name schon vergeben). */
    fun updateExercise(name: String, muscleGroup: String?, onResult: (Boolean) -> Unit) {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch {
            onResult(repository.updateExercise(exercise.copy(name = name, muscleGroup = muscleGroup)))
        }
    }

    fun deleteExercise() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch { repository.deleteExercise(exercise) }
    }
}
