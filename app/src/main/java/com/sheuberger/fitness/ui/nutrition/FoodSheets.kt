package com.sheuberger.fitness.ui.nutrition

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.data.EntryUnit
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.data.FoodBasis
import com.sheuberger.fitness.data.FoodEntry
import com.sheuberger.fitness.data.FoodSource
import com.sheuberger.fitness.data.MealSlot
import com.sheuberger.fitness.nutrition.Barcode
import com.sheuberger.fitness.nutrition.FoodCatalog
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient
import com.sheuberger.fitness.nutrition.toEntry
import com.sheuberger.fitness.nutrition.units
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppFilterChip
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.ButtonTone
import com.sheuberger.fitness.ui.components.ConfirmDialog
import com.sheuberger.fitness.ui.components.PillSegments
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.components.appTextFieldColors
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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
@OptIn(ExperimentalLayoutApi::class)
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
    val units = remember(food) { food.units() }
    // Die Eingaben gehören zu diesem Lebensmittel, nicht zu seinem genauen Stand. Der Aufrufer reicht nach dem
    // Favorisieren den neuen Stand aus der Datenbank herein; Barcode und ID bleiben dabei gleich, also bleibt auch
    // die getippte Menge stehen. Erst ein anderes Lebensmittel fängt frisch an. Zuerst nach dem Barcode: Ein neues
    // von Open Food Facts hat noch die ID 0 und bekommt seine echte erst beim Speichern, der Barcode bleibt.
    // Aus der Nährwerttabelle (ohne Barcode) gilt der Name, aus demselben Grund.
    val identity = food.barcode ?: if (food.source == FoodSource.MANUAL) food.id.toString() else "${food.source}:${food.name}"
    var savedUnit by rememberSaveable(identity) { mutableStateOf(units.first()) }
    var amountText by rememberSaveable(identity) { mutableStateOf(amountText(defaultAmount(food, units.first()), units.first())) }
    var slot by rememberSaveable(identity) { mutableStateOf(initialSlot) }
    // Der Stern springt beim Tippen sofort um. Kommt der gespeicherte Stand zurück, gilt wieder er: So zeigt der
    // Stern nie etwas anderes als die Datenbank, auch nicht nach zweimal schnellem Tippen.
    var tappedFavorite by remember(identity) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(food.favorite) { tappedFavorite = null }
    val favorite = tappedFavorite ?: food.favorite

    // Wird das Lebensmittel inzwischen anders gespeichert (z. B. ohne Portionsgröße), passt die Einheit evtl. nicht mehr.
    val unit = savedUnit.takeIf { it in units } ?: units.first()
    val amount = NutritionFormat.parse(amountText)?.takeIf { it > 0 && it <= maxAmount(unit) }
    // Nur für die Vorschau; der echte Eintrag entsteht beim Tippen auf "Eintragen" (mit der Uhrzeit von dann).
    val preview = amount?.let { food.toEntry(it, unit, date, slot) }

    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = food.name,
            subtitle = listOfNotNull(food.brand, "${NutritionFormat.kcal(food.kcal)} ${food.basis.label}").joinToString(" · "),
            action = {
                FavoriteToggle(
                    favorite = favorite,
                    onToggle = {
                        val next = !favorite
                        tappedFavorite = next
                        onToggleFavorite(food.copy(favorite = next))
                    },
                )
            },
        )
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    text = if (units == listOf(EntryUnit.PORTION)) "Portionen" else "Menge",
                    modifier = Modifier.weight(1f),
                )
                if (units.size == 2) {
                    PillSegments(
                        options = units,
                        selected = unit,
                        onSelect = { next ->
                            // Dieselbe Menge in der neuen Einheit: 1 Portion zu 30 g wird zu 30 g.
                            amountText = amountText(convert(amount, unit, next, food) ?: defaultAmount(food, next), next)
                            savedUnit = next
                        },
                        label = { if (it == EntryUnit.GRAM) "Gramm" else "Portionen" },
                        modifier = Modifier.width(184.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            AmountStepper(
                text = amountText,
                onTextChange = { amountText = it },
                isError = amountText.isNotEmpty() && amount == null,
                onDecrement = {
                    val current = NutritionFormat.parse(amountText) ?: return@AmountStepper
                    val next = step(current, stepSize(unit), up = false)
                    if (next > 0) amountText = amountText(next, unit)
                },
                onIncrement = {
                    val next = step(NutritionFormat.parse(amountText) ?: 0.0, stepSize(unit), up = true)
                    if (next <= maxAmount(unit)) amountText = amountText(next, unit)
                },
                // "Portionen" wäre als Einheit im Feld zu breit für die Zahl daneben; die Auswahl darüber sagt es schon.
                suffix = if (unit == EntryUnit.GRAM) "g" else null,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickAmounts(unit).forEach { value ->
                    AppFilterChip(
                        label = if (unit == EntryUnit.GRAM) "${NutritionFormat.number(value, 0)} g" else NutritionFormat.portions(value),
                        selected = amount == value,
                        onClick = { amountText = amountText(value, unit) },
                    )
                }
            }
            val portionG = food.portionG
            if (unit == EntryUnit.PORTION && portionG != null) {
                Text(
                    text = "1 Portion = ${NutritionFormat.number(portionG, 1)} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Mahlzeit")
            Spacer(Modifier.height(10.dp))
            FitSegments(options = MealSlot.entries, selected = slot, onSelect = { slot = it }, label = { it.label })

            Spacer(Modifier.height(20.dp))
            FoodLogSummary(preview, remainingKcal)
            attribution(food.source)?.let { text ->
                Spacer(Modifier.height(8.dp))
                FoodSheetsAttribution(text)
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Eintragen",
                onClick = {
                    val value = amount ?: return@PrimaryButton
                    val entry = food.toEntry(value, unit, date, slot) ?: return@PrimaryButton
                    dismiss { onLog(entry) }
                },
                enabled = preview != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            QuietButton(
                text = "Bearbeiten",
                // Mit dem aktuellen Stern: Sonst würde Speichern im Formular einen eben gesetzten Favoriten zurücksetzen.
                onClick = { dismiss { onEdit(food.copy(favorite = favorite)) } },
                leadingIcon = Icons.Outlined.Edit,
                tone = ButtonTone.Neutral,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    val start = remember(initial, barcode) { FoodFormTexts.of(initial, barcode) }
    val key = "${initial?.id}|${initial?.barcode}|$barcode"
    var name by rememberSaveable(key) { mutableStateOf(start.name) }
    var brand by rememberSaveable(key) { mutableStateOf(start.brand) }
    var basis by rememberSaveable(key) { mutableStateOf(initial?.basis ?: FoodBasis.PER_100G) }
    var kcal by rememberSaveable(key) { mutableStateOf(start.kcal) }
    var protein by rememberSaveable(key) { mutableStateOf(start.protein) }
    var carbs by rememberSaveable(key) { mutableStateOf(start.carbs) }
    var fat by rememberSaveable(key) { mutableStateOf(start.fat) }
    var portion by rememberSaveable(key) { mutableStateOf(start.portion) }
    var code by rememberSaveable(key) { mutableStateOf(start.barcode) }
    // Pflichtfelder melden "leer" erst, wenn man einmal drin war: Ein frisches Formular soll nicht schon rot sein.
    var nameTouched by rememberSaveable(key) { mutableStateOf(false) }
    var kcalTouched by rememberSaveable(key) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val perPortion = basis == FoodBasis.PER_PORTION
    val nameError = if (name.isBlank() && nameTouched) "Bitte einen Namen eingeben." else null
    val kcalError = numberError(kcal, max = if (perPortion) 5000.0 else 900.0, unit = "kcal", required = true, touched = kcalTouched)
    val gramsMax = if (perPortion) 1000.0 else 100.0
    val proteinError = numberError(protein, max = gramsMax, unit = "g")
    val carbsError = numberError(carbs, max = gramsMax, unit = "g")
    val fatError = numberError(fat, max = gramsMax, unit = "g")
    val portionError = numberError(portion, max = 2000.0, unit = "g", min = 1.0)
    val codeError = if (code.isNotEmpty() && Barcode.validate(code) == null) {
        "Ungültiger Barcode: Länge oder Prüfziffer stimmt nicht."
    } else {
        null
    }
    val valid = name.isNotBlank() && kcal.isNotBlank() &&
        listOf(kcalError, proteinError, carbsError, fatError, portionError, codeError).all { it == null }

    AppSheet(onDismiss = onDismiss, dismissOnScrimTap = false) {
        AppSheetHeader(
            title = if (initial == null) "Neues Lebensmittel" else "Lebensmittel bearbeiten",
            subtitle = when (val source = initial?.source?.let(::attribution)) {
                null -> "Werte wie auf der Packung"
                else -> if (initial.edited) "$source, von dir angepasst" else source
            },
            action = { QuietButton("Abbrechen", onClick = { dismiss() }, tone = ButtonTone.Neutral) },
        )
        // Erscheint eine Fehlermeldung unter einem Feld, wächst das Formular weich mit statt zu springen.
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .animateContentSize(tween(200, easing = FastOutSlowInEasing)),
        ) {
            val nameFocus = remember { FocusRequester() }
            LabeledTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                placeholder = "z. B. Haferflocken",
                error = nameError,
                modifier = Modifier
                    .focusRequester(nameFocus)
                    .touchedWhenLeft { nameTouched = true },
            )
            // Neues Lebensmittel (z. B. nach einem Scan ohne Treffer): gleich mit dem Namen loslegen.
            // Hier im Inhalt, weil der Sheet-Inhalt erst einen Frame später erscheint.
            if (initial == null) LaunchedEffect(Unit) { nameFocus.requestFocus() }
            Spacer(Modifier.height(8.dp))
            LabeledTextField(value = brand, onValueChange = { brand = it }, label = "Marke (optional)")

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Nährwerte", Modifier.weight(1f))
                PillSegments(
                    options = FoodBasis.entries,
                    selected = basis,
                    onSelect = { basis = it },
                    label = { it.label },
                    modifier = Modifier.width(200.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = "Kalorien",
                    suffix = "kcal",
                    error = kcalError,
                    modifier = Modifier
                        .weight(1f)
                        .touchedWhenLeft { kcalTouched = true },
                )
                NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), suffix = "g", error = proteinError)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(carbs, { carbs = it }, "Kohlenhydrate", Modifier.weight(1f), suffix = "g", error = carbsError)
                NumberField(fat, { fat = it }, "Fett", Modifier.weight(1f), suffix = "g", error = fatError)
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Portion")
            Spacer(Modifier.height(10.dp))
            NumberField(
                value = portion,
                onValueChange = { portion = it },
                label = "Gramm pro Portion (optional)",
                suffix = "g",
                error = portionError,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (perPortion) {
                    "Damit lässt es sich auch in Gramm eintragen."
                } else {
                    "Damit lässt es sich auch in Portionen eintragen, z. B. 1 Becher."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Barcode")
            Spacer(Modifier.height(10.dp))
            TextField(
                value = code,
                // Nur die Ziffern 0–9 (nicht Char.isDigit: das ließe auch arabische Ziffern durch), höchstens 13.
                onValueChange = { raw -> code = raw.filter { it in '0'..'9' }.take(13) },
                label = { Text("Barcode (optional)") },
                isError = codeError != null,
                supportingText = codeError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = AppFieldShape,
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = "Speichern",
                onClick = {
                    val food = buildFood(
                        initial = initial,
                        texts = start,
                        name = name,
                        brand = brand,
                        basis = basis,
                        kcal = kcal,
                        protein = protein,
                        carbs = carbs,
                        fat = fat,
                        portion = portion,
                        code = code,
                    ) ?: return@PrimaryButton
                    dismiss { onSave(food) }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            )
            if (initial != null && onDelete != null) {
                Spacer(Modifier.height(6.dp))
                QuietButton(
                    text = "Lebensmittel löschen",
                    onClick = { confirmDelete = true },
                    tone = ButtonTone.Destructive,
                    leadingIcon = Icons.Outlined.DeleteOutline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (confirmDelete && initial != null && onDelete != null) {
            ConfirmDialog(
                title = "Lebensmittel löschen?",
                message = "„${initial.name}“ verschwindet aus deinen Lebensmitteln. " +
                    "Was du damit schon eingetragen hast, bleibt im Tagebuch.",
                confirmLabel = "Löschen",
                onDismiss = { confirmDelete = false },
                // Erst schließt der Dialog, dann das Sheet, dann wird gelöscht.
                onConfirm = { dismiss { onDelete(initial) } },
            )
        }
    }
}

// --- Bausteine ---

/** Stern rechts im Kopf. Beim Setzen federt er kurz, wie ein kleines "gemerkt". */
@Composable
private fun FavoriteToggle(favorite: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scale = remember { Animatable(1f) }
    // Nur nach einem Tippen federn, nicht schon beim Öffnen eines Favoriten.
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(favorite) {
        if (tapped && favorite) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .appClickable(
                onClick = {
                    tapped = true
                    onToggle()
                },
                shape = CircleShape,
                pressedScale = 0.9f,
                role = Role.Switch,
                onClickLabel = if (favorite) "Aus den Favoriten nehmen" else "Als Favorit merken",
            )
            .semantics { stateDescription = if (favorite) "Favorit" else "Kein Favorit" },
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = "Favorit",
            tint = if (favorite) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

/** Live-Ergebnis: Kalorien groß, darunter die Makros und was danach vom Tagesziel übrig bleibt. */
@Composable
private fun FoodLogSummary(entry: FoodEntry?, remainingKcal: Double?) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppFieldShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                Text(
                    text = entry?.kcal?.roundToInt()?.toString() ?: "–",
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
            val protein = entry?.proteinG
            if (protein != null) {
                Text(
                    text = NutritionFormat.protein(protein)!!,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
        }
        val macros = listOfNotNull(
            entry?.carbsG?.let { "${NutritionFormat.grams(it)} Kohlenhydrate" },
            entry?.fatG?.let { "${NutritionFormat.grams(it)} Fett" },
        )
        if (macros.isNotEmpty()) {
            Text(
                text = macros.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (entry == null) {
            Text(
                text = "Gib eine Menge ein.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (remainingKcal != null && entry != null) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 1.dp, color = colors.outlineVariant)
            Text(
                text = remainingAfter(remainingKcal - entry.kcal),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface,
            )
        }
    }
}

/** Quellenangabe für Daten von Open Food Facts (Pflicht laut Lizenz). */
@Composable
private fun FoodSheetsAttribution(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Meldet, wenn ein Feld den Fokus hatte und ihn wieder abgibt (nicht schon beim ersten Anzeigen ohne Fokus). */
private fun Modifier.touchedWhenLeft(onLeft: () -> Unit): Modifier = composed {
    // remember: Der Modifier entsteht bei jedem Neuaufbau neu, der Merker muss das überdauern.
    // Ein einfaches Array statt mutableStateOf: Die Oberfläche muss sich bei einer Änderung nicht neu zeichnen.
    val hadFocus = remember { booleanArrayOf(false) }
    onFocusChanged { state ->
        if (hadFocus[0] && !state.isFocused) onLeft()
        hadFocus[0] = state.isFocused
    }
}

// --- Rechnen und Prüfen (ohne Compose) ---

/** "danach noch 1450 kcal" oder "danach 320 kcal über dem Ziel". */
private fun remainingAfter(after: Double): String {
    val rounded = after.roundToInt()
    return when {
        rounded > 0 -> "danach noch $rounded kcal"
        rounded == 0 -> "danach genau am Ziel"
        else -> "danach ${-rounded} kcal über dem Ziel"
    }
}

/** Startmenge: 1 Portion, in Gramm eine Portion (falls bekannt) oder 100 g. */
private fun defaultAmount(food: Food, unit: EntryUnit): Double = when (unit) {
    EntryUnit.PORTION -> 1.0
    EntryUnit.GRAM -> food.portionG ?: 100.0
}

/** Mehr isst niemand auf einmal; größere Zahlen sind Tippfehler. */
private fun maxAmount(unit: EntryUnit): Double = if (unit == EntryUnit.GRAM) 5000.0 else 100.0

private fun stepSize(unit: EntryUnit): Double = if (unit == EntryUnit.GRAM) 10.0 else 0.5

private fun quickAmounts(unit: EntryUnit): List<Double> =
    if (unit == EntryUnit.GRAM) listOf(50.0, 100.0, 150.0, 200.0) else listOf(0.5, 1.0, 1.5, 2.0)

private fun amountText(value: Double, unit: EntryUnit): String =
    NutritionFormat.number(value, if (unit == EntryUnit.GRAM) 1 else 2)

/**
 * Nächster Schritt auf dem Raster: Von 37 g geht "+" auf 40 g und "−" auf 30 g, von 40 g auf 50 bzw. 30 g.
 * So landet man nach einem krummen Wert gleich wieder auf runden Zahlen.
 */
private fun step(current: Double, size: Double, up: Boolean): Double {
    val steps = current / size
    // Die kleine Toleranz fängt Rundungsreste ab: 0.3 / 0.1 ist in Kommazahlen 2.9999999999999996.
    val next = if (up) floor(steps + 1e-9) + 1 else ceil(steps - 1e-9) - 1
    return next * size
}

/** Rechnet eine Menge in die andere Einheit um. null, wenn es nichts umzurechnen gibt. */
private fun convert(amount: Double?, from: EntryUnit, to: EntryUnit, food: Food): Double? {
    val grams = food.portionG ?: return null
    if (amount == null || from == to) return amount
    val converted = if (to == EntryUnit.GRAM) amount * grams else amount / grams
    // Portionen auf Viertel runden (100 g Joghurt bei 150 g pro Becher sind dann 0,75 statt 0,67), Gramm auf ganze.
    return if (to == EntryUnit.GRAM) converted.roundToInt().toDouble() else (Math.round(converted * 4) / 4.0).takeIf { it > 0 }
}

/**
 * Fehlertext für ein Zahlenfeld, oder null, wenn alles passt. Kurz, weil die Felder nur halb so breit sind.
 * Leere Pflichtfelder melden sich erst, wenn sie [touched] sind.
 */
private fun numberError(
    text: String,
    max: Double,
    unit: String,
    required: Boolean = false,
    touched: Boolean = false,
    min: Double = 0.0,
): String? {
    if (text.isBlank()) return if (required && touched) "Pflichtfeld" else null
    val value = NutritionFormat.parse(text) ?: return "Keine gültige Zahl"
    return when {
        value > max -> "Max. ${NutritionFormat.number(max, 0)} $unit"
        value < min -> "Mind. ${NutritionFormat.number(min, 0)} $unit"
        else -> null
    }
}

/** Die Texte, mit denen das Formular startet. Zum Vergleich beim Speichern: Was gleich blieb, behält den alten Wert. */
private data class FoodFormTexts(
    val name: String,
    val brand: String,
    val kcal: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val portion: String,
    val barcode: String,
) {
    companion object {
        fun of(food: Food?, barcode: String?) = FoodFormTexts(
            name = food?.name.orEmpty(),
            brand = food?.brand.orEmpty(),
            kcal = food?.kcal?.let { NutritionFormat.number(it, 1) }.orEmpty(),
            protein = food?.proteinG?.let { NutritionFormat.number(it, 1) }.orEmpty(),
            carbs = food?.carbsG?.let { NutritionFormat.number(it, 1) }.orEmpty(),
            fat = food?.fatG?.let { NutritionFormat.number(it, 1) }.orEmpty(),
            portion = food?.portionG?.let { NutritionFormat.number(it, 1) }.orEmpty(),
            // Ein neuer Barcode (nach einem Scan) nur, wenn er gültig ist: Er stammt von außen.
            barcode = (food?.barcode ?: barcode?.let { Barcode.validate(it) }).orEmpty(),
        )
    }
}

/**
 * Baut aus den Eingaben das Lebensmittel. null, wenn eine Pflichtangabe fehlt.
 *
 * Unveränderte Zahlenfelder behalten den alten, genauen Wert: Das Formular zeigt 12,35 g als "12,4", und
 * Speichern ohne Änderung soll daraus nicht 12,4 machen. Bei Daten von außen (Open Food Facts, BLV) wird jede echte
 * Änderung als `edited` markiert, damit ein späteres Neuladen sie nicht überschreibt.
 */
private fun buildFood(
    initial: Food?,
    texts: FoodFormTexts,
    name: String,
    brand: String,
    basis: FoodBasis,
    kcal: String,
    protein: String,
    carbs: String,
    fat: String,
    portion: String,
    code: String,
): Food? {
    fun value(text: String, startText: String, old: Double?): Double? =
        if (text == startText) old else NutritionFormat.parse(text)

    val trimmedName = name.trim().ifEmpty { return null }
    val kcalValue = value(kcal, texts.kcal, initial?.kcal) ?: return null
    // copy statt eines neuen Food: Was das Formular nicht zeigt (ID, Quelle, Packungsgröße, Favorit, Ladezeit),
    // bleibt so erhalten, auch wenn Food später weitere Felder bekommt. Ein neues ist "von Hand" (MANUAL).
    val result = (initial ?: Food(name = trimmedName, kcal = kcalValue)).copy(
        name = trimmedName,
        brand = brand.trim().ifEmpty { null },
        barcode = Barcode.validate(code),
        basis = basis,
        kcal = kcalValue,
        proteinG = value(protein, texts.protein, initial?.proteinG),
        carbsG = value(carbs, texts.carbs, initial?.carbsG),
        fatG = value(fat, texts.fat, initial?.fatG),
        portionG = value(portion, texts.portion, initial?.portionG),
    )
    val changed = initial != null && result != initial
    return if (changed && result.source != FoodSource.MANUAL) result.copy(edited = true) else result
}

/** Quellenangabe zu Daten von außen (Pflicht laut Lizenz). null bei selbst eingegebenen Werten. */
private fun attribution(source: FoodSource): String? = when (source) {
    FoodSource.OFF -> OpenFoodFactsClient.ATTRIBUTION
    FoodSource.BLV -> FoodCatalog.BLV_ATTRIBUTION
    FoodSource.MANUAL -> null
}
