package com.sheuberger.fitness.ui.workouts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.ExerciseLog
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.rotation.RotationSettings
import com.sheuberger.fitness.ui.exercises.ExerciseDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Die Karte "Als Nächstes" mit echter (In-Memory-)Datenbank: Reihenfolge, Erkennen, Überspringen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = FitnessRepository(db)
    private val settings = RotationSettings(context)

    // Eigener Scope statt des Haupt-Threads: Der ist im Test mit dem Test selbst beschäftigt.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Montag, 9. November 2026.
    private val today = LocalDate.of(2026, 11, 9)
    private val yesterday = today.minusDays(1)

    private val vm = WorkoutsViewModel(repository, settings, today = { today }, scope = scope)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun awaitState(predicate: (WorkoutsUiState) -> Boolean = { true }): WorkoutsUiState = runBlocking {
        withTimeout(5_000) { vm.uiState.first { !it.loading && predicate(it) } }
    }

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    private fun exercise(name: String): Long = run { repository.createExercise(name, null) }

    private fun workout(name: String, vararg exercises: Long): Long = run {
        val id = repository.createWorkout(name)
        exercises.forEach { repository.addExerciseToWorkout(id, it) }
        id
    }

    private fun log(exercise: Long, date: LocalDate, workout: Long? = null) = run {
        repository.saveLog(ExerciseLog(exerciseId = exercise, date = date, weightKg = 50.0, workoutId = workout))
    }

    // Simons Plan. Bankdrücken steckt in Upper A und Upper B.
    private val bank = exercise("Bankdrücken")
    private val rudern = exercise("Rudern")
    private val klimmzug = exercise("Klimmzüge")
    private val kniebeuge = exercise("Kniebeugen")
    private val kreuzheben = exercise("Kreuzheben")
    private val upperA = workout("Upper A", bank, rudern)
    private val lowerA = workout("Lower A", kniebeuge)
    private val upperB = workout("Upper B", bank, klimmzug)
    private val lowerB = workout("Lower B", kreuzheben)

    private fun setUpRotation() {
        vm.saveRotation(listOf(upperA, lowerA, upperB, lowerB))
        awaitState { it.next != null }
    }

    @Test
    fun hinweis_bisZurReihenfolge_undWegklickbar() {
        assertTrue(awaitState().showRotationHint)
        vm.dismissRotationHint()
        assertFalse(awaitState { !it.showRotationHint }.showRotationHint)
    }

    @Test
    fun reihenfolge_karteZeigtDasErste_undDieListeFolgtDerReihenfolge() {
        setUpRotation()
        val state = awaitState()
        val next = state.next!!
        assertEquals("Upper A", next.workout.workout.name)
        assertFalse(next.trainedToday)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), next.steps.map { it.name })
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), state.workouts.map { it.workout.name })
        assertFalse(state.showRotationHint)
    }

    @Test
    fun nachDemTrainingVonGestern_istDasNaechsteDran() {
        setUpRotation()
        log(rudern, yesterday, upperA)
        val next = awaitState { it.next?.workout?.workout?.id == lowerA }.next!!
        assertEquals("Upper A", next.lastWorkoutName)
        assertEquals(yesterday, next.lastDate)
    }

    @Test
    fun amTrainingstag_bleibtDieKarteBeiHeute_mitFortschritt() {
        setUpRotation()
        log(bank, today, upperA)
        val next = awaitState { it.next?.trainedToday == true }.next!!
        // Nicht schon "Lower A": Upper A bleibt bis Mitternacht auf der Karte.
        assertEquals(upperA, next.workout.workout.id)
        assertEquals(1, next.doneToday)
        assertEquals(2, next.workout.exerciseNames.size)
    }

    @Test
    fun ueberspringen_undAnderesWorkoutWaehlen() {
        setUpRotation()
        log(rudern, yesterday, upperA)
        awaitState { it.next?.workout?.workout?.id == lowerA }

        vm.skip()
        awaitState { it.next?.workout?.workout?.id == upperB }

        vm.chooseNext(upperA)
        awaitState { it.next?.workout?.workout?.id == upperA }

        // Wer dann trainiert, ersetzt die Wahl: Nach Upper A heute kommt beim nächsten Mal Lower A.
        log(rudern, today, upperA)
        val next = awaitState { it.next?.trainedToday == true }.next!!
        assertEquals(upperA, next.workout.workout.id)
    }

    @Test
    fun heuteWarEinAnderesWorkout_ordnetDieEintraegeNeuZu() {
        setUpRotation()
        // Im Übungen-Tab nur Bankdrücken eingetragen: passt zu Upper A und B, Upper A war dran.
        log(bank, today)
        assertEquals(upperA, awaitState { it.next?.trainedToday == true }.next!!.workout.workout.id)

        vm.setTodayWorkout(upperB)

        assertEquals(upperB, awaitState { it.next?.workout?.workout?.id == upperB }.next!!.workout.workout.id)
        assertEquals(listOf<Long?>(upperB), run { db.exerciseLogDao().observeAll().first() }.map { it.workoutId })
    }

    @Test
    fun zuletztTrainiert_folgtDerErkennung() {
        setUpRotation()
        // Bankdrücken in Upper B: Upper A teilt sich die Übung, wurde aber nicht trainiert.
        log(bank, yesterday, upperB)
        val state = awaitState { it.next?.workout?.workout?.id == lowerB }
        val byName = state.workouts.associateBy { it.workout.name }
        assertEquals(yesterday, byName.getValue("Upper B").lastTrained)
        assertNull(byName.getValue("Upper A").lastTrained)
    }

    @Test
    fun ausDemWorkoutGeoeffnet_merktSichDerEintragDasWorkout() {
        val inWorkout = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to bank, "workoutId" to upperB)),
            repository,
            scope,
        )
        val fromLibrary = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to bank, "workoutId" to -1L)),
            repository,
            scope,
        )
        val detail = WorkoutDetailViewModel(SavedStateHandle(mapOf("workoutId" to lowerA)), repository, scope)

        inWorkout.saveLog(null, today, 80.0, 8)
        fromLibrary.saveLog(null, today, 82.5, 5)
        detail.logWeight(kniebeuge, today, 100.0, 5)

        // Die drei Speichervorgänge laufen gleichzeitig, deshalb über das Gewicht statt die Reihenfolge zuordnen.
        val logs = awaitLogs { it.size == 3 }
        assertEquals(mapOf(80.0 to upperB, 82.5 to null, 100.0 to lowerA), logs.associate { it.weightKg to it.workoutId })

        // Bearbeiten (auch aus dem Übungen-Tab) lässt das Workout des Eintrags stehen.
        val original = logs.single { it.weightKg == 80.0 }
        fromLibrary.saveLog(original, yesterday, 85.0, 6)
        val edited = awaitLogs { all -> all.any { it.weightKg == 85.0 } }.single { it.id == original.id }
        assertEquals(upperB, edited.workoutId)
        assertEquals(yesterday, edited.date)
        assertEquals(6, edited.reps)
    }

    private fun awaitLogs(predicate: (List<ExerciseLog>) -> Boolean): List<ExerciseLog> = runBlocking {
        withTimeout(5_000) { db.exerciseLogDao().observeAll().first(predicate) }
    }
}
