package com.sheuberger.fitness.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.round

/**
 * Bottom Sheet zum Eintragen oder Bearbeiten eines Gewichts.
 * Wird für Übungen (mit Wiederholungen) und für das Körpergewicht (ohne) verwendet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEntrySheet(
    title: String,
    initialDate: LocalDate,
    initialWeight: Double?,
    step: Double,
    onDismiss: () -> Unit,
    onSave: (date: LocalDate, weightKg: Double, reps: Int?) -> Unit,
    showReps: Boolean = false,
    initialReps: Int? = null,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var date by rememberSaveable { mutableStateOf(initialDate) }
    var weightText by rememberSaveable { mutableStateOf(initialWeight?.let(Format::number) ?: "") }
    var repsText by rememberSaveable { mutableStateOf(initialReps?.toString() ?: "") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val weight = Format.parseWeight(weightText)
    val repsValid = repsText.isBlank() || Format.parseReps(repsText) != null

    // Sheet erst animiert schließen, dann den Aufrufer informieren.
    fun close(after: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { after() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            SectionLabel("Gewicht")
            Spacer(Modifier.height(8.dp))
            Stepper(
                text = weightText,
                onTextChange = { weightText = it },
                suffix = "kg",
                keyboardType = KeyboardType.Decimal,
                isError = weightText.isNotBlank() && weight == null,
                onDecrement = {
                    val current = Format.parseWeight(weightText) ?: return@Stepper
                    val next = roundToStep(current - step, step)
                    if (next > 0) weightText = Format.number(next)
                },
                onIncrement = {
                    val current = Format.parseWeight(weightText) ?: 0.0
                    weightText = Format.number(roundToStep(current + step, step))
                },
            )

            if (showReps) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Wiederholungen (optional)")
                Spacer(Modifier.height(8.dp))
                Stepper(
                    text = repsText,
                    onTextChange = { repsText = it.filter(Char::isDigit).take(3) },
                    suffix = "Wdh.",
                    keyboardType = KeyboardType.Number,
                    isError = !repsValid,
                    onDecrement = {
                        val current = repsText.toIntOrNull() ?: return@Stepper
                        repsText = if (current <= 1) "" else (current - 1).toString()
                    },
                    onIncrement = {
                        val current = repsText.toIntOrNull() ?: 0
                        repsText = (current + 1).coerceAtMost(999).toString()
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Datum")
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = when (date) {
                            LocalDate.now() -> "Heute, ${Format.date(date)}"
                            LocalDate.now().minusDays(1) -> "Gestern, ${Format.date(date)}"
                            else -> Format.weekdayDate(date)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Datum ändern",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val value = weight ?: return@Button
                    val reps = if (showReps) Format.parseReps(repsText) else null
                    close { onSave(date, value, reps) }
                },
                enabled = weight != null && repsValid,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Speichern", style = MaterialTheme.typography.titleMedium)
            }
            if (onDelete != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { close(onDelete) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Eintrag löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPick = {
                date = it
                showDatePicker = false
            },
        )
    }
}

/** Großes Zahlenfeld mit − und + daneben, wie in gängigen Trainings-Apps. */
@Composable
private fun Stepper(
    text: String,
    onTextChange: (String) -> Unit,
    suffix: String,
    keyboardType: KeyboardType,
    isError: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(onClick = onDecrement, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Weniger")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new -> onTextChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
            singleLine = true,
            isError = isError,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            suffix = { Text(suffix, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onIncrement, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Mehr")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // Der DatePicker rechnet in Millisekunden (UTC). Wir wandeln von/zu LocalDate um.
    val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun roundToStep(value: Double, step: Double): Double = round(value / step) * step
