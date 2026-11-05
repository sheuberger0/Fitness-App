package com.sheuberger.fitness.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sheuberger.fitness.FitnessApplication
import com.sheuberger.fitness.data.FitnessRepository
import com.sheuberger.fitness.ui.bodyweight.BodyWeightViewModel
import com.sheuberger.fitness.ui.exercises.ExerciseDetailViewModel
import com.sheuberger.fitness.ui.exercises.ExercisesViewModel
import com.sheuberger.fitness.ui.mensa.MensaViewModel
import com.sheuberger.fitness.ui.nutrition.AddFoodViewModel
import com.sheuberger.fitness.ui.nutrition.NutritionViewModel
import com.sheuberger.fitness.ui.workouts.WorkoutDetailViewModel
import com.sheuberger.fitness.ui.workouts.WorkoutsViewModel

/**
 * Erzeugt alle ViewModels und gibt ihnen das Repository mit.
 * [createSavedStateHandle] enthält die Navigationsargumente, z. B. die ID des Workouts.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { WorkoutsViewModel(repository()) }
        initializer { WorkoutDetailViewModel(createSavedStateHandle(), repository()) }
        initializer { ExercisesViewModel(repository()) }
        initializer { ExerciseDetailViewModel(createSavedStateHandle(), repository()) }
        initializer { BodyWeightViewModel(repository()) }
        initializer {
            val app = this[APPLICATION_KEY] as FitnessApplication
            MensaViewModel(app.mensaClient, app.mensaSettings, app.nutrition, app.nutritionSettings)
        }
        initializer {
            val app = this[APPLICATION_KEY] as FitnessApplication
            NutritionViewModel(app.nutrition, app.nutritionSettings, app.repository)
        }
        initializer {
            val app = this[APPLICATION_KEY] as FitnessApplication
            // Die Suche bei Open Food Facts als Funktion: Im Test ersetzt eine Attrappe das Internet.
            AddFoodViewModel(
                app.nutrition,
                app.mensaClient,
                app.mensaSettings,
                lookup = app.foodFacts::lookup,
                catalog = app.foodCatalog,
            )
        }
    }
}

private fun CreationExtras.repository(): FitnessRepository =
    (this[APPLICATION_KEY] as FitnessApplication).repository
