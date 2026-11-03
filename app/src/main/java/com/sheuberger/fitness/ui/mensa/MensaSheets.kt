package com.sheuberger.fitness.ui.mensa

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.mensa.Mensa
import com.sheuberger.fitness.mensa.MensaPreferences
import com.sheuberger.fitness.mensa.PriceGroup
import com.sheuberger.fitness.mensa.RankedMeal
import com.sheuberger.fitness.ui.components.AppFieldShape
import com.sheuberger.fitness.ui.components.AppFilterChip
import com.sheuberger.fitness.ui.components.AppSheet
import com.sheuberger.fitness.ui.components.AppSheetHeader
import com.sheuberger.fitness.ui.components.CircleIconButton
import com.sheuberger.fitness.ui.components.PillSegments
import com.sheuberger.fitness.ui.components.QuietButton
import com.sheuberger.fitness.ui.components.SectionLabel
import kotlin.math.roundToInt

/** Details zu einem Gericht: Zutaten, alle Nährwerte und alle Preise. */
@Composable
fun MealDetailSheet(meal: RankedMeal, group: PriceGroup, onDismiss: () -> Unit) {
    val m = meal.meal
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = m.name,
            subtitle = "${m.place()} · ${m.time.label}${m.dietTag()}",
            action = { QuietButton("Fertig", onClick = { dismiss() }) },
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            if (m.description.isNotEmpty()) {
                Text(m.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (meal.alsoAt.isNotEmpty()) {
                if (m.description.isNotEmpty()) Spacer(Modifier.height(8.dp))
                Text(
                    "Gibt es auch in: ${meal.alsoAt.joinToString(", ") { it.title }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (m.description.isNotEmpty() || meal.alsoAt.isNotEmpty()) Spacer(Modifier.height(20.dp))

            val n = m.nutrition
            SectionLabel(if (n != null) "Nährwerte pro Portion" else "Nährwerte")
            Spacer(Modifier.height(8.dp))
            if (n != null) {
                InfoTable(
                    buildList {
                        add("Kalorien" to "${n.kcal.roundToInt()} kcal")
                        add("Protein" to MensaFormat.grams(n.proteinG))
                        n.carbsG?.let { add("Kohlenhydrate" to MensaFormat.grams(it)) }
                        n.fatG?.let { add("Fett" to MensaFormat.grams(it)) }
                        n.weightG?.let { add("Portion" to "${it.roundToInt()} g") }
                        add("Protein pro Franken" to "${MensaFormat.decimal(meal.proteinPerFranc)} g")
                    },
                )
                if (n.estimated) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Die ETH gibt Protein, Kohlenhydrate und Fett pro 100 g an und die Kalorien pro Portion. " +
                            "Daraus sind Portionsgewicht und Werte pro Portion berechnet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Diese Mensa gibt für das Gericht keine Nährwerte an.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Preise")
            Spacer(Modifier.height(8.dp))
            InfoTable(
                PriceGroup.entries.mapNotNull { g -> m.price(g)?.let { g.label to MensaFormat.chf(it) } },
                highlight = m.price(group)?.let { group.label },
            )
        }
    }
}

/** Einstellungen der Rangliste: Preiskategorie, Budget und welche Mensen mitzählen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MensaSettingsSheet(
    prefs: MensaPreferences,
    onChange: ((MensaPreferences) -> MensaPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = "Einstellungen",
            subtitle = "Für die Mensa-Rangliste",
            action = { QuietButton("Fertig", onClick = { dismiss() }) },
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            SectionLabel("Preiskategorie")
            Spacer(Modifier.height(8.dp))
            PillSegments(
                options = PriceGroup.entries,
                selected = prefs.group,
                onSelect = { g -> onChange { it.copy(group = g) } },
                label = {
                    when (it) {
                        PriceGroup.Students -> "Studierende"
                        PriceGroup.Staff -> "Personal"
                        PriceGroup.External -> "Extern"
                    }
                },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Budget pro Gericht")
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppFieldShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(6.dp),
            ) {
                CircleIconButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Budget senken",
                    onClick = { onChange { it.copy(budget = stepBudget(it.budget, -1)) } },
                    enabled = prefs.budget > MensaPreferences.MIN_BUDGET,
                    size = 48.dp,
                )
                Text(
                    text = MensaFormat.chf(prefs.budget),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Budget erhöhen",
                    onClick = { onChange { it.copy(budget = stepBudget(it.budget, +1)) } },
                    enabled = prefs.budget < MensaPreferences.MAX_BUDGET,
                    size = 48.dp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Nur Gerichte bis zu diesem Preis kommen in die Rangliste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Mensen")
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mensa.entries.forEach { mensa ->
                    val selected = mensa in prefs.mensas
                    AppFilterChip(
                        label = mensa.title,
                        selected = selected,
                        onClick = {
                            onChange {
                                val next = if (selected) it.mensas - mensa else it.mensas + mensa
                                // Mindestens eine Mensa muss übrig bleiben, sonst wäre die Liste immer leer.
                                if (next.isEmpty()) it else it.copy(mensas = next)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Budget in 50-Rappen-Schritten, auf den nächsten Schritt gerundet. */
internal fun stepBudget(current: Double, direction: Int): Double {
    val step = MensaPreferences.BUDGET_STEP
    val next = (Math.round(current / step) + direction) * step
    return next.coerceIn(MensaPreferences.MIN_BUDGET, MensaPreferences.MAX_BUDGET)
}

/** Zweispaltige Tabelle auf einer warmen Fläche, wie die Statistik-Karten. */
@Composable
private fun InfoTable(rows: List<Pair<String, String>>, highlight: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppFieldShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            val strong = label == highlight
            Row(Modifier.padding(vertical = 12.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (strong) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (strong) colors.primary else colors.onSurface,
                )
            }
        }
    }
}
