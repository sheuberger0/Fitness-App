package com.sheuberger.fitness

import android.app.Application
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.FitnessRepository

/**
 * Lebt so lange wie die App. Hier werden Datenbank und Repository einmalig erzeugt
 * (einfache "manuelle Dependency Injection"), die ViewModels holen sie sich von hier.
 */
class FitnessApplication : Application() {
    val repository: FitnessRepository by lazy { FitnessRepository(AppDatabase.create(this)) }
}
