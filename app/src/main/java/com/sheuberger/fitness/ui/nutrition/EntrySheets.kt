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
import com.sheuberger.fitness.data.EntrySource
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.nutrition.withAmount
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.ButtonTone
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
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

/**
 * Eintrag im Tagebuch bearbeiten: Menge und Mahlzeit, bei "Schnell"-Einträgen auch Name, Kalorien und Protein.
 * Kalorien und Makros wachsen mit der Menge mit ([withAmount]). Wie im Gewichts-Sheet fährt das Sheet bei
 * "Speichern" und "Eintrag löschen" erst weg und ruft dann den Aufrufer. Gelöscht wird ohne Rückfrage:
 * Der Bildschirm darunter bietet "Rückgängig" an, das ist schneller als ein Dialog und genauso sicher.
 *
 * @param remainingKcal was an diesem Tag noch bis zum Ziel fehlt, mit dem Eintrag, wie er jetzt ist.
 *   Daraus wird "Danach noch … kcal". null: kein Hinweis.
 */
@Composable
fun EntryEditSheet(
    entry: FoodEntry,
    onSave: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
    remainingKcal: Double? = null,
) {
    val quick = entry.source == EntrySource.QUICK
    var amountText by rememberSaveable { mutableStateOf(NutritionFormat.number(entry.amount, 2)) }
    var slot by rememberSaveable { mutableStateOf(entry.meal) }
    // Nur für "Schnell": Die Felder zeigen die Werte für die ganze Menge. Der Standardname bleibt Platzhalter,
    // damit man nicht erst "Schnell eingetragen" löschen muss, um einen eigenen Namen zu tippen.
    var name by rememberSaveable { mutableStateOf(if (entry.name == NutritionFormat.QUICK_NAME) "" else entry.name) }
    var kcalText by rememberSaveable { mutableStateOf(NutritionFormat.number(entry.kcal, 0)) }
    var proteinText by rememberSaveable { mutableStateOf(entry.proteinG?.let { NutritionFormat.number(it, 1) } ?: "") }
    // Für welche Menge die Zahlen in den Feldern gelten: Aus 1 Portion mit 350 kcal werden mit "+" 1,5 Portionen
    // mit 525 kcal. Wer die Kalorien danach selbst ändert, meint die neue Menge.
    var fieldsAmount by rememberSaveable { mutableStateOf(entry.amount) }

    val amount = parseAmount(amountText, entry.unit)
    val updated = amount?.let { editedEntry(entry, it, slot, name, kcalText, proteinText) }

    fun changeAmount(text: String) {
        amountText = text
        val newAmount = parseAmount(text, entry.unit) ?: return
        if (quick && fieldsAmount > 0 && newAmount != fieldsAmount) {
            val factor = newAmount / fieldsAmount
            NutritionFormat.parse(kcalText)?.let { kcalText = NutritionFormat.number(it * factor, 0) }
            NutritionFormat.parse(proteinText)?.let { proteinText = NutritionFormat.number(it * factor, 1) }
        }
        fieldsAmount = newAmount
    }

    // "Danach noch …": den Eintrag, wie er war, wieder herausrechnen und den geänderten dazunehmen.
    val afterChange = remainingKcal?.let { NutritionFormat.afterLogging(it + entry.kcal - (updated ?: entry).kcal) }

    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            // Bei "Schnell" ist der Name selbst ein Feld; oben stünde sonst, was man gerade ändert.
            title = if (quick) "Eintrag bearbeiten" else entry.name,
            subtitle = if (quick) null else origin(entry),
        )
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (quick) {
                QuickFields(
                    kcalText = kcalText,
                    onKcalChange = { kcalText = it },
                    proteinText = proteinText,
                    onProteinChange = { proteinText = it },
                    name = name,
                    onNameChange = { name = it },
                )
                Spacer(Modifier.height(20.dp))
            }
            AmountInput(
                text = amountText,
                onTextChange = ::changeAmount,
                unit = entry.unit,
                isError = amountText.isNotBlank() && amount == null,
                onStep = { direction ->
                    changeAmount(NutritionFormat.number(stepAmount(amount ?: entry.amount, entry.unit, direction), 2))
                },
            )
            if (quick) {
                if (afterChange != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(afterChange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                ValuesPreview(
                    values = updated ?: entry,
                    caption = listOfNotNull(NutritionFormat.amount(amount ?: entry.amount, entry.unit), afterChange)
                        .joinToString(" · "),
                )
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel("Mahlzeit")
            Spacer(Modifier.height(4.dp))
            FitSegments(options = MealSlot.entries, selected = slot, onSelect = { slot = it }, label = { it.label })
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Speichern",
                onClick = {
                    val values = updated ?: return@PrimaryButton
                    // Erst wegfahren, dann speichern: Das Tagebuch darunter ändert sich erst, wenn das Sheet weg ist.
                    dismiss { onSave(values) }
                },
                enabled = updated != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            // Dezent statt roter Fläche: Löschen ist hier nur die Nebenaktion, und es lässt sich rückgängig machen.
            QuietButton(
                text = "Eintrag löschen",
                onClick = { dismiss { onDelete(entry) } },
                tone = ButtonTone.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Der bearbeitete Eintrag, oder null, solange etwas Ungültiges eingetippt ist. ID, Tag und Zeitpunkt bleiben.
 * Bei "Schnell" gelten Kalorien und Protein aus den Feldern (schon für die neue Menge), Kohlenhydrate und Fett
 * wachsen mit der Menge mit. Bei allen anderen rechnet [withAmount] alles aus der Menge.
 */
internal fun editedEntry(
    entry: FoodEntry,
    amount: Double,
    slot: MealSlot,
    name: String,
    kcalText: String,
    proteinText: String,
): FoodEntry? {
    val scaled = entry.withAmount(amount).copy(meal = slot)
    if (entry.source != EntrySource.QUICK) return scaled
    val kcal = parseQuickKcal(kcalText) ?: return null
    val protein = NutritionFormat.parse(proteinText)
    if (quickProteinError(proteinText, protein, kcal) != null) return null
    return scaled.copy(name = name.trim().ifEmpty { NutritionFormat.QUICK_NAME }, kcal = kcal, proteinG = protein)
}

/** Woher ein Eintrag stammt, als Untertitel: "Polymensa · Street". Bei eigenen Lebensmitteln steht der Name allein. */
private fun origin(entry: FoodEntry): String? =
    if (entry.source == EntrySource.MENSA) {
        listOfNotNull(entry.mensa, entry.mensaLine).joinToString(" · ").ifEmpty { "Mensa" }
    } else {
        null
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
