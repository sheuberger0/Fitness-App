package com.sheuberger.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Eine Übung aus der Übungsbibliothek, z. B. "Bankdrücken".
 * Eine Übung kann in mehreren Workouts vorkommen; der Gewichtsverlauf gehört zur Übung.
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String? = null,
)

/** Ein Workout (Trainingsplan), z. B. "Push" oder "Oberkörper A". */
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Verknüpfungstabelle zwischen Workout und Übung (n:m-Beziehung).
 * [position] bestimmt die Reihenfolge der Übungen im Workout.
 * Wird ein Workout oder eine Übung gelöscht, verschwindet die Verknüpfung automatisch (CASCADE).
 */
@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["workoutId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class WorkoutExercise(
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
)

/** Ein Gewichtseintrag für eine Übung an einem bestimmten Tag. */
@Entity(
    tableName = "exercise_logs",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId"), Index("date")],
)
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val date: LocalDate,
    val weightKg: Double,
    val reps: Int? = null,
)

/** Ein Körpergewichts-Eintrag. Pro Tag gibt es höchstens einen Eintrag. */
@Entity(
    tableName = "body_weights",
    indices = [Index(value = ["date"], unique = true)],
)
data class BodyWeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
)
