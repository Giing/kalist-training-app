package com.kalist.training.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kalist.training.R
import com.kalist.training.ui.navigation.CalendarRoute
import com.kalist.training.ui.navigation.ExercisesRoute
import com.kalist.training.ui.navigation.KalistNavHost
import com.kalist.training.ui.navigation.SettingsRoute
import com.kalist.training.ui.navigation.TemplatesRoute
import kotlin.reflect.KClass

private data class TopLevel(val route: Any, val routeClass: KClass<*>, @StringRes val label: Int, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(CalendarRoute, CalendarRoute::class, R.string.nav_calendar, Icons.Outlined.CalendarMonth),
    TopLevel(TemplatesRoute, TemplatesRoute::class, R.string.nav_templates, Icons.Outlined.ListAlt),
    TopLevel(ExercisesRoute, ExercisesRoute::class, R.string.nav_exercises, Icons.Outlined.FitnessCenter),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Outlined.Settings),
)

@Composable
fun KalistRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination == null || topLevels.any { destination.hasRoute(it.routeClass) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevels.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        KalistNavHost(
            navController = navController,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}
