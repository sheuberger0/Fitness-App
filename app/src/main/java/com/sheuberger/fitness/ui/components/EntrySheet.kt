package com.sheuberger.fitness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.round

/**
 * Bottom Sheet zum Eintragen oder Bearbeiten eines Gewichts.
 * Wird für Übungen (mit Wiederholungen) und für das Körpergewicht (ohne) verwendet.
 * Gleitet weich herein; "Speichern" und "Löschen" lassen das Sheet erst wegfahren und rufen dann den Aufrufer.
 */
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
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var weightText by rememberSaveable { mutableStateOf(initialWeight?.let(Format::number) ?: "") }
    var repsText by rememberSaveable { mutableStateOf(initialReps?.toString() ?: "") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val weight = Format.parseWeight(weightText)
    val repsValid = repsText.isBlank() || Format.parseReps(repsText) != null

    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = title)
        Column(Modifier.padding(horizontal = 24.dp)) {
            SectionLabel("Gewicht")
            Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(20.dp))
                SectionLabel("Wiederholungen (optional)")
                Spacer(Modifier.height(10.dp))
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

            Spacer(Modifier.height(20.dp))
            SectionLabel("Datum")
            Spacer(Modifier.height(10.dp))
            DateRow(date = date, onClick = { showDatePicker = true })

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = "Speichern",
                onClick = {
                    val value = weight ?: return@PrimaryButton
                    val reps = if (showReps) Format.parseReps(repsText) else null
                    // Erst wegfahren, dann speichern: Die Liste darunter ändert sich erst, wenn das Sheet weg ist.
                    dismiss { onSave(date, value, reps) }
                },
                enabled = weight != null && repsValid,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onDelete != null) {
                Spacer(Modifier.height(6.dp))
                QuietButton(
                    text = "Eintrag löschen",
                    onClick = { dismiss(onDelete) },
                    tone = ButtonTone.Destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
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
}

/** Datumszeile: tippen öffnet den Kalender. */
@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .appClickable(onClick = onClick, shape = AppFieldShape, pressedScale = 0.98f, onClickLabel = "Datum ändern")
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(
            Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        CircleIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Weniger",
            onClick = onDecrement,
            size = 52.dp,
        )
        TextField(
            value = text,
            onValueChange = { new -> onTextChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
            singleLine = true,
            isError = isError,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            suffix = { Text(suffix) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            shape = AppFieldShape,
            colors = appTextFieldColors(),
            modifier = Modifier.weight(1f),
        )
        CircleIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Mehr",
            onClick = onIncrement,
            size = 52.dp,
        )
    }
}

/** Kalender zum Wählen des Datums, in den Farben der App und mit Pillen-Buttons. */
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
    val colorScheme = MaterialTheme.colorScheme
    val colors = DatePickerDefaults.colors(
        containerColor = colorScheme.surfaceContainerLow,
        titleContentColor = colorScheme.onSurfaceVariant,
        headlineContentColor = colorScheme.onSurface,
        weekdayContentColor = colorScheme.onSurfaceVariant,
        selectedDayContainerColor = colorScheme.primary,
        selectedDayContentColor = colorScheme.onPrimary,
        todayContentColor = colorScheme.primary,
        todayDateBorderColor = colorScheme.primary,
        selectedYearContainerColor = colorScheme.primary,
        selectedYearContentColor = colorScheme.onPrimary,
        dividerColor = colorScheme.outlineVariant,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PrimaryButton(
                text = "OK",
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null,
                compact = true,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, bottom = 4.dp),
            )
        },
        dismissButton = {
            QuietButton(
                text = "Abbrechen",
                onClick = onDismiss,
                tone = ButtonTone.Neutral,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        },
        colors = colors,
    ) {
        DatePicker(state = state, showModeToggle = false, colors = colors)
    }
}

private fun roundToStep(value: Double, step: Double): Double = round(value / step) * step
