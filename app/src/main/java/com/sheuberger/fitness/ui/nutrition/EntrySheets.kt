package com.sheuberger.fitness.ui.nutrition

import androidx.compose.runtime.Composable
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader

/** Eintrag bearbeiten (wird im nächsten Schritt gefüllt). */
@Composable
fun EntryEditSheet(
    entry: FoodEntry,
    onSave: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = entry.name)
    }
}
