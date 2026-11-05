package com.sheuberger.fitness.ui.nutrition

import androidx.compose.runtime.Composable
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import java.time.LocalDate

// Sheets für "Meine Lebensmittel": eines eintragen, eines anlegen oder bearbeiten.
// Sie kennen kein ViewModel, nur Callbacks. So lassen sie sich aus dem Essen-Tab und nach einem Scan gleich nutzen.

/**
 * Ein Lebensmittel eintragen: Menge und Einheit ([com.sheuberger.fitness.nutrition.units]), Mahlzeit,
 * live berechnete Kalorien und Protein ([com.sheuberger.fitness.nutrition.toEntry]), Favorit an/aus, "Bearbeiten".
 *
 * @param onLog bekommt den fertigen Eintrag. Das Sheet schließt sich vorher selbst.
 * @param onEdit öffnet [FoodEditSheet] für dieses Lebensmittel (Sheet schließt sich vorher).
 * @param onToggleFavorite speichert den umgeschalteten Favoriten; das Sheet bleibt offen.
 * @param remainingKcal was heute noch bis zum Ziel fehlt, für den Hinweis "danach noch … kcal". null: kein Hinweis.
 */
@Composable
fun FoodLogSheet(
    food: Food,
    date: LocalDate,
    initialSlot: MealSlot,
    onLog: (FoodEntry) -> Unit,
    onEdit: (Food) -> Unit,
    onToggleFavorite: (Food) -> Unit,
    onDismiss: () -> Unit,
    remainingKcal: Double? = null,
) {
    // Platzhalter: Den Inhalt baut Agent 1 (Logik und Mensa).
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = food.name)
    }
}

/**
 * Lebensmittel anlegen ([initial] = null) oder bearbeiten: Name, Marke, Werte pro 100 g oder pro Portion,
 * Kalorien (Pflicht), Protein, Kohlenhydrate, Fett, Gramm pro Portion, Barcode.
 * Bei Daten von Open Food Facts steht die Quellenangabe [com.sheuberger.fitness.nutrition.OpenFoodFactsClient.ATTRIBUTION]
 * dabei, und Bearbeiten setzt `edited = true`.
 *
 * @param barcode vorbelegter Barcode für ein neues Lebensmittel, z. B. nach einem Scan ohne Treffer.
 * @param onSave bekommt das Lebensmittel (bei Neuem mit `id == 0`). Das Sheet schließt sich vorher selbst.
 * @param onDelete null: kein "Löschen"-Knopf (z. B. bei einem neuen Lebensmittel).
 */
@Composable
fun FoodEditSheet(
    initial: Food?,
    onSave: (Food) -> Unit,
    onDismiss: () -> Unit,
    barcode: String? = null,
    onDelete: ((Food) -> Unit)? = null,
) {
    // Platzhalter: Den Inhalt baut Agent 1 (Logik und Mensa).
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = if (initial == null) "Neues Lebensmittel" else "Lebensmittel bearbeiten")
    }
}
