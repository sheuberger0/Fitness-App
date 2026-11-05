package com.sheuberger.fitness.ui.nutrition

import androidx.compose.runtime.Composable
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.nutrition.PortionValues
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import java.time.LocalDate

/**
 * Sheet "Mensa-Gericht eintragen". Wird an zwei Stellen geöffnet: im Essen-Tab ("+" → Mensa) und im Mensa-Tab
 * (Gericht antippen → "Eintragen").
 *
 * Zeigt die Startwerte einer Portion mit ihrer Herkunft ([NutritionFormat.origin]), die Portionsgröße
 * (½, 1, 1½, 2) und die Mahlzeit. Die Werte lassen sich korrigieren; das nächste Mal startet das Gericht
 * dann mit deinen Werten (der letzte Eintrag ist das "Gedächtnis", siehe [com.sheuberger.fitness.nutrition.mensaPortion]).
 *
 * @param loadPortion liefert die Startwerte, z. B. `NutritionRepository::mensaPortion`.
 * @param onLog bekommt den fertigen Eintrag. Das Sheet schließt sich vorher selbst (`dismiss { onLog(...) }`).
 * @param remainingKcal was heute noch bis zum Ziel fehlt, für den Hinweis "danach noch … kcal". null: kein Hinweis.
 */
@Composable
fun MensaLogSheet(
    meal: Meal,
    date: LocalDate,
    initialSlot: MealSlot,
    loadPortion: suspend (Meal) -> PortionValues,
    onLog: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
    remainingKcal: Double? = null,
) {
    // Platzhalter: Den Inhalt baut Agent 1 (Logik und Mensa).
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = meal.name)
    }
}
