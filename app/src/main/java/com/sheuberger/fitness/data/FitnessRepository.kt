package com.sheuberger.fitness.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Die einzige Stelle, über die ViewModels auf Daten zugreifen.
 * So bleibt die Oberfläche unabhängig davon, wie und wo die Daten gespeichert werden.
 */
class FitnessRepository(private val db: AppDatabase) {
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val logDao = db.exerciseLogDao()
    private val bodyWeightDao = db.bodyWeightDao()

    val exercises: Flow<List<Exercise>> = exerciseDao.observeAll()
    val workouts: Flow<List<Workout>> = workoutDao.observeAll()
    val workoutLinks: Flow<List<WorkoutExercise>> = workoutDao.observeAllLinks()
    val exerciseLogs: Flow<List<ExerciseLog>> = logDao.observeAll()
    val bodyWeights: Flow<List<BodyWeightEntry>> = bodyWeightDao.observeAll()

    fun workout(id: Long): Flow<Workout?> = workoutDao.observe(id)
    fun workoutExercises(workoutId: Long): Flow<List<Exercise>> = workoutDao.observeExercises(workoutId)
    fun exercise(id: Long): Flow<Exercise?> = exerciseDao.observe(id)
    fun logsFor(exerciseId: Long): Flow<List<ExerciseLog>> = logDao.observeForExercise(exerciseId)

    // --- Workouts ---

    suspend fun createWorkout(name: String): Long = workoutDao.insert(Workout(name = name))

    suspend fun renameWorkout(workout: Workout, name: String) = workoutDao.update(workout.copy(name = name))

    suspend fun deleteWorkout(workout: Workout) = workoutDao.delete(workout)

    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) = db.withTransaction {
        val nextPosition = (workoutDao.getLinks(workoutId).maxOfOrNull { it.position } ?: -1) + 1
        workoutDao.insertLink(WorkoutExercise(workoutId, exerciseId, nextPosition))
    }

    suspend fun removeExerciseFromWorkout(workoutId: Long, exerciseId: Long) =
        workoutDao.deleteLink(workoutId, exerciseId)

    /** Verschiebt eine Übung im Workout um eine Position nach oben oder unten. */
    suspend fun moveExercise(workoutId: Long, exerciseId: Long, up: Boolean) = db.withTransaction {
        val links = workoutDao.getLinks(workoutId)
        val index = links.indexOfFirst { it.exerciseId == exerciseId }
        val target = if (up) index - 1 else index + 1
        if (index == -1 || target !in links.indices) return@withTransaction
        val reordered = links.toMutableList().apply { add(target, removeAt(index)) }
        workoutDao.updateLinks(reordered.mapIndexed { i, link -> link.copy(position = i) })
    }

    // --- Übungen ---

    /** Legt eine Übung an. Gibt es den Namen schon, wird die vorhandene Übung verwendet. */
    suspend fun createExercise(name: String, muscleGroup: String?): Long {
        exerciseDao.findByName(name)?.let { return it.id }
        return exerciseDao.insert(Exercise(name = name, muscleGroup = muscleGroup))
    }

    /** Gibt false zurück, wenn bereits eine andere Übung so heißt. */
    suspend fun updateExercise(exercise: Exercise): Boolean {
        val existing = exerciseDao.findByName(exercise.name)
        if (existing != null && existing.id != exercise.id) return false
        exerciseDao.update(exercise)
        return true
    }

    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    // --- Gewichtseinträge für Übungen ---

    suspend fun saveLog(log: ExerciseLog) {
        if (log.id == 0L) logDao.insert(log) else logDao.update(log)
    }

    suspend fun deleteLog(log: ExerciseLog) = logDao.delete(log)

    // --- Körpergewicht ---

    /** Speichert einen Eintrag. Existiert für das Datum schon ein anderer, wird dieser ersetzt. */
    suspend fun saveBodyWeight(entry: BodyWeightEntry) = db.withTransaction {
        val sameDay = bodyWeightDao.findByDate(entry.date)
        if (sameDay != null && sameDay.id != entry.id) bodyWeightDao.delete(sameDay)
        if (entry.id == 0L) bodyWeightDao.insert(entry) else bodyWeightDao.update(entry)
    }

    suspend fun deleteBodyWeight(entry: BodyWeightEntry) = bodyWeightDao.delete(entry)
}
