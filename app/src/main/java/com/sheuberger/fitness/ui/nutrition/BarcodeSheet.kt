package com.sheuberger.fitness.ui.nutrition

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheuberger.fitness.data.Food
import com.sheuberger.fitness.nutrition.Barcode
import com.sheuberger.fitness.nutrition.BarcodeScanner
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient
import com.sheuberger.fitness.nutrition.ScanResult
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.ButtonTone
import com.sheuberger.fitness.ui.components.FadeSwap
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SecondaryButton
import com.sheuberger.fitness.ui.components.appTextFieldColors
import com.sheuberger.fitness.ui.components.rememberAppSheetState
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

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
    // Eigener Zustand des Sheets, damit auch die Suche (außerhalb des Inhalts) es schließen kann.
    val sheetState = rememberAppSheetState()
    val context = LocalContext.current
    val scanner = remember(context) { BarcodeScanner(context) }
    val focusManager = LocalFocusManager.current
    // Eine Suche dauert etwas. Bekommt das Sheet inzwischen neue Callbacks, gelten die neuen.
    val currentResolve by rememberUpdatedState(resolve)
    val currentOnFood by rememberUpdatedState(onFood)

    // rememberSaveable: Schritt und Eingabe überstehen das Drehen des Handys (Android baut den Bildschirm dann neu).
    var step by rememberSaveable(stateSaver = BarcodeSheetStepSaver) {
        mutableStateOf<BarcodeSheetStep>(BarcodeSheetStep.Scanning)
    }
    var input by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var scannerOpened by rememberSaveable { mutableStateOf(false) }
    // Daran erkennt das Sheet sein Scan-Ergebnis wieder, auch nach dem Drehen (siehe PendingBarcodeScan).
    val sheetId = rememberSaveable { Random.nextLong() }

    fun search(code: String) {
        // Tastatur weg: Sie würde sonst über dem Ergebnis stehen bleiben.
        focusManager.clearFocus()
        inputError = null
        step = BarcodeSheetStep.Searching(code)
    }

    fun openScanner() {
        step = BarcodeSheetStep.Scanning
        PendingBarcodeScan.start(sheetId)
        try {
            scanner.scan { result -> PendingBarcodeScan.finish(sheetId, result) }
        } catch (e: Exception) {
            // Z. B. ein Handy ohne Google Play-Dienste: nicht abstürzen, dann werden die Ziffern eben eingetippt.
            PendingBarcodeScan.finish(sheetId, ScanResult.Failed(BarcodeScanner.GENERIC))
        }
    }

    fun onScanned(result: ScanResult) {
        when (result) {
            is ScanResult.Code -> search(result.value)
            // Im Scanner zurück: dann eben eintippen. Wer inzwischen schon "Ziffern eintippen" gewählt hat,
            // behält sein Feld, wie es ist.
            ScanResult.Cancelled -> if (step == BarcodeSheetStep.Scanning) step = BarcodeSheetStep.Manual(notice = null)
            is ScanResult.Failed -> step = BarcodeSheetStep.Manual(notice = result.message)
        }
    }

    fun submitInput() {
        // Erst hier prüfen: Ungültiges geht gar nicht erst an die Datenbank oder ins Internet.
        val error = barcodeInputError(input)
        if (error != null) inputError = error else search(input)
    }

    // Beim Öffnen gleich den Scanner starten. Nach dem Drehen nicht nochmal: Er ist dann schon offen oder fertig.
    LaunchedEffect(Unit) {
        if (!scannerOpened) {
            scannerOpened = true
            openScanner()
        } else if (step == BarcodeSheetStep.Scanning && !PendingBarcodeScan.isFor(sheetId)) {
            // Android hat die App beendet, während der Scanner offen war: Dessen Ergebnis kommt nie mehr an.
            step = BarcodeSheetStep.Manual(notice = SCAN_INTERRUPTED)
        }
    }

    // Holt das Ergebnis des Scanners ab, sobald eines ankommt (auch eines, das während des Drehens kam).
    val arrivals = PendingBarcodeScan.arrivals
    LaunchedEffect(arrivals) {
        PendingBarcodeScan.take(sheetId)?.let { onScanned(it) }
    }

    // Die Suche hängt am Schritt: So startet sie nach dem Drehen neu und bricht ab, wenn das Sheet verschwindet.
    val searching = step as? BarcodeSheetStep.Searching
    if (searching != null) {
        LaunchedEffect(searching) {
            val outcome = try {
                currentResolve(searching.code)
            } catch (e: CancellationException) {
                // Sheet geschlossen: Abbruch bleibt Abbruch, keine Fehlermeldung.
                throw e
            } catch (e: Exception) {
                BarcodeOutcome.Failed(searching.code, SEARCH_FAILED)
            }
            when (outcome) {
                // Kommt nur von einem Scan (Getipptes ist schon geprüft): Ziffern zum Korrigieren ins Feld.
                BarcodeOutcome.Invalid -> {
                    input = searching.code.filter { it in '0'..'9' }.take(13)
                    inputError = barcodeInputError(input) ?: "Dieser Code lässt sich nicht verwenden."
                    step = BarcodeSheetStep.Manual(notice = null)
                }
                // Gefunden: Das Sheet bleibt beim Schließen auf "Einen Moment", dann geht es mit dem Eintragen weiter.
                is BarcodeOutcome.Known -> sheetState.dismiss { currentOnFood(outcome.food) }
                is BarcodeOutcome.Found -> sheetState.dismiss { currentOnFood(outcome.food) }
                is BarcodeOutcome.InStore -> step = BarcodeSheetStep.NoMatch(outcome.code, inStore = true)
                is BarcodeOutcome.NotFound -> step = BarcodeSheetStep.NoMatch(outcome.code, inStore = false)
                is BarcodeOutcome.Failed -> step = BarcodeSheetStep.Failed(outcome.code, outcome.message)
            }
        }
    }

    AppSheet(onDismiss = onDismiss, state = sheetState) {
        // Nur ein Wechsel der Art überblendet. Ändert sich bloß der Hinweis, wird ohne Überblenden aktualisiert.
        FadeSwap(targetState = step, contentKey = { it::class }) { shown ->
            when (shown) {
                BarcodeSheetStep.Scanning -> BarcodeScanningStep(
                    onType = { step = BarcodeSheetStep.Manual(notice = null) },
                )
                is BarcodeSheetStep.Manual -> BarcodeManualStep(
                    notice = shown.notice,
                    input = input,
                    // Bei 13 Ziffern geht nichts mehr dazu: Dann gleich sagen, wenn die Prüfziffer nicht passt.
                    error = inputError ?: input.takeIf { it.length == 13 }?.let { barcodeInputError(it) },
                    onInput = { raw ->
                        // Nur 0–9 (nicht Char.isDigit: das ließe auch arabische Ziffern durch), höchstens 13.
                        input = raw.filter { it in '0'..'9' }.take(13)
                        inputError = null
                    },
                    onSearch = { submitInput() },
                    onScan = { openScanner() },
                )
                is BarcodeSheetStep.Searching -> BarcodeSearchingStep(code = shown.code)
                is BarcodeSheetStep.NoMatch -> BarcodeNoMatchStep(
                    code = shown.code,
                    inStore = shown.inStore,
                    onCreate = { sheetState.dismiss { onCreateFood(shown.code) } },
                    onScan = { openScanner() },
                )
                is BarcodeSheetStep.Failed -> BarcodeFailedStep(
                    code = shown.code,
                    message = shown.message,
                    onRetry = { search(shown.code) },
                    onCreate = { sheetState.dismiss { onCreateFood(shown.code) } },
                )
            }
        }
    }
}

