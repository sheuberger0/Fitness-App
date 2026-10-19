package com.sheuberger.fitness.ui.exercises

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.data.MuscleGroups
import com.sheuberger.fitness.ui.components.AppDialog
import com.sheuberger.fitness.ui.components.AppDialogButtons
import com.sheuberger.fitness.ui.components.AppDialogState
import com.sheuberger.fitness.ui.components.AppDialogTitle
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppFilterChip
import com.sheuberger.fitness.ui.components.SectionLabel
import com.sheuberger.fitness.ui.components.appTextFieldColors
import com.sheuberger.fitness.ui.components.rememberAppDialogState

/**
 * Dialog zum Anlegen oder Bearbeiten einer Übung: Name plus Muskelgruppe.
 *
 * [existingNames] enthält die Namen aller *anderen* Übungen in Kleinbuchstaben.
 * [rejectedName] ist ein Name, den das Repository abgelehnt hat (falls die Liste kurz veraltet war).
 *
 * [onConfirm] läuft sofort beim Tippen auf den Bestätigen-Button, der Dialog bleibt dabei offen.
 * Geschlossen wird er vom Aufrufer über [state] (`state.dismiss { ... }`): So kann z. B. "Speichern"
 * erst abwarten, ob der Name angenommen wurde, und den Dialog nur dann animiert schließen.
 * Abbrechen, Zurück und Tippen daneben schließen ihn selbst und rufen danach [onDismiss] auf.
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
    state: AppDialogState = rememberAppDialogState(),
) {
    // Der Zustand lebt hier und nicht im Dialog-Inhalt, damit er beim Neuaufbau des Inhalts erhalten bleibt.
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    var group by rememberSaveable { mutableStateOf(initialGroup) }

    val trimmed = name.text.trim()
    val duplicate = trimmed.lowercase() in existingNames || trimmed.equals(rejectedName, ignoreCase = true)
    val error = if (trimmed.isNotEmpty() && duplicate) "Eine Übung mit diesem Namen gibt es schon." else null

    AppDialog(onDismiss = onDismiss, state = state) {
        AppDialogTitle(title)
        Spacer(Modifier.height(20.dp))

        val focusRequester = remember { FocusRequester() }
        // Erscheint der Fehlerhinweis unter dem Feld, wächst der Dialog weich mit statt zu springen.
        Column(Modifier.animateContentSize(tween(200, easing = FastOutSlowInEasing))) {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("z. B. Bankdrücken") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = AppFieldShape,
                colors = appTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        // Hier im Inhalt (nicht außerhalb), weil der Dialog-Inhalt erst einen Frame später erscheint.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Muskelgruppe")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MuscleGroups.forEach { option ->
                // Nochmal tippen hebt die Auswahl wieder auf.
                AppFilterChip(
                    label = option,
                    selected = group == option,
                    onClick = { group = if (group == option) null else option },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        AppDialogButtons(
            confirmLabel = confirmLabel,
            onConfirm = { onConfirm(trimmed, group) },
            onCancel = { dismiss() },
            confirmEnabled = trimmed.isNotEmpty() && error == null,
        )
    }
}
