package com.sheuberger.fitness.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Spielt ein App-Update durch: Eine Datenbank, wie sie eine ältere App-Version auf dem Handy
 * hinterlassen hat, wird mit dem aktuellen Code geöffnet. Alle Einträge müssen danach noch da sein.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseUpdateTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun everyReleasedVersionMigratesToCurrent() {
        for (version in 1 until AppDatabase.VERSION) {
            val name = "migrate-$version.db"
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS)
        }
    }

    @Test
    fun entriesFromVersion1SurviveUpdate() = runBlocking {
        helper.createDatabase(DB, 1).apply {
            // Eine Installation von App 1.0 bis 1.3: Standardübungen plus eigene Einträge.
            execSQL("INSERT INTO exercises (id, name, muscleGroup) VALUES (1, 'Bankdrücken', 'Brust')")
            execSQL("INSERT INTO exercises (id, name, muscleGroup) VALUES (2, 'Meine Übung', NULL)")
            execSQL("INSERT INTO workouts (id, name, createdAt) VALUES (1, 'Push', 1760000000000)")
            execSQL("INSERT INTO workout_exercises (workoutId, exerciseId, position) VALUES (1, 1, 0)")
            execSQL("INSERT INTO workout_exercises (workoutId, exerciseId, position) VALUES (1, 2, 1)")
            execSQL("INSERT INTO exercise_logs (exerciseId, date, weightKg, reps) VALUES (1, ${day(1)}, 80.0, 8)")
            execSQL("INSERT INTO exercise_logs (exerciseId, date, weightKg, reps) VALUES (1, ${day(8)}, 82.5, NULL)")
            execSQL("INSERT INTO body_weights (date, weightKg) VALUES (${day(1)}, 78.4)")
            close()
        }

        val db = open()

        assertEquals(listOf("Push"), db.workoutDao().observeAll().first().map { it.name })
        assertEquals(
            listOf("Bankdrücken", "Meine Übung"),
            db.workoutDao().observeExercises(1).first().map { it.name },
        )
        assertEquals(listOf(80.0, 82.5), db.exerciseLogDao().observeForExercise(1).first().map { it.weightKg })
        assertEquals(8, db.exerciseLogDao().observeForExercise(1).first().first().reps)
        assertEquals(listOf(78.4), db.bodyWeightDao().observeAll().first().map { it.weightKg })

        // Ab Version 2 gibt es die Tabellen des Kalorien-Trackers. Sie sind nach dem Update leer und nutzbar.
        val food = db.foodDao().insert(Food(name = "Haferflocken", kcal = 370.0))
        db.foodEntryDao().insert(
            FoodEntry(date = LocalDate.of(2026, 11, 4), meal = MealSlot.BREAKFAST, name = "Haferflocken", kcal = 296.0, foodId = food),
        )
        assertEquals(listOf("Haferflocken"), db.foodEntryDao().getDay(LocalDate.of(2026, 11, 4)).map { it.name })
    }

    @Test
    fun entriesFromVersion2SurviveUpdate() = runBlocking {
        helper.createDatabase(DB, 2).apply {
            // Eine Installation von App 2.0 bis 2.2: Workouts, Gewichtseinträge und ein Essens-Eintrag.
            execSQL("INSERT INTO exercises (id, name, muscleGroup) VALUES (1, 'Bankdrücken', 'Brust')")
            execSQL("INSERT INTO exercises (id, name, muscleGroup) VALUES (2, 'Kniebeugen', 'Beine')")
            execSQL("INSERT INTO workouts (id, name, createdAt) VALUES (1, 'Upper A', 1760000000000)")
            execSQL("INSERT INTO workouts (id, name, createdAt) VALUES (2, 'Lower A', 1760000001000)")
            execSQL("INSERT INTO workout_exercises (workoutId, exerciseId, position) VALUES (1, 1, 0)")
            execSQL("INSERT INTO workout_exercises (workoutId, exerciseId, position) VALUES (2, 2, 0)")
            execSQL("INSERT INTO exercise_logs (id, exerciseId, date, weightKg, reps) VALUES (1, 1, ${day(3)}, 80.0, 8)")
            execSQL("INSERT INTO exercise_logs (id, exerciseId, date, weightKg, reps) VALUES (2, 2, ${day(5)}, 100.0, 5)")
            execSQL("INSERT INTO body_weights (date, weightKg) VALUES (${day(3)}, 78.4)")
            execSQL(
                "INSERT INTO food_entries (date, loggedAt, meal, name, kcal, amount, unit, source, estimated) " +
                    "VALUES (${day(3)}, 1760000000000, 'LUNCH', 'Mensa-Menü', 850.0, 1.0, 'PORTION', 'MENSA', 0)",
            )
            close()
        }

        val db = open()

        // Alles ist noch da, die neuen Spalten sind leer: kein Workout in der Reihenfolge, Einträge ohne Workout.
        val workouts = db.workoutDao().observeAll().first()
        assertEquals(listOf("Lower A", "Upper A"), workouts.map { it.name })
        assertTrue(workouts.all { it.rotationPosition == null })
        val logs = db.exerciseLogDao().observeAll().first()
        assertEquals(listOf(80.0, 100.0), logs.map { it.weightKg })
        assertEquals(listOf(8, 5), logs.map { it.reps })
        assertTrue(logs.all { it.workoutId == null })
        assertEquals(listOf(78.4), db.bodyWeightDao().observeAll().first().map { it.weightKg })
        assertEquals(listOf("Mensa-Menü"), db.foodEntryDao().getDay(LocalDate.of(2026, 10, 3)).map { it.name })

        // Die neuen Spalten lassen sich nutzen: Reihenfolge festlegen, Satz im Workout eintragen.
        val repository = FitnessRepository(db)
        repository.setRotation(listOf(1, 2))
        repository.saveLog(ExerciseLog(exerciseId = 1, date = LocalDate.of(2026, 10, 7), weightKg = 82.5, workoutId = 1))
        assertEquals(
            listOf("Upper A" to 0, "Lower A" to 1),
            db.workoutDao().observeAll().first().sortedBy { it.rotationPosition }.map { it.name to it.rotationPosition },
        )
        assertEquals(listOf(null, null, 1L), db.exerciseLogDao().observeAll().first().map { it.workoutId })
        assertEquals(3, repository.latestLogId())
    }

    @Test
    fun deletingWorkoutKeepsItsEntries() = runBlocking {
        val db = open()
        val repository = FitnessRepository(db)
        val workoutId = repository.createWorkout("Upper A")
        val exerciseId = db.exerciseDao().findByName("Bankdrücken")!!.id
        repository.addExerciseToWorkout(workoutId, exerciseId)
        repository.saveLog(ExerciseLog(exerciseId = exerciseId, date = LocalDate.of(2026, 11, 9), weightKg = 80.0, workoutId = workoutId))

        repository.deleteWorkout(db.workoutDao().observe(workoutId).first()!!)

        // Der Satz bleibt, er weiß nur nicht mehr, in welchem (gelöschten) Workout er eingetragen wurde.
        val logs = db.exerciseLogDao().observeAll().first()
        assertEquals(listOf(80.0), logs.map { it.weightKg })
        assertEquals(listOf<Long?>(null), logs.map { it.workoutId })
    }

    @Test
    fun updateAddsNewDefaultExercisesButNotDeletedOnes() = runBlocking {
        // Alte Installation: "Bankdrücken" wurde gelöscht, eine eigene "Beinpresse" gibt es schon.
        helper.createDatabase(DB, AppDatabase.VERSION).apply {
            execSQL("INSERT INTO exercises (name, muscleGroup) VALUES ('Beinpresse', 'Sonstige')")
            close()
        }

        val defaults = listOf(
            "Bankdrücken" to "Brust",
            "Beinpresse" to "Beine",
            "Neue Maschine" to "Rücken",
        )
        var db = open(defaults)
        val names = db.exerciseDao().observeAll().first().map { it.name }

        assertTrue("Neue Standardübung fehlt", "Neue Maschine" in names)
        assertFalse("Gelöschte Standardübung ist zurückgekommen", "Bankdrücken" in names)
        assertEquals("Eigene Übung doppelt", 1, names.count { it == "Beinpresse" })
        assertEquals("Sonstige", db.exerciseDao().findByName("Beinpresse")!!.muscleGroup)

        // Löscht man die neue Übung, bleibt sie auch beim nächsten Start weg.
        db.exerciseDao().delete(db.exerciseDao().findByName("Neue Maschine")!!)
        db.close()
        db = open(defaults)
        assertFalse("Neue Maschine" in db.exerciseDao().observeAll().first().map { it.name })
    }

    @Test
    fun freshInstallGetsAllDefaultExercises() = runBlocking {
        val names = open().exerciseDao().observeAll().first().map { it.name }
        assertEquals(DefaultExercises.map { it.first }.sorted(), names.sorted())
    }

    private fun open(defaults: List<Pair<String, String?>> = DefaultExercises): AppDatabase =
        AppDatabase.build(context, DB, defaults).also { db = it }

    private fun day(n: Int) = LocalDate.of(2026, 10, n).toEpochDay()

    private companion object {
        const val DB = "update-test.db"
    }
}
