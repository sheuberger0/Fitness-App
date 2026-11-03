package com.sheuberger.fitness.mensa

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MensaPreferences(
    val group: PriceGroup = PriceGroup.Students,
    val budget: Double = DEFAULT_BUDGET,
    val rankBy: RankBy = RankBy.Protein,
    val mensas: Set<Mensa> = Mensa.entries.toSet(),
) {
    companion object {
        const val DEFAULT_BUDGET = 9.50
        const val BUDGET_STEP = 0.50
        const val MIN_BUDGET = 4.0
        const val MAX_BUDGET = 25.0
    }
}

/**
 * Speichert die Einstellungen der Mensa-Rangliste in den SharedPreferences (kleine Schlüssel-Wert-Datei).
 * Absichtlich nicht in der Room-Datenbank: Dafür bräuchte es eine neue Tabelle und eine Migration,
 * für vier Einstellungen wäre das zu viel.
 */
class MensaSettings(context: Context) {
    private val prefs = context.getSharedPreferences("mensa", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val preferences: StateFlow<MensaPreferences> = state.asStateFlow()

    fun update(transform: (MensaPreferences) -> MensaPreferences) {
        val next = transform(state.value)
        state.value = next
        prefs.edit()
            .putString(KEY_GROUP, next.group.name)
            .putFloat(KEY_BUDGET, next.budget.toFloat())
            .putString(KEY_RANK, next.rankBy.name)
            .putStringSet(KEY_MENSAS, next.mensas.map { it.name }.toSet())
            .apply()
    }

    private fun read(): MensaPreferences {
        val defaults = MensaPreferences()
        return MensaPreferences(
            group = enumOrNull<PriceGroup>(prefs.getString(KEY_GROUP, null)) ?: defaults.group,
            budget = prefs.getFloat(KEY_BUDGET, defaults.budget.toFloat()).toDouble(),
            rankBy = enumOrNull<RankBy>(prefs.getString(KEY_RANK, null)) ?: defaults.rankBy,
            mensas = prefs.getStringSet(KEY_MENSAS, null)
                ?.mapNotNull { enumOrNull<Mensa>(it) }?.toSet()?.takeIf { it.isNotEmpty() }
                ?: defaults.mensas,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private companion object {
        const val KEY_GROUP = "price_group"
        const val KEY_BUDGET = "budget"
        const val KEY_RANK = "rank_by"
        const val KEY_MENSAS = "mensas"
    }
}
