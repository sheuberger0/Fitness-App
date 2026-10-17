package com.sheuberger.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Workout::class,
        WorkoutExercise::class,
        ExerciseLog::class,
        BodyWeightEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitness.db")
                .addCallback(SeedCallback)
                .build()
    }
}

/**
 * Wird genau einmal beim allerersten Start ausgeführt und füllt die
 * Übungsbibliothek mit gängigen Übungen, damit man nicht bei null anfängt.
 */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        DefaultExercises.forEach { (name, group) ->
            db.execSQL(
                "INSERT INTO exercises (name, muscleGroup) VALUES (?, ?)",
                arrayOf(name, group),
            )
        }
    }
}
