package com.sheuberger.fitness.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sheuberger.fitness.ui.bodyweight.BodyWeightScreen
import com.sheuberger.fitness.ui.exercises.ExerciseDetailScreen
import com.sheuberger.fitness.ui.exercises.ExercisesScreen
import com.sheuberger.fitness.ui.workouts.WorkoutDetailScreen
import com.sheuberger.fitness.ui.workouts.WorkoutsScreen

/** Die drei Tabs der unteren Navigationsleiste. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Workouts("workouts", "Workouts", Icons.Outlined.FitnessCenter),
    Exercises("exercises", "Übungen", Icons.AutoMirrored.Outlined.ListAlt),
    BodyWeight("bodyweight", "Körpergewicht", Icons.Outlined.MonitorWeight),
}

private object Routes {
    const val WORKOUT = "workout/{workoutId}"
    const val EXERCISE = "exercise/{exerciseId}"
    fun workout(id: Long) = "workout/$id"
    fun exercise(id: Long) = "exercise/$id"
}

@Composable
fun FitnessAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = Tab.entries.any { it.route == currentDestination?.route }

    Scaffold(
        // Die einzelnen Screens kümmern sich selbst um Status- und Navigationsleiste.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Verhindert, dass sich beim Tab-Wechsel ein riesiger Back-Stack aufbaut.
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Workouts.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
            composable(Tab.Workouts.route) {
                WorkoutsScreen(onOpenWorkout = { navController.navigate(Routes.workout(it)) })
            }
            composable(Tab.Exercises.route) {
                ExercisesScreen(onOpenExercise = { navController.navigate(Routes.exercise(it)) })
            }
            composable(Tab.BodyWeight.route) {
                BodyWeightScreen()
            }
            composable(
                route = Routes.WORKOUT,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
            ) {
                WorkoutDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { navController.navigate(Routes.exercise(it)) },
                )
            }
            composable(
                route = Routes.EXERCISE,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType }),
            ) {
                ExerciseDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
