package com.sheuberger.fitness.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.components.SectionLabel

/**
 * Dialog zum Anlegen oder Bearbeiten einer Übung: Name plus Muskelgruppe.
 *
 * [existingNames] enthält die Namen aller *anderen* Übungen in Kleinbuchstaben.
 * [rejectedName] ist ein Name, den das Repository abgelehnt hat (falls die Liste kurz veraltet war).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseDialog(
    title: String,
    confirmLabel: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, muscleGroup: String?) -> Unit,
    initialName: String = "",
    initialGroup: String? = null,
    rejectedName: String? = null,
) {
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    var group by rememberSaveable { mutableStateOf(initialGroup) }
    val focusRequester = remember { FocusRequester() }

    val trimmed = name.text.trim()
    val duplicate = trimmed.lowercase() in existingNames || trimmed.equals(rejectedName, ignoreCase = true)
    val error = if (trimmed.isNotEmpty() && duplicate) "Eine Übung mit diesem Namen gibt es schon." else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("z. B. Bankdrücken") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Muskelgruppe")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    MuscleGroups.forEach { option ->
                        // Nochmal tippen hebt die Auswahl wieder auf.
                        MuscleGroupChip(
                            label = option,
                            selected = group == option,
                            onClick = { group = if (group == option) null else option },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed, group) },
                enabled = trimmed.isNotEmpty() && error == null,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Ruhiger Filter-Chip: grau, ausgewählt im Akzent-Container. Ohne Rahmen, wie bei Apple Fitness. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MuscleGroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
    )
}
