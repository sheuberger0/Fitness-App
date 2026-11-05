package com.sheuberger.fitness

import android.app.Application
import com.sheuberger.fitness.data.AppDatabase
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.data.NutritionRepository
import com.sheuberger.fitness.mensa.MensaClient
import com.sheuberger.fitness.mensa.MensaSettings
import com.sheuberger.fitness.nutrition.FoodCatalog
import com.sheuberger.fitness.nutrition.NutritionSettings
import com.sheuberger.fitness.nutrition.OpenFoodFactsClient

/**
 * Lebt so lange wie die App. Hier werden Datenbank und Repository einmalig erzeugt
 * (einfache "manuelle Dependency Injection"), die ViewModels holen sie sich von hier.
 */
class FitnessApplication : Application() {
    // Eine Datenbank für alles: Training und Kalorien-Tracker teilen sich die Datei fitness.db.
    private val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: FitnessRepository by lazy { FitnessRepository(database) }
    val nutrition: NutritionRepository by lazy { NutritionRepository(database) }
    val nutritionSettings: NutritionSettings by lazy { NutritionSettings(this) }

    // Barcode-Suche bei Open Food Facts (nur Lesen, ohne Konto).
    val foodFacts: OpenFoodFactsClient by lazy { OpenFoodFactsClient() }

    // Die eingebauten Nährwert-Tabellen (Schweizer Nährwertdatenbank und Produkte aus Schweizer Läden).
    // Lebt hier, damit sie nur einmal geladen werden, egal wie oft das Sheet "Eintragen" aufgeht.
    val foodCatalog: FoodCatalog by lazy { FoodCatalog(open = assets::open) }

    // Mensa: Lädt die Menüs aus dem Internet. Lebt hier, damit der Zwischenspeicher Tab-Wechsel übersteht.
    val mensaClient: MensaClient by lazy { MensaClient() }
    val mensaSettings: MensaSettings by lazy { MensaSettings(this) }
}
