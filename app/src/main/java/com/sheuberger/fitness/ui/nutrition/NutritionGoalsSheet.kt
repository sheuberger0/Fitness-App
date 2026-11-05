package com.sheuberger.fitness.ui.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SectionLabel
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Ziele: Kalorien und Protein pro Tag und wie sich das Gewicht pro Woche ändern soll. Jede Änderung gilt sofort,
 * wie in den Mensa-Einstellungen; "Fertig" schließt nur. Unter jedem Wert steht in einem Satz, was er bedeutet.
 *
 * @param bodyWeightKg Durchschnittsgewicht der letzten Tage, für "≈ 2,0 g pro kg". null: nur die Faustregel.
 */
@Composable
fun NutritionGoalsSheet(
    goals: NutritionGoals,
    onChange: ((NutritionGoals) -> NutritionGoals) -> Unit,
    onDismiss: () -> Unit,
    bodyWeightKg: Double? = null,
) {
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = "Ziele",
            subtitle = "Für Tagebuch und Trend",
            action = { QuietButton("Fertig", onClick = { dismiss() }) },
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            SectionLabel("Kalorien pro Tag")
            Spacer(Modifier.height(8.dp))
            GoalStepper(
                value = NutritionFormat.kcal(goals.kcalTarget.toDouble()),
                onDecrement = { onChange { it.copy(kcalTarget = stepKcalTarget(it.kcalTarget, -1)) } },
                onIncrement = { onChange { it.copy(kcalTarget = stepKcalTarget(it.kcalTarget, +1)) } },
                decrementEnabled = goals.kcalTarget > NutritionGoals.MIN_KCAL,
                incrementEnabled = goals.kcalTarget < NutritionGoals.MAX_KCAL,
                decrementLabel = "Kalorienziel senken",
                incrementLabel = "Kalorienziel erhöhen",
            )
            Hint(
                "Was du im Schnitt pro Tag essen willst. Nach einer Änderung prüft die App frühestens nach " +
                    "14 Tagen, ob es passt.",
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Protein pro Tag")
            Spacer(Modifier.height(8.dp))
            GoalStepper(
                value = NutritionFormat.grams(goals.proteinTargetG.toDouble()),
                onDecrement = { onChange { it.copy(proteinTargetG = stepProteinTarget(it.proteinTargetG, -1)) } },
                onIncrement = { onChange { it.copy(proteinTargetG = stepProteinTarget(it.proteinTargetG, +1)) } },
                decrementEnabled = goals.proteinTargetG > NutritionGoals.MIN_PROTEIN,
                incrementEnabled = goals.proteinTargetG < NutritionGoals.MAX_PROTEIN,
                decrementLabel = "Proteinziel senken",
                incrementLabel = "Proteinziel erhöhen",
            )
            Hint(proteinHint(goals.proteinTargetG, bodyWeightKg))

            Spacer(Modifier.height(20.dp))
            SectionLabel("Gewicht pro Woche")
            Spacer(Modifier.height(8.dp))
            GoalStepper(
                // Ohne "pro Woche" (steht in der Überschrift): "+0,25 kg pro Woche" passt auf 360 dp nicht
                // zwischen die beiden Buttons.
                value = NutritionFormat.signedKg(goals.weeklyGoalKg),
                onDecrement = { onChange { it.copy(weeklyGoalKg = stepWeeklyGoal(it.weeklyGoalKg, -1)) } },
                onIncrement = { onChange { it.copy(weeklyGoalKg = stepWeeklyGoal(it.weeklyGoalKg, +1)) } },
                // Kleine Toleranz: Kommazahlen treffen −1,0 nicht immer genau.
                decrementEnabled = goals.weeklyGoalKg > NutritionGoals.MIN_WEEKLY_GOAL + 1e-6,
                incrementEnabled = goals.weeklyGoalKg < NutritionGoals.MAX_WEEKLY_GOAL - 1e-6,
                decrementLabel = "Wochenziel senken",
                incrementLabel = "Wochenziel erhöhen",
            )
            Hint(NutritionFormat.dailyBalance(goals.weeklyGoalKg))
        }
    }
}

/** Ein Satz unter dem Wert, wie unter dem Budget in den Mensa-Einstellungen. */
@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Nächster Wert im Raster von [step]: "+" springt zum nächsten Vielfachen darüber, "−" zum nächsten darunter.
 * Nach einem übernommenen Vorschlag kann das Ziel z. B. 4020 kcal sein; "+" führt dann auf 4050, nicht auf 4070.
 */
internal fun stepOnGrid(current: Int, step: Int, direction: Int, min: Int, max: Int): Int {
    val next = if (direction > 0) Math.floorDiv(current, step) * step + step else -Math.floorDiv(-current, step) * step - step
    return next.coerceIn(min, max)
}

internal fun stepKcalTarget(current: Int, direction: Int): Int =
    stepOnGrid(current, NutritionGoals.KCAL_STEP, direction, NutritionGoals.MIN_KCAL, NutritionGoals.MAX_KCAL)

internal fun stepProteinTarget(current: Int, direction: Int): Int =
    stepOnGrid(current, NutritionGoals.PROTEIN_STEP, direction, NutritionGoals.MIN_PROTEIN, NutritionGoals.MAX_PROTEIN)

/** Wie [stepOnGrid], in Schritten von 0,05 kg. Auf zwei Nachkommastellen gerundet, damit aus 0,3 nicht 0,30000000004 wird. */
internal fun stepWeeklyGoal(current: Double, direction: Int): Double {
    val step = NutritionGoals.WEEKLY_GOAL_STEP
    // Kleine Toleranz: 0,25 / 0,05 ergibt mit Kommazahlen im Computer nicht immer genau 5.
    val steps = current / step
    val next = if (direction > 0) floor(steps + 1e-6) + 1 else ceil(steps - 1e-6) - 1
    return (Math.round(next * step * 100) / 100.0).coerceIn(NutritionGoals.MIN_WEEKLY_GOAL, NutritionGoals.MAX_WEEKLY_GOAL)
}

/**
 * Das Proteinziel im Verhältnis zum Körpergewicht. Die übliche Faustregel für Muskelaufbau sind 1,6 bis 2,2 g
 * pro kg; darüber bringt mehr Protein kaum noch etwas.
 */
internal fun proteinHint(proteinG: Int, bodyWeightKg: Double?): String {
    if (bodyWeightKg == null || bodyWeightKg <= 0) {
        return "Für Muskelaufbau gelten 1,6 bis 2,2 g pro kg Körpergewicht als sinnvoll."
    }
    val perKg = NutritionFormat.number(proteinG / bodyWeightKg, 1)
    return "≈ $perKg g pro kg Körpergewicht. Für Muskelaufbau gelten 1,6 bis 2,2 g als sinnvoll."
}
