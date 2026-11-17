package com.kalist.training.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kalist.training.ui.calendar.CalendarScreen
import com.kalist.training.ui.exercises.ExercisesScreen
import com.kalist.training.ui.settings.SettingsScreen
import com.kalist.training.ui.templates.TemplatesScreen

@Composable
fun KalistNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = CalendarRoute, modifier = modifier) {
        composable<CalendarRoute> { CalendarScreen() }
        composable<TemplatesRoute> { TemplatesScreen() }
        composable<ExercisesRoute> { ExercisesScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}
