package com.kalist.training.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kalist.training.ui.calendar.CalendarScreen
import com.kalist.training.ui.exercises.ExerciseEditScreen
import com.kalist.training.ui.exercises.ExercisesScreen
import com.kalist.training.ui.settings.SettingsScreen
import com.kalist.training.ui.templates.TemplateEditScreen
import com.kalist.training.ui.templates.TemplatesScreen

@Composable
fun KalistNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = CalendarRoute, modifier = modifier) {
        composable<CalendarRoute> { CalendarScreen() }
        composable<TemplatesRoute> {
            TemplatesScreen(
                onOpen = { navController.navigate(TemplateEditRoute(it)) },
                onCreate = { navController.navigate(TemplateEditRoute()) },
            )
        }
        composable<TemplateEditRoute> { TemplateEditScreen(onDone = back) }
        composable<ExercisesRoute> {
            ExercisesScreen(
                onOpen = { navController.navigate(ExerciseEditRoute(it)) },
                onCreate = { navController.navigate(ExerciseEditRoute()) },
            )
        }
        composable<ExerciseEditRoute> {
            ExerciseEditScreen(onDone = back, onOpenHistory = { navController.navigate(ExerciseHistoryRoute(it)) })
        }
        composable<SettingsRoute> { SettingsScreen() }
    }
}
