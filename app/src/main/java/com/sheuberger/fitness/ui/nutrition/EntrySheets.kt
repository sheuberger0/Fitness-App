package com.sheuberger.fitness.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.nutrition.withAmount
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.SectionLabel
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// Sheets für einzelne Einträge im Tagebuch: einen vorhandenen bearbeiten und einen früheren noch einmal eintragen.

/**
 * Einen früheren Eintrag noch einmal eintragen ("Zuletzt", "Zuletzt in der Mensa"): Menge und Mahlzeit wählen,
 * Kalorien und Makros wachsen mit der Menge mit ([withAmount]). Eingetragen wird eine Kopie mit neuer ID,
 * dem gewählten Tag und der jetzigen Uhrzeit. Das Sheet schließt sich vor [onLog] selbst.
 *
 * @param remainingKcal was an diesem Tag noch bis zum Ziel fehlt, für "Danach noch … kcal". null: kein Hinweis.
 */
@Composable
fun RelogSheet(
    entry: FoodEntry,
    date: LocalDate,
    initialSlot: MealSlot,
    onLog: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
    remainingKcal: Double? = null,
) {
    var amountText by rememberSaveable { mutableStateOf(NutritionFormat.number(entry.amount, 2)) }
    var slot by rememberSaveable { mutableStateOf(initialSlot) }
    val amount = parseAmount(amountText, entry.unit)
    val preview = amount?.let { entry.withAmount(it) }

    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = entry.name, subtitle = NutritionFormat.entryDetail(entry))
        Column(Modifier.padding(horizontal = 24.dp)) {
            AmountInput(
                text = amountText,
                onTextChange = { amountText = it },
                unit = entry.unit,
                isError = amountText.isNotBlank() && amount == null,
                onStep = { direction -> amountText = NutritionFormat.number(stepAmount(amount ?: entry.amount, entry.unit, direction), 2) },
            )
            Spacer(Modifier.height(12.dp))
            ValuesPreview(
                values = preview ?: entry,
                caption = listOfNotNull(
                    NutritionFormat.amount(amount ?: entry.amount, entry.unit),
                    remainingKcal?.let { NutritionFormat.afterLogging(it - (preview ?: entry).kcal) },
                ).joinToString(" · "),
            )
            Spacer(Modifier.height(20.dp))
            SectionLabel("Mahlzeit")
            Spacer(Modifier.height(4.dp))
            FitSegments(options = MealSlot.entries, selected = slot, onSelect = { slot = it }, label = { it.label })
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Eintragen",
                onClick = {
                    val values = preview ?: return@PrimaryButton
                    // Erst wegfahren, dann eintragen: Das Tagebuch darunter ändert sich erst, wenn das Sheet weg ist.
                    dismiss {
                        onLog(values.copy(id = 0, date = date, meal = slot, loggedAt = System.currentTimeMillis()))
                    }
                },
                enabled = preview != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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

// --- gemeinsame Teile ---

/** "Menge" mit großem Zahlenfeld, − und +. Gramm stehen im Feld; "Portionen" passt auf 360 dp nicht zweimal hinein. */
@Composable
private fun AmountInput(
    text: String,
    onTextChange: (String) -> Unit,
    unit: EntryUnit,
    isError: Boolean,
    onStep: (direction: Int) -> Unit,
) {
    SectionLabel(if (unit == EntryUnit.GRAM) "Menge in Gramm" else "Portionen")
    Spacer(Modifier.height(10.dp))
    AmountStepper(
        text = text,
        onTextChange = onTextChange,
        isError = isError,
        onDecrement = { onStep(-1) },
        onIncrement = { onStep(+1) },
        suffix = if (unit == EntryUnit.GRAM) EntryUnit.GRAM.label else null,
    )
}

/** Kalorien und Makros für die gewählte Menge, auf einer warmen Fläche. Die Kalorien zählen beim Ändern mit. */
@Composable
private fun ValuesPreview(values: FoodEntry, caption: String?) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppFieldShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                if (values.estimated) {
                    Text(
                        "≈ ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                AnimatedCount(
                    value = values.kcal.roundToInt(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    " kcal",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            val macros = macroLine(values)
            if (macros != null) {
                Text(
                    macros,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
            }
        }
        if (!caption.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** "45 g Protein", oder null ohne Angabe. Kohlenhydrate und Fett stehen im Tagebuch, hier zählt das Wichtigste. */
private fun macroLine(values: FoodEntry): String? = NutritionFormat.protein(values.proteinG)

/** Höchstens so viel auf einmal: Mehr ist sicher ein Tippfehler (5000 g sind 5 kg). */
private fun maxAmount(unit: EntryUnit): Double = if (unit == EntryUnit.GRAM) 5000.0 else 50.0

/** Die eingetippte Menge, oder null, wenn sie leer, 0 oder unrealistisch groß ist. */
internal fun parseAmount(text: String, unit: EntryUnit): Double? =
    NutritionFormat.parse(text)?.takeIf { it > 0 && it <= maxAmount(unit) }

/**
 * Schrittweite für − und +: halbe Portionen (wie in der Mensa: ½, 1, 1½), Gramm in 10ern.
 * Unter 50 g in 5ern, z. B. für Nüsse oder Proteinpulver, wo 10 g schon viel ausmachen.
 */
internal fun amountStep(unit: EntryUnit, current: Double, direction: Int): Double = when (unit) {
    EntryUnit.PORTION -> 0.5
    EntryUnit.GRAM -> if (current < 50.0 || (direction < 0 && current <= 50.0)) 5.0 else 10.0
}

/**
 * Nächster Wert im Raster der Schrittweite: "+" springt zum nächsten Vielfachen darüber, "−" zum nächsten darunter.
 * Aus 1,3 Portionen wird mit "+" also 1,5 und nicht 1,8. Nie 0 oder weniger: Dann bleibt der Wert, wie er ist.
 */
internal fun stepAmount(current: Double, unit: EntryUnit, direction: Int): Double {
    val step = amountStep(unit, current, direction)
    // Kleine Toleranz: 1,5 / 0,5 ergibt mit Kommazahlen im Computer nicht immer genau 3.
    val steps = current / step
    val next = if (direction > 0) floor(steps + 1e-6) + 1 else ceil(steps - 1e-6) - 1
    val value = Math.round(next * step * 100) / 100.0
    return if (value <= 0) current else value.coerceAtMost(maxAmount(unit))
}
