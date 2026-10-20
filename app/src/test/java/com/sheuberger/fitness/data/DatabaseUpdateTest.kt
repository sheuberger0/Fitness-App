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
