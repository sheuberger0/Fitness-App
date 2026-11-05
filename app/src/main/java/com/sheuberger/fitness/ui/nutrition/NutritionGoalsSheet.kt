package com.sheuberger.fitness.ui.nutrition

import androidx.compose.runtime.Composable
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader

/** Ziele (wird im nächsten Schritt gefüllt). */
@Composable
fun NutritionGoalsSheet(
    goals: NutritionGoals,
    onChange: ((NutritionGoals) -> NutritionGoals) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = "Ziele")
    }
}
