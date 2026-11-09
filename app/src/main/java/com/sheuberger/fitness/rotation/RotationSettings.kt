package com.sheuberger.fitness.rotation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Was zur Trainingsreihenfolge nicht in die Datenbank gehört, in den SharedPreferences (wie bei der Mensa):
 * die Wahl von Hand ("Überspringen") und ob der Hinweis zum Einrichten weggeklickt wurde.
 *
 * Die Reihenfolge selbst steht in der Datenbank (Spalte `rotationPosition` der Workouts). Gespeichert wird hier
 * nur die neueste Wahl, ältere braucht es nicht mehr: Spätestens das nächste Training ersetzt sie.
 */
class RotationSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reihenfolge", Context.MODE_PRIVATE)
    private val overrideState = MutableStateFlow(readOverride())
    private val hintState = MutableStateFlow(prefs.getBoolean(KEY_HINT_DISMISSED, false))

    /** Von Hand gewähltes nächstes Workout, oder null. */
    val override: StateFlow<RotationOverride?> = overrideState.asStateFlow()

    /** true, wenn der Hinweis "Welches Workout ist dran?" weggeklickt wurde. */
    val setupHintDismissed: StateFlow<Boolean> = hintState.asStateFlow()

    fun setOverride(value: RotationOverride) {
        overrideState.value = value
        prefs.edit()
            .putLong(KEY_WORKOUT, value.workoutId)
            .putLong(KEY_DATE, value.date.toEpochDay())
            .putLong(KEY_AFTER_LOG, value.afterLogId)
            .apply()
    }

    fun dismissSetupHint() {
        hintState.value = true
        prefs.edit().putBoolean(KEY_HINT_DISMISSED, true).apply()
    }

    private fun readOverride(): RotationOverride? {
        if (!prefs.contains(KEY_WORKOUT)) return null
        return RotationOverride(
            workoutId = prefs.getLong(KEY_WORKOUT, 0),
            date = LocalDate.ofEpochDay(prefs.getLong(KEY_DATE, 0)),
            afterLogId = prefs.getLong(KEY_AFTER_LOG, 0),
        )
    }

    private companion object {
        const val KEY_WORKOUT = "naechstes_workout"
        const val KEY_DATE = "naechstes_datum"
        const val KEY_AFTER_LOG = "naechstes_nach_eintrag"
        const val KEY_HINT_DISMISSED = "hinweis_weggeklickt"
    }
}
