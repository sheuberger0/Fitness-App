package com.sheuberger.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// DAOs (Data Access Objects) beschreiben die Datenbankzugriffe.
// Funktionen, die einen Flow zurückgeben, liefern automatisch neue Daten,
// sobald sich die Tabelle ändert. Dadurch aktualisiert sich die Oberfläche von selbst.

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<Workout?>

    @Insert
    suspend fun insert(workout: Workout): Long

    @Update
    suspend fun update(workout: Workout)

    @Delete
    suspend fun delete(workout: Workout)

    @Query(
        """
        SELECT e.* FROM exercises e
        INNER JOIN workout_exercises we ON we.exerciseId = e.id
        WHERE we.workoutId = :workoutId
        ORDER BY we.position
        """
    )
    fun observeExercises(workoutId: Long): Flow<List<Exercise>>

    @Query("SELECT * FROM workout_exercises")
    fun observeAllLinks(): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getLinks(workoutId: Long): List<WorkoutExercise>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: WorkoutExercise)

    @Update
    suspend fun updateLinks(links: List<WorkoutExercise>)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun deleteLink(workoutId: Long, exerciseId: Long)
}

@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_logs ORDER BY date, id")
    fun observeAll(): Flow<List<ExerciseLog>>

    @Query("SELECT * FROM exercise_logs WHERE exerciseId = :exerciseId ORDER BY date, id")
    fun observeForExercise(exerciseId: Long): Flow<List<ExerciseLog>>

    @Insert
    suspend fun insert(log: ExerciseLog): Long

    @Update
    suspend fun update(log: ExerciseLog)

    @Delete
    suspend fun delete(log: ExerciseLog)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weights ORDER BY date")
    fun observeAll(): Flow<List<BodyWeightEntry>>

    @Query("SELECT * FROM body_weights WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: LocalDate): BodyWeightEntry?

    @Insert
    suspend fun insert(entry: BodyWeightEntry): Long

    @Update
    suspend fun update(entry: BodyWeightEntry)

    @Delete
    suspend fun delete(entry: BodyWeightEntry)
}
