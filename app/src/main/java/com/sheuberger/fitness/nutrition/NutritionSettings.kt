package com.sheuberger.fitness.nutrition

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Deine Ziele für den Kalorien-Tracker. Alles lässt sich in den Einstellungen ändern. */
data class NutritionGoals(
    val kcalTarget: Int = DEFAULT_KCAL,
    val proteinTargetG: Int = DEFAULT_PROTEIN,
    /** Gewünschte Gewichtsänderung pro Woche in kg, z. B. +0,25 für langsamen Aufbau. */
    val weeklyGoalKg: Double = DEFAULT_WEEKLY_GOAL,
    /** Wann du den letzten Zielvorschlag übernommen oder verschoben hast. Danach 14 Tage Ruhe. */
    val lastReview: LocalDate? = null,
) {
    companion object {
        const val DEFAULT_KCAL = 3800
        const val KCAL_STEP = 50
        const val MIN_KCAL = 1200
        const val MAX_KCAL = 6000

        const val DEFAULT_PROTEIN = 150
        const val PROTEIN_STEP = 5
        const val MIN_PROTEIN = 40
        const val MAX_PROTEIN = 300

        const val DEFAULT_WEEKLY_GOAL = 0.25
        const val WEEKLY_GOAL_STEP = 0.05
        const val MIN_WEEKLY_GOAL = -1.0
        const val MAX_WEEKLY_GOAL = 1.0
    }
}

/**
 * Speichert die Ziele in den SharedPreferences, wie [com.sheuberger.fitness.mensa.MensaSettings].
 * Für vier Werte braucht es keine Tabelle und keine Migration.
 */
class NutritionSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nutrition", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val goals: StateFlow<NutritionGoals> = state.asStateFlow()

    fun update(transform: (NutritionGoals) -> NutritionGoals) {
        val next = transform(state.value).let {
            it.copy(
                kcalTarget = it.kcalTarget.coerceIn(NutritionGoals.MIN_KCAL, NutritionGoals.MAX_KCAL),
                proteinTargetG = it.proteinTargetG.coerceIn(NutritionGoals.MIN_PROTEIN, NutritionGoals.MAX_PROTEIN),
                weeklyGoalKg = it.weeklyGoalKg.coerceIn(NutritionGoals.MIN_WEEKLY_GOAL, NutritionGoals.MAX_WEEKLY_GOAL),
            )
        }
        state.value = next
        prefs.edit()
            .putInt(KEY_KCAL, next.kcalTarget)
            .putInt(KEY_PROTEIN, next.proteinTargetG)
            .putFloat(KEY_GOAL, next.weeklyGoalKg.toFloat())
            .putLong(KEY_REVIEW, next.lastReview?.toEpochDay() ?: NO_DATE)
            .apply()
    }

    private fun read(): NutritionGoals {
        val defaults = NutritionGoals()
        return NutritionGoals(
            kcalTarget = prefs.getInt(KEY_KCAL, defaults.kcalTarget),
            proteinTargetG = prefs.getInt(KEY_PROTEIN, defaults.proteinTargetG),
            // Float hat Rundungsreste (0.25f ist exakt, 0.3f nicht): auf zwei Nachkommastellen runden.
            weeklyGoalKg = Math.round(prefs.getFloat(KEY_GOAL, defaults.weeklyGoalKg.toFloat()) * 100) / 100.0,
            lastReview = prefs.getLong(KEY_REVIEW, NO_DATE).takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay),
        )
    }

    private companion object {
        const val KEY_KCAL = "kcal_target"
        const val KEY_PROTEIN = "protein_target"
        const val KEY_GOAL = "weekly_goal_kg"
        const val KEY_REVIEW = "last_review"
        const val NO_DATE = Long.MIN_VALUE
    }
}
