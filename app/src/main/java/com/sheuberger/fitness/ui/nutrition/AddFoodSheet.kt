package com.sheuberger.fitness.ui.nutrition

import androidx.compose.runtime.Composable
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import java.time.LocalDate

/** Sheet "Eintragen" (wird im nächsten Schritt gefüllt). */
@Composable
fun AddFoodSheet(
    addFood: AddFoodViewModel,
    state: AddFoodUiState,
    date: LocalDate,
    today: LocalDate,
    initialSlot: MealSlot,
    remainingKcal: Double?,
    onLogged: (message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss, fillHeight = true) {
        AppSheetHeader(title = "Eintragen", subtitle = NutritionFormat.day(date, today))
    }
}
