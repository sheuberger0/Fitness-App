package com.sheuberger.fitness.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sheuberger.fitness.nutrition.NutritionGoals
import com.sheuberger.fitness.nutrition.TargetSuggestion
import com.sheuberger.fitness.nutrition.TrendSummary
import com.sheuberger.fitness.ui.components.AppCard
import com.sheuberger.fitness.ui.components.Format
import com.sheuberger.fitness.ui.components.PrimaryButton
import com.sheuberger.fitness.ui.components.SecondaryButton
import com.sheuberger.fitness.ui.components.SectionLabel
import java.time.LocalDate
import kotlin.math.round

/**
 * Was die letzten Wochen sagen: Durchschnittsgewicht, wie schnell es sich ändert, wie viel du im Schnitt isst.
 * Mit genug Daten schlägt [com.sheuberger.fitness.nutrition.Trend] ein neues Kalorienziel vor, das man hier
 * übernimmt oder verschiebt. Fehlen Daten, steht da, was noch fehlt.
 */
@Composable
internal fun TrendCard(
    trend: TrendSummary,
    goals: NutritionGoals,
    today: LocalDate,
    onAccept: (TargetSuggestion) -> Unit,
    onPostpone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        SectionLabel("Trend")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrendTile(
                label = "Ø Gewicht",
                // Eine Nachkommastelle reicht: Das Gewicht schwankt ohnehin von Tag zu Tag.
                value = trend.avgWeightKg?.let { Format.kg(round(it * 10) / 10) } ?: "–",
                caption = "7 Tage",
                modifier = Modifier.weight(1f),
            )
            TrendTile(
                label = "Pro Woche",
                value = trend.weeklyChangeKg?.let(NutritionFormat::signedKg) ?: "–",
                caption = "Ziel ${NutritionFormat.signed(goals.weeklyGoalKg)}",
                modifier = Modifier.weight(1f),
            )
            TrendTile(
                label = "Ø Kalorien",
                value = trend.avgIntakeKcal?.let(NutritionFormat::kcal) ?: "–",
                caption = if (trend.loggedDays == 1) "an 1 Tag" else "an ${trend.loggedDays} Tagen",
                modifier = Modifier.weight(1f),
            )
        }

        val hint = trendHint(trend, goals, today)
        if (hint != null) {
            Spacer(Modifier.height(14.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }

        val suggestion = trend.suggestion
        if (suggestion != null) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Vorschlag: Ziel auf ${suggestion.newTargetKcal} kcal (${NutritionFormat.signed(suggestion.deltaKcal)})",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = NutritionFormat.suggestionReason(suggestion),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(text = "Später", onClick = onPostpone, compact = true, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Übernehmen",
                    onClick = { onAccept(suggestion) },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Ein Satz dazu, was noch fehlt oder wie es steht. Die Zahlen 3 und 5 sind die Mindestwerte aus
 * [com.sheuberger.fitness.nutrition.Trend] (Wiegungen pro Woche, Tage mit Einträgen).
 */
internal fun trendHint(trend: TrendSummary, goals: NutritionGoals, today: LocalDate): String? {
    val review = goals.lastReview
    return when {
        trend.weeklyChangeKg == null ->
            "Wiege dich an mindestens 3 Tagen pro Woche, dann siehst du, wie schnell sich dein Gewicht ändert."
        trend.loggedDays < 5 ->
            "Trag an mindestens 5 von 7 Tagen ein, dann prüft die App, ob dein Kalorienziel passt."
        trend.suggestion != null -> null
        // Nach einem Vorschlag hat der Körper 14 Tage Zeit, sich auf das neue Ziel einzustellen.
        review != null && today.isBefore(review.plusDays(14)) ->
            "Nächste Prüfung deines Ziels frühestens am ${Format.shortDate(review.plusDays(14))}."
        else -> "Du liegst im Plan, dein Kalorienziel passt."
    }
}

/** Kleine Kennzahl wie [com.sheuberger.fitness.ui.components.StatTile], mit einer Zeile Zusammenhang darunter. */
@Composable
private fun TrendTile(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
