package com.sheuberger.fitness.ui.nutrition

import androidx.compose.runtime.Composable
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader

/**
 * Sheet "Barcode": startet den Google-Scanner (braucht keine Kamera-Berechtigung, siehe
 * [com.sheuberger.fitness.nutrition.BarcodeScanner]) oder nimmt die Ziffern von Hand an, prüft den Code mit
 * [resolve] (in der App `AddFoodViewModel::resolveBarcode`) und zeigt Laden, Fehler und "Erneut versuchen" selbst.
 *
 * @param onFood ein Lebensmittel zum Eintragen. Schon gespeichert (`id != 0`, aus "Meine Lebensmittel") oder
 *   neu von Open Food Facts (`id == 0`, noch nicht gespeichert, Barcode gesetzt).
 * @param onCreateFood kein Treffer (Ladencode oder bei Open Food Facts unbekannt): Formular "Neues Lebensmittel"
 *   mit diesem Barcode öffnen.
 *
 * Vor jedem Callback schließt sich das Sheet selbst (`dismiss { ... }`); [onDismiss] kommt immer nach dem Schließen.
 */
@Composable
fun BarcodeSheet(
    resolve: suspend (String) -> BarcodeOutcome,
    onFood: (Food) -> Unit,
    onCreateFood: (barcode: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Platzhalter: Den Inhalt baut Agent 1 (Logik und Mensa).
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = "Barcode")
    }
}
