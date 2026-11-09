package com.sheuberger.fitness.ui.workouts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppMenu
import com.sheuberger.fitness.ui.components.AppMenuDivider
import com.sheuberger.fitness.ui.components.AppMenuItem
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.ButtonTone
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.CircleIconButtonStyle
import com.sheuberger.fitness.ui.components.FadeSwap
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.appClickable
import com.sheuberger.fitness.ui.theme.LocalAccentFill

// Die Trainingsreihenfolge in der Workout-Liste: die Karte "Als Nächstes" bzw. "Heute" ganz oben, der Hinweis
// zum Einrichten und die Sheets zum Festlegen der Reihenfolge und zum Wählen eines anderen Workouts.

/**
 * Karte ganz oben in der Liste: welches Workout als Nächstes dran ist, bzw. am Trainingstag "Heute" mit dem
 * Fortschritt. Ein Tipp öffnet das Workout (wie bei den anderen Karten), das Menü bietet Überspringen & Co.
 */
@Composable
internal fun NextWorkoutCard(
    next: NextWorkout,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    onChooseOther: () -> Unit,
    onEditOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    AppCard(
        onClick = onOpen,
        onClickLabel = "Workout öffnen",
        // Wie die Workout-Karten: rechts wenig Abstand, weil der runde Menü-Button eigene Luft mitbringt.
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, bottom = 20.dp, end = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (next.trainedToday) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (next.trainedToday) "Heute" else "Als Nächstes",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.weight(1f),
            )
            Box {
                CircleIconButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "Optionen",
                    onClick = { menuOpen = true },
                    style = CircleIconButtonStyle.Plain,
                    size = 40.dp,
                )
                AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (next.trainedToday) {
                        AppMenuItem(
                            text = "Heute war ein anderes Workout",
                            icon = Icons.Outlined.SwapHoriz,
                            onClick = {
                                menuOpen = false
                                onChooseOther()
                            },
                        )
                    } else {
                        AppMenuItem(
                            text = "Überspringen",
                            icon = Icons.Outlined.SkipNext,
                            onClick = {
                                menuOpen = false
                                onSkip()
                            },
                        )
                        AppMenuItem(
                            text = "Anderes Workout wählen",
                            icon = Icons.Outlined.SwapHoriz,
                            onClick = {
                                menuOpen = false
                                onChooseOther()
                            },
                        )
                    }
                    AppMenuDivider()
                    AppMenuItem(
                        text = "Reihenfolge bearbeiten",
                        icon = Icons.Outlined.Edit,
                        onClick = {
                            menuOpen = false
                            onEditOrder()
                        },
                    )
                }
            }
        }

        Column(Modifier.padding(end = 12.dp)) {
            // Beim Überspringen blendet der neue Name weich über, statt hart zu wechseln.
            FadeSwap(targetState = next, contentKey = { it.workout.workout.id to it.trainedToday }) { shown ->
                Column {
                    Text(
                        text = shown.workout.workout.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detailLine(shown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            RotationStrip(
                steps = next.steps,
                currentId = next.workout.workout.id,
                done = next.trainedToday,
            )
            val lastName = next.lastWorkoutName
            val lastDate = next.lastDate
            if (!next.trainedToday && lastName != null && lastDate != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Zuletzt: $lastName · ${Format.relative(lastDate)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Zweite Zeile der Karte: am Trainingstag der Fortschritt, sonst die Übungen des Workouts. */
private fun detailLine(next: NextWorkout): String {
    val names = next.workout.exerciseNames
    return when {
        names.isEmpty() -> "Noch keine Übungen hinzugefügt"
        next.trainedToday && next.doneToday >= names.size ->
            if (names.size == 1) "Übung eingetragen" else "Alle ${names.size} Übungen eingetragen"
        next.trainedToday -> "${next.doneToday} von ${names.size} Übungen eingetragen"
        else -> "${Format.exerciseCount(names.size)} · ${names.joinToString(" · ")}"
    }
}

/**
 * Die ganze Reihenfolge als Streifen aus Pillen, das aktuelle Workout in Akzentfarbe. So sieht man auf einen
 * Blick, was danach kommt. Bei mehr als fünf Workouts passen die Namen nicht mehr nebeneinander, dann stehen
 * dort Nummern.
 */
@Composable
private fun RotationStrip(steps: List<RotationStep>, currentId: Long, done: Boolean) {
    val description = steps.joinToString(prefix = "Reihenfolge: ") { step ->
        if (step.workoutId == currentId) "${step.name} (${if (done) "heute" else "als Nächstes"})" else step.name
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        // TalkBack liest den Streifen als einen Satz vor statt als einzelne Wörter.
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        steps.forEachIndexed { index, step ->
            StripSegment(
                label = if (steps.size > MaxNamedSteps) "${index + 1}" else step.name,
                current = step.workoutId == currentId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val MaxNamedSteps = 5

@Composable
private fun StripSegment(label: String, current: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = LocalAccentFill.current
    // Weich umfärben, wenn beim Überspringen ein anderes Workout dran ist.
    val container by animateColorAsState(
        targetValue = if (current) fill.container else colors.surfaceContainerHigh,
        animationSpec = tween(250),
        label = "stripContainer",
    )
    val content by animateColorAsState(
        targetValue = if (current) fill.content else colors.onSurfaceVariant,
        animationSpec = tween(250),
        label = "stripContent",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(30.dp)
            .background(container, CircleShape)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Hinweis über der Liste, solange keine Reihenfolge festgelegt ist. Lässt sich wegklicken. */
@Composable
internal fun RotationHintCard(onSetUp: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AppCard(
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, bottom = 20.dp, end = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(colors.primaryContainer, CircleShape),
            ) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Welches Workout ist dran?",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Hinweis schließen",
                onClick = onDismiss,
                style = CircleIconButtonStyle.Plain,
                size = 40.dp,
            )
        }
        Column(Modifier.padding(end = 12.dp)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Leg die Reihenfolge deiner Workouts fest. Dann steht hier immer, welches als Nächstes dran " +
                    "ist. Die App erkennt es an deinen Einträgen.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = "Reihenfolge festlegen", onClick = onSetUp, compact = true)
        }
    }
}

/**
 * Sheet zum Festlegen der Reihenfolge: Die Workouts in der Reihenfolge antippen, in der man sie trainiert,
 * nochmal tippen nimmt eines heraus. Jede Änderung wird sofort gespeichert, "Fertig" schließt nur.
 *
 * Die Liste steht dabei still (älteste Workouts zuerst), damit nichts unter dem Finger wegspringt.
 * Die Auswahl hält das Sheet selbst: So geht bei schnellem Tippen nichts verloren, auch wenn die Datenbank
 * einen Moment braucht.
 */
@Composable
internal fun RotationSheet(
    workouts: List<WorkoutSummary>,
    initial: List<Long>,
    onChange: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val listed = remember(workouts) { workouts.sortedWith(compareBy({ it.workout.createdAt }, { it.workout.id })) }
    var order by remember { mutableStateOf(initial) }
    fun update(value: List<Long>) {
        order = value
        onChange(value)
    }

    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = "Reihenfolge",
            action = { QuietButton("Fertig", onClick = { dismiss() }) },
        )
        Text(
            text = "Tippe deine Workouts in der Reihenfolge an, in der du sie trainierst. Nach dem letzten geht es " +
                "wieder mit dem ersten los. Nochmal tippen nimmt eines heraus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        listed.forEach { summary ->
            val id = summary.workout.id
            val index = order.indexOf(id)
            OrderRow(
                name = summary.workout.name,
                detail = Format.exerciseCount(summary.exerciseNames.size),
                number = if (index >= 0) index + 1 else null,
                onClick = { update(if (index >= 0) order - id else order + id) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        ) {
            QuietButton(
                text = "Alle abwählen",
                onClick = { update(emptyList()) },
                enabled = order.isNotEmpty(),
                tone = ButtonTone.Neutral,
            )
        }
    }
}

/** Zeile im Reihenfolge-Sheet. Rechts die Nummer in der Reihenfolge, bzw. ein leerer Kreis. */
@Composable
private fun OrderRow(name: String, detail: String, number: Int?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Außen 12 dp + innen 12 dp: Text bündig mit dem Titel, die gedrückte Fläche hat trotzdem Luft.
            .padding(horizontal = 12.dp)
            .appClickable(
                onClick = onClick,
                shape = AppFieldShape,
                pressedScale = 0.98f,
                role = Role.Checkbox,
                onClickLabel = if (number != null) "Aus der Reihenfolge nehmen" else "Zur Reihenfolge hinzufügen",
            )
            .semantics { selected = number != null }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        NumberBadge(number)
    }
}

/** Kreis mit der Nummer in der Reihenfolge (Akzentfarbe) oder leer mit feinem Rand. */
@Composable
private fun NumberBadge(number: Int?) {
    val colors = MaterialTheme.colorScheme
    val fill = LocalAccentFill.current
    val container by animateColorAsState(
        targetValue = if (number != null) fill.container else colors.surfaceContainerLow,
        animationSpec = tween(180),
        label = "badgeFill",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .background(container, CircleShape)
            .then(
                if (number == null) Modifier.border(1.5.dp, colors.outline, CircleShape) else Modifier,
            ),
    ) {
        if (number != null) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = fill.content)
        }
    }
}

/**
 * Wählt ein Workout aus der Reihenfolge: "Was ist als Nächstes dran?" bzw. "Was hast du heute trainiert?".
 * Ein Tipp wählt und schließt das Sheet.
 */
@Composable
internal fun RotationPickerSheet(
    title: String,
    subtitle: String,
    rotation: List<WorkoutSummary>,
    selectedId: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = title, subtitle = subtitle)
        Column(
            Modifier
                .padding(bottom = 16.dp)
                .selectableGroup(),
        ) {
            rotation.forEachIndexed { index, summary ->
                val selected = summary.workout.id == selectedId
                PickerRow(
                    position = index + 1,
                    name = summary.workout.name,
                    detail = summary.lastTrained?.let { "Zuletzt ${Format.relative(it)}" } ?: "Noch nicht trainiert",
                    selected = selected,
                    onClick = { dismiss { if (!selected) onPick(summary.workout.id) } },
                )
            }
        }
    }
}

@Composable
private fun PickerRow(position: Int, name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill = LocalAccentFill.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .appClickable(onClick = onClick, shape = AppFieldShape, pressedScale = 0.98f, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (selected) Modifier.background(fill.container, CircleShape)
                    else Modifier.border(1.5.dp, colors.outline, CircleShape),
                ),
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = fill.content, modifier = Modifier.size(16.dp))
            }
        }
    }
}