// --- Die Schritte ---

@Composable
private fun BarcodeScanningStep(onType: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AppSheetHeader(title = "Barcode scannen", subtitle = "Der Scanner öffnet sich gleich.")
        Column(Modifier.padding(horizontal = 24.dp)) {
            BarcodeStatus(
                icon = Icons.Outlined.QrCodeScanner,
                text = "Halte die Kamera ruhig über den Strichcode. Die App bekommt nur die Ziffern, kein Bild.",
                pulsing = true,
            )
            Spacer(Modifier.height(20.dp))
            SecondaryButton(
                text = "Ziffern eintippen",
                onClick = onType,
                leadingIcon = Icons.Outlined.Dialpad,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BarcodeManualStep(
    notice: String?,
    input: String,
    error: String?,
    onInput: (String) -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth()) {
        AppSheetHeader(title = "Barcode eintippen", subtitle = "Die Ziffern unter dem Strichcode")
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (notice != null) {
                BarcodeStatus(icon = Icons.Outlined.Info, text = notice)
                Spacer(Modifier.height(16.dp))
            }
            TextField(
                value = input,
                onValueChange = onInput,
                label = { Text("Barcode") },
                supportingText = { Text(error ?: "Meist 13 Ziffern, auf kleinen Packungen 8.") },
                isError = error != null,
                singleLine = true,
                // Gleich breite Ziffern mit etwas Luft: So lässt sich Stelle für Stelle mit der Packung vergleichen.
                textStyle = LocalTextStyle.current.copy(letterSpacing = 1.sp, fontFeatureSettings = "tnum"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = AppFieldShape,
                colors = appTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Suchen",
                onClick = onSearch,
                // Ab 8 Ziffern kann es ein Barcode sein (EAN-8); was dann nicht passt, sagt die Prüfung beim Tippen.
                enabled = input.length >= 8 && error == null,
                leadingIcon = Icons.Outlined.Search,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            QuietButton(
                text = "Mit der Kamera scannen",
                onClick = onScan,
                leadingIcon = Icons.Outlined.QrCodeScanner,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    // Gleich lostippen können: Mit dem Fokus erscheint die Zahlentastatur.
    // runCatching: Ist das Feld (z. B. mitten im Überblenden) noch nicht bereit, bleibt es einfach ohne Fokus.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun BarcodeSearchingStep(code: String) {
    Column(Modifier.fillMaxWidth()) {
        AppSheetHeader(title = "Einen Moment …", subtitle = groupedBarcode(code))
        Column(Modifier.padding(horizontal = 24.dp)) {
            BarcodeStatus(
                icon = Icons.Outlined.Search,
                text = "Suche in deinen Lebensmitteln und bei Open Food Facts …",
                pulsing = true,
            )
            BarcodeAttribution()
        }
    }
}

@Composable
private fun BarcodeNoMatchStep(code: String, inStore: Boolean, onCreate: () -> Unit, onScan: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AppSheetHeader(title = if (inStore) "Ladencode" else "Nicht gefunden", subtitle = groupedBarcode(code))
        Column(Modifier.padding(horizontal = 24.dp)) {
            BarcodeStatus(
                icon = if (inStore) Icons.Outlined.Storefront else Icons.Outlined.SearchOff,
                text = if (inStore) {
                    // Ladencodes stecken oft Gewicht oder Preis mit drin; dann hat jede Packung einen anderen.
                    "Diesen Code vergibt das Geschäft selbst, z. B. für Käse oder Fleisch aus der Theke. " +
                        "Darum steht er in keiner Datenbank. Trag die Werte von der Packung einmal ein: " +
                        "Derselbe Code wird danach erkannt."
                } else {
                    "Open Food Facts kennt diesen Code noch nicht. Trag die Werte von der Packung einmal ein: " +
                        "Beim nächsten Scan kennt die App das Lebensmittel."
                },
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Selbst anlegen",
                onClick = onCreate,
                leadingIcon = Icons.Outlined.EditNote,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            QuietButton(
                text = "Anderen Code scannen",
                onClick = onScan,
                tone = ButtonTone.Neutral,
                leadingIcon = Icons.Outlined.QrCodeScanner,
                modifier = Modifier.fillMaxWidth(),
            )
            // Nur wenn Open Food Facts gefragt wurde. Einen Ladencode schickt die App gar nicht erst hin.
            if (!inStore) BarcodeAttribution()
        }
    }
}

@Composable
private fun BarcodeFailedStep(code: String, message: String, onRetry: () -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AppSheetHeader(title = "Suche fehlgeschlagen", subtitle = groupedBarcode(code))
        Column(Modifier.padding(horizontal = 24.dp)) {
            BarcodeStatus(icon = Icons.Outlined.CloudOff, text = message, error = true)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Erneut versuchen",
                onClick = onRetry,
                leadingIcon = Icons.Outlined.Refresh,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Auch ohne Internet weiterkommen: Die Werte stehen ja auf der Packung.
            SecondaryButton(
                text = "Selbst anlegen",
                onClick = onCreate,
                leadingIcon = Icons.Outlined.EditNote,
                modifier = Modifier.fillMaxWidth(),
            )
            BarcodeAttribution()
        }
    }
}

// --- Bausteine ---

/**
 * Meldung mit Symbol im Kreis. [pulsing]: Das Symbol atmet sanft, solange etwas passiert (wie die Platzhalter
 * beim Laden). [error] färbt den Kreis in der Fehlerfarbe.
 */
@Composable
private fun BarcodeStatus(icon: ImageVector, text: String, error: Boolean = false, pulsing: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val circle = if (error) colors.errorContainer else colors.primaryContainer
    val tint = if (error) colors.onErrorContainer else colors.onPrimaryContainer
    val pulse = if (pulsing) {
        rememberInfiniteTransition(label = "barcodePulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "alpha",
        )
    } else {
        null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppFieldShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            // TalkBack liest eine neue Meldung von selbst vor, z. B. "Nicht gefunden" nach der Suche.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                // Nur beim Zeichnen gelesen: Das Pulsieren baut nichts neu auf.
                .graphicsLayer { alpha = pulse?.value ?: 1f }
                .background(circle, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Quellenangabe für Open Food Facts (Pflicht laut Lizenz), unter den Ergebnissen der Suche. */
@Composable
private fun BarcodeAttribution() {
    Text(
        text = OpenFoodFactsClient.ATTRIBUTION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp),
    )
}

// --- Zustand ---

/** Wo das Sheet gerade steht. Jeder Schritt bringt mit, was er anzeigt, damit beim Überblenden nichts springt. */
private sealed interface BarcodeSheetStep {
    /** Der Google-Scanner ist offen oder öffnet sich gleich. */
    data object Scanning : BarcodeSheetStep

    /** Ziffern eintippen; [notice] erklärt, warum (z. B. eine Meldung des Scanners). */
    data class Manual(val notice: String?) : BarcodeSheetStep

    data class Searching(val code: String) : BarcodeSheetStep

    /** Kein Treffer: Ladencode ([inStore]) oder bei Open Food Facts unbekannt. */
    data class NoMatch(val code: String, val inStore: Boolean) : BarcodeSheetStep

    data class Failed(val code: String, val message: String) : BarcodeSheetStep
}

/** Speichert den Schritt als einfache Liste von Texten, denn nur solche Werte überstehen das Drehen. */
private val BarcodeSheetStepSaver = listSaver<BarcodeSheetStep, String?>(
    save = { step ->
        when (step) {
            BarcodeSheetStep.Scanning -> listOf("scanning")
            is BarcodeSheetStep.Manual -> listOf("manual", step.notice)
            is BarcodeSheetStep.Searching -> listOf("searching", step.code)
            is BarcodeSheetStep.NoMatch -> listOf(if (step.inStore) "inStore" else "notFound", step.code)
            is BarcodeSheetStep.Failed -> listOf("failed", step.code, step.message)
        }
    },
    restore = { saved ->
        val code = saved.getOrNull(1).orEmpty()
        when (saved.firstOrNull()) {
            "manual" -> BarcodeSheetStep.Manual(notice = saved.getOrNull(1))
            "searching" -> BarcodeSheetStep.Searching(code)
            "inStore" -> BarcodeSheetStep.NoMatch(code, inStore = true)
            "notFound" -> BarcodeSheetStep.NoMatch(code, inStore = false)
            "failed" -> BarcodeSheetStep.Failed(code, saved.getOrNull(2).orEmpty())
            else -> BarcodeSheetStep.Scanning
        }
    },
)

/**
 * Hält das Ergebnis des Scanners außerhalb der Oberfläche fest. Dreht man das Handy, während der Scanner offen ist,
 * baut Android den Bildschirm neu auf. Das Ergebnis käme sonst bei der alten, unsichtbaren Oberfläche an und
 * ginge verloren. Läuft alles im Hauptthread (Compose und die Rückrufe der Play-Dienste), deshalb ohne Sperren.
 */
private object PendingBarcodeScan {
    /** Das Sheet, dessen Scan gerade läuft. */
    private var waitingFor: Long? = null

    /** Das Sheet, dem [result] gehört. Ein späteres Sheet holt sich so nie ein altes Ergebnis ab. */
    private var owner: Long? = null
    private var result: ScanResult? = null

    /** Zählt die angekommenen Ergebnisse. Compose-State: Das Sheet merkt so sofort, dass eines da ist. */
    var arrivals by mutableIntStateOf(0)
        private set

    fun start(sheetId: Long) {
        waitingFor = sheetId
        owner = null
        result = null
    }

    fun finish(sheetId: Long, scan: ScanResult) {
        if (waitingFor == sheetId) waitingFor = null
        owner = sheetId
        result = scan
        arrivals++
    }

    /** Gibt das Ergebnis für dieses Sheet genau einmal heraus. */
    fun take(sheetId: Long): ScanResult? {
        if (owner != sheetId) return null
        return result.also {
            owner = null
            result = null
        }
    }

    /** Läuft für dieses Sheet noch ein Scan, oder wartet sein Ergebnis? */
    fun isFor(sheetId: Long): Boolean = waitingFor == sheetId || owner == sheetId
}

// --- Texte und Prüfung (ohne Compose) ---

private const val SCAN_INTERRUPTED = "Der Scan wurde unterbrochen. Bitte nochmal scannen oder die Ziffern eintippen."
private const val SEARCH_FAILED = "Die Suche hat nicht geklappt. Bitte nochmal versuchen."

/** Warum [digits] kein gültiger Barcode ist, oder null, wenn er passt. Getrennt nach Länge und Tippfehler. */
private fun barcodeInputError(digits: String): String? = when {
    Barcode.validate(digits) != null -> null
    digits.length !in setOf(8, 12, 13) -> "Ein Barcode hat 8, 12 oder 13 Ziffern."
    // Die letzte Ziffer ist eine Prüfziffer: Passt sie nicht, ist irgendwo eine Ziffer vertippt.
    else -> "Eine Ziffer stimmt nicht. Bitte nochmal mit der Packung vergleichen."
}

/**
 * Wie unter dem Strichcode gedruckt, damit man vergleichen kann: "7 614500 010013" (EAN-13), "9638 5074" (EAN-8),
 * "0 36000 29145 2" (UPC-A). Geschützte Leerzeichen: Der Code bricht nie mitten durch um.
 */
private fun groupedBarcode(code: String): String {
    if (!code.all { it in '0'..'9' }) return code
    val groups = when (code.length) {
        13 -> listOf(code.take(1), code.substring(1, 7), code.substring(7))
        8 -> listOf(code.take(4), code.substring(4))
        12 -> listOf(code.take(1), code.substring(1, 6), code.substring(6, 11), code.substring(11))
        else -> listOf(code)
    }
    return groups.joinToString(" ")
}
