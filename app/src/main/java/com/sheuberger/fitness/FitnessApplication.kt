package com.sheuberger.fitness

import android.app.Application
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaSettings

/**
 * Lebt so lange wie die App. Hier werden Datenbank und Repository einmalig erzeugt
 * (einfache "manuelle Dependency Injection"), die ViewModels holen sie sich von hier.
 */
class FitnessApplication : Application() {
    val repository: FitnessRepository by lazy { FitnessRepository(AppDatabase.create(this)) }

    // Mensa: Lädt die Menüs aus dem Internet. Lebt hier, damit der Zwischenspeicher Tab-Wechsel übersteht.
    val mensaClient: MensaClient by lazy { MensaClient() }
    val mensaSettings: MensaSettings by lazy { MensaSettings(this) }
}
