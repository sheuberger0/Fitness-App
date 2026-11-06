package com.sheuberger.fitness.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Merkt sich das gewählte Farbschema in den SharedPreferences (kleine Schlüssel-Wert-Datei, wie bei der Mensa).
 *
 * Gespeichert wird die [AppColorScheme.id], nicht die Position in der Liste. So bleibt die Auswahl auch dann
 * erhalten, wenn später Schemata dazukommen oder die Reihenfolge sich ändert. Die Trainingsdaten in der
 * Datenbank berührt das nicht.
 */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("darstellung", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(AppColorScheme.fromId(prefs.getString(KEY_SCHEME, null)))

    /** Das aktuelle Farbschema. Die App hört darauf und wechselt sofort, ohne Neustart. */
    val scheme: StateFlow<AppColorScheme> = state.asStateFlow()

    fun select(scheme: AppColorScheme) {
        state.value = scheme
        prefs.edit().putString(KEY_SCHEME, scheme.id).apply()
    }

    private companion object {
        const val KEY_SCHEME = "farbschema"
    }
}
