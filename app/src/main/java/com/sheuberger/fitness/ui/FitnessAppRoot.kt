package com.sheuberger.fitness.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sheuberger.fitness.ui.bodyweight.BodyWeightScreen
import com.sheuberger.fitness.ui.components.AppOverlayHost
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
    val showBottomBar = currentDestination == null || Tab.entries.any { it.route == currentDestination.route }

    // Ebene für Sheets und Dialoge (AppSheet/AppDialog): liegt über allem, auch über der Navigationsleiste.
    AppOverlayHost {
        Scaffold(
            // Die einzelnen Screens kümmern sich selbst um Status- und Navigationsleiste.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                // Die Leiste fährt beim Öffnen einer Detailseite nach unten weg, statt zu verschwinden.
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = expandVertically(tween(SlideMillis, easing = NavEasing), expandFrom = Alignment.Top),
                    exit = shrinkVertically(tween(SlideMillis, easing = NavEasing), shrinkTowards = Alignment.Top),
                ) {
                    // Ruhige Leiste im warmen Hintergrundton, nur durch eine feine Linie abgesetzt (wie in der Claude-App).
                    // Der aktive Tab bekommt eine zart eingefärbte Akzent-Pille.
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
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
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
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
                enterTransition = { enter() },
                exitTransition = { exit() },
                popEnterTransition = { popEnter() },
                popExitTransition = { popExit() },
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
}

// --- Seitenübergänge ---
// Zwischen den Tabs wird nur kurz überblendet (wie bei Instagram oder Apple Health),
// Detailseiten schieben sich von rechts herein und beim Zurückgehen wieder hinaus.
// So "fühlt" man, ob man tiefer in die App geht oder nur den Bereich wechselt.

private const val SlideMillis = 320
private val NavEasing = FastOutSlowInEasing

private fun NavBackStackEntry.isTab() = Tab.entries.any { it.route == destination.route }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.isTab() && targetState.isTab()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTabSwitch()) {
        fadeIn(tween(220, delayMillis = 60, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(220, delayMillis = 60, easing = LinearOutSlowInEasing), initialScale = 0.98f)
    } else {
        slideInHorizontally(tween(SlideMillis, easing = NavEasing)) { it / 4 } + fadeIn(tween(SlideMillis))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (isTabSwitch()) {
        fadeOut(tween(80))
    } else {
        // Die alte Seite rückt nur ein Stück nach links und wird dunkler, wie ein Stapel Karten.
        slideOutHorizontally(tween(SlideMillis, easing = NavEasing)) { -it / 10 } + fadeOut(tween(SlideMillis), targetAlpha = 0.6f)
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    if (isTabSwitch()) enter()
    else slideInHorizontally(tween(SlideMillis, easing = NavEasing)) { -it / 10 } + fadeIn(tween(SlideMillis), initialAlpha = 0.6f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    if (isTabSwitch()) exit()
    else slideOutHorizontally(tween(SlideMillis, easing = NavEasing)) { it / 4 } + fadeOut(tween(SlideMillis))
