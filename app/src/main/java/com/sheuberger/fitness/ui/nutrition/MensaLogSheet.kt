package com.sheuberger.fitness.ui.nutrition

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.mensa.Meal
import com.sheuberger.fitness.nutrition.MensaEstimate
import com.sheuberger.fitness.nutrition.PortionOrigin
import com.sheuberger.fitness.nutrition.PortionValues
import com.sheuberger.fitness.nutrition.publishedPortion
import com.sheuberger.fitness.nutrition.toEntry
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.PillSegments
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.appTextFieldColors
import com.sheuberger.fitness.ui.mensa.place
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import kotlin.math.roundToInt

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
    val currentLoadPortion by rememberUpdatedState(loadPortion)
    // Die Startwerte einer Portion; null, solange sie laden. Das ist ein Blick in die Datenbank und meist schon
    // fertig, bevor das Sheet ganz offen ist. Nicht gespeichert: Nach dem Drehen ist Neuladen schneller als Merken.
    var start by remember(meal) { mutableStateOf<PortionValues?>(null) }
    LaunchedEffect(meal) {
        start = try {
            currentLoadPortion(meal)
        } catch (e: CancellationException) {
            // Sheet zu: Abbruch bleibt Abbruch.
            throw e
        } catch (e: Exception) {
            // Datenbank nicht lesbar: Dann eben ohne das "Gedächtnis", mit den Angaben der Mensa oder der Schätzung.
            meal.publishedPortion() ?: MensaEstimate.guess(meal.name, meal.description)
        }
    }

    // Eingaben überstehen das Drehen. Ein anderes Gericht fängt frisch an.
    var portions by rememberSaveable(meal.id) { mutableStateOf(1.0) }
    var slot by rememberSaveable(meal.id) { mutableStateOf(initialSlot) }
    // null: Das Feld zeigt den Startwert. Sonst steht hier, was du getippt hast.
    var kcalInput by rememberSaveable(meal.id) { mutableStateOf<String?>(null) }
    var proteinInput by rememberSaveable(meal.id) { mutableStateOf<String?>(null) }

    val base = start
    val startKcalText = base?.let { NutritionFormat.number(it.kcal, 0) }.orEmpty()
    val startProteinText = base?.proteinG?.let { NutritionFormat.number(it, 0) }.orEmpty()
    val kcalText = kcalInput ?: startKcalText
    val proteinText = proteinInput ?: startProteinText
    // Wer den Startwert wieder hintippt, hat nichts geändert: Dann gelten weiter die genauen Werte (z. B. 852,4 statt 852).
    val kcalChanged = base != null && kcalText != startKcalText
    val proteinChanged = base != null && proteinText != startProteinText
    val edited = kcalChanged || proteinChanged

    val kcalError = if (kcalChanged) mensaValueError(kcalText, min = 1.0, max = 5000.0, unit = "kcal", required = true) else null
    val proteinError = if (proteinChanged) mensaValueError(proteinText, min = 0.0, max = 300.0, unit = "g") else null
    // Die Werte pro Portion, mit denen eingetragen wird. null: lädt noch oder eine Eingabe passt nicht.
    val values = base?.let {
        if (kcalError != null || proteinError != null) return@let null
        editedPortion(
            start = it,
            kcal = if (kcalChanged) NutritionFormat.parse(kcalText) ?: return@let null else it.kcal,
            // Ein geleertes Protein-Feld heißt: weiß ich nicht (null), nicht 0 g.
            proteinG = if (proteinChanged) NutritionFormat.parse(proteinText) else it.proteinG,
            kcalChanged = kcalChanged,
            proteinChanged = proteinChanged,
        )
    }

    val today = remember { LocalDate.now() }
    // Getippte Werte sollen nicht durch einen versehentlichen Tipp daneben verloren gehen.
    AppSheet(onDismiss = onDismiss, dismissOnScrimTap = !edited) {
        AppSheetHeader(
            title = meal.name,
            // Das Datum nur, wenn es nicht heute ist: z. B. ein Gericht von gestern nachtragen.
            subtitle = listOfNotNull(meal.place(), date.takeIf { it != today }?.let { NutritionFormat.day(it, today) })
                .joinToString(" · "),
        )
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .animateContentSize(tween(200, easing = FastOutSlowInEasing)),
        ) {
            SectionLabel("Portionen")
            Spacer(Modifier.height(10.dp))
            PillSegments(
                options = MensaPortionChoices,
                selected = portions,
                onSelect = { portions = it },
                label = { NutritionFormat.portions(it) },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Pro Portion")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MensaValueField(
                    value = kcalText,
                    onValueChange = { kcalInput = it },
                    label = "Kalorien",
                    suffix = "kcal",
                    error = kcalError,
                    enabled = base != null,
                    modifier = Modifier.weight(1f),
                )
                MensaValueField(
                    value = proteinText,
                    onValueChange = { proteinInput = it },
                    label = "Protein",
                    suffix = "g",
                    error = proteinError,
                    enabled = base != null,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            MensaOriginHint(start = base, edited = edited)

            Spacer(Modifier.height(20.dp))
            SectionLabel("Mahlzeit")
            Spacer(Modifier.height(10.dp))
            FitSegments(options = MealSlot.entries, selected = slot, onSelect = { slot = it }, label = { it.label })

            Spacer(Modifier.height(20.dp))
            MensaLogSummary(portions = portions, values = values, loading = base == null, remainingKcal = remainingKcal)

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Eintragen",
                onClick = {
                    val chosen = values ?: return@PrimaryButton
                    // Jetzt erst der Eintrag: mit der Uhrzeit des Tippens, nicht der des Öffnens.
                    val entry = meal.toEntry(portions, chosen, date, slot)
                    dismiss { onLog(entry) }
                },
                enabled = values != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// --- Bausteine ---

/** Zahlenfeld wie [NumberField], aber abschaltbar, solange die Startwerte laden. */
@Composable
private fun MensaValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    TextField(
        value = value,
        onValueChange = { onValueChange(NutritionFormat.decimalInput(it, maxLength = 6)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = { Text(suffix) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        shape = AppFieldShape,
        colors = appTextFieldColors(),
        modifier = modifier,
    )
}

/** Woher die Werte kommen. Nach einer Änderung: dass die App sie sich für das nächste Mal merkt. */
@Composable
private fun MensaOriginHint(start: PortionValues?, edited: Boolean) {
    val (icon, text) = when {
        start == null -> Icons.Outlined.Info to "Werte werden geladen …"
        edited -> Icons.Outlined.EditNote to "Deine Werte. Beim nächsten Mal startet das Gericht damit."
        start.origin == PortionOrigin.PUBLISHED -> Icons.Outlined.Restaurant to NutritionFormat.origin(start.origin, start.estimated)
        start.origin == PortionOrigin.LAST_TIME -> Icons.Outlined.History to NutritionFormat.origin(start.origin, start.estimated)
        else -> Icons.Outlined.Info to "${NutritionFormat.origin(start.origin, start.estimated)} nach dem Namen des " +
            "Gerichts, denn die Mensa gibt keine Nährwerte an. Weißt du es besser, korrigier die Werte."
    }
    Row(Modifier.padding(start = 4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Live-Ergebnis für alle Portionen: Kalorien groß (mit "≈", wenn geschätzt), Protein, Portionen, was übrig bleibt. */
@Composable
private fun MensaLogSummary(portions: Double, values: PortionValues?, loading: Boolean, remainingKcal: Double?) {
    val colors = MaterialTheme.colorScheme
    val kcal = values?.kcal?.times(portions)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppFieldShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            // TalkBack liest die Zusammenfassung am Stück vor statt Zahl für Zahl.
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                if (values?.estimated == true) {
                    Text(
                        text = "≈ ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(
                    text = kcal?.roundToInt()?.toString() ?: "–",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = " kcal",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            val protein = values?.proteinG
            if (protein != null) {
                Text(
                    text = NutritionFormat.protein(protein * portions)!!,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = when {
                loading -> "Lädt …"
                values == null -> "Bitte die Werte prüfen."
                else -> listOfNotNull(
                    mensaPortionsLabel(portions),
                    values.carbsG?.let { "${NutritionFormat.grams(it * portions)} Kohlenhydrate" },
                    values.fatG?.let { "${NutritionFormat.grams(it * portions)} Fett" },
                ).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if (remainingKcal != null && kcal != null) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 1.dp, color = colors.outlineVariant)
            Text(
                text = mensaRemainingAfter(remainingKcal - kcal),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
        }
    }
}

// --- Rechnen (ohne Compose) ---

/** Die Portionsgrößen der Mensa: halbe, normale, anderthalb und doppelte. */
private val MensaPortionChoices = listOf(0.5, 1.0, 1.5, 2.0)

/**
 * Die Werte nach deiner Korrektur (ohne Korrektur: genau die Startwerte).
 *
 * Was du korrigiert hast, gilt nicht mehr als geschätzt. Geschätzt bleibt nur der Teil, den du nicht angefasst
 * hast: bei einer Schätzung nach dem Namen die Kalorien, bei den Angaben der ETH das berechnete Protein.
 * Kohlenhydrate und Fett stehen nicht im Sheet; sie wachsen mit den Kalorien mit (doppelte Kalorien heißt
 * meist eine doppelt so große Portion), statt bei der alten Portion stehen zu bleiben.
 */
private fun editedPortion(
    start: PortionValues,
    kcal: Double,
    proteinG: Double?,
    kcalChanged: Boolean,
    proteinChanged: Boolean,
): PortionValues {
    val ratio = if (kcalChanged && start.kcal > 0) kcal / start.kcal else 1.0
    val stillEstimated = start.estimated && when (start.origin) {
        PortionOrigin.GUESS -> !kcalChanged
        PortionOrigin.PUBLISHED -> !proteinChanged
        // Vom letzten Mal: Welcher Teil damals geschätzt war, ist nicht bekannt. Eine Korrektur gilt fürs Ganze.
        PortionOrigin.LAST_TIME -> !kcalChanged && !proteinChanged
    }
    return start.copy(
        kcal = kcal,
        proteinG = proteinG,
        carbsG = start.carbsG?.times(ratio),
        fatG = start.fatG?.times(ratio),
        estimated = stillEstimated,
    )
}

/** Fehlertext für ein Feld, oder null. Kurz, weil die Felder nur halb so breit sind. */
private fun mensaValueError(text: String, min: Double, max: Double, unit: String, required: Boolean = false): String? {
    if (text.isBlank()) return if (required) "Pflichtfeld" else null
    val value = NutritionFormat.parse(text) ?: return "Keine gültige Zahl"
    return when {
        value > max -> "Max. ${NutritionFormat.number(max, 0)} $unit"
        value < min -> "Mind. ${NutritionFormat.number(min, 0)} $unit"
        else -> null
    }
}

/** "½ Portion", "1 Portion", "1½ Portionen" */
private fun mensaPortionsLabel(portions: Double): String {
    val amount = NutritionFormat.portions(portions)
    return if (portions <= 1.0) "$amount Portion" else "$amount Portionen"
}

/** "danach noch 1450 kcal" oder "danach 320 kcal über dem Ziel". */
private fun mensaRemainingAfter(after: Double): String {
    val rounded = after.roundToInt()
    return when {
        rounded > 0 -> "danach noch $rounded kcal"
        rounded == 0 -> "danach genau am Ziel"
        else -> "danach ${-rounded} kcal über dem Ziel"
    }
}
