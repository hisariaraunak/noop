package com.noop.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noop.features.today.TodayRoute
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopDesignTheme

private enum class RebuildDestination(val route: String, val label: String) {
    Today("today_v2", "Today"),
    Journal("journal_v2", "Journal"),
    Trends("trends_v2", "Trends"),
    You("you_v2", "You"),
}

/** Android-first navigation shell for the rebuild. Legacy AppRoot remains intact during migration. */
@Composable
fun RebuildAppRoot(viewModel: AppViewModel) {
    NoopDesignTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route ?: RebuildDestination.Today.route

        Scaffold(
            bottomBar = {
                NavigationBar {
                    RebuildDestination.entries.forEach { destination ->
                        val icon = when (destination) {
                            RebuildDestination.Today -> Icons.Filled.Home
                            RebuildDestination.Journal -> Icons.Filled.Edit
                            RebuildDestination.Trends -> Icons.Filled.Timeline
                            RebuildDestination.You -> Icons.Filled.Person
                        }
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                nav.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(RebuildDestination.Today.route) { saveState = true }
                                }
                            },
                            icon = { Icon(icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = nav,
                startDestination = RebuildDestination.Today.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(RebuildDestination.Today.route) { TodayRoute(viewModel) }
                composable(RebuildDestination.Journal.route) {
                    RebuildSectionScreen(
                        title = "Journal",
                        description = "Workouts, sleep sessions and daily notes will live here.",
                    )
                }
                composable(RebuildDestination.Trends.route) {
                    RebuildSectionScreen(
                        title = "Trends",
                        description = "Long-term metrics, baselines and correlations will live here.",
                    )
                }
                composable(RebuildDestination.You.route) {
                    RebuildSectionScreen(
                        title = "You",
                        description = "Devices, profile, data and settings will live here.",
                    )
                }
            }
        }
    }
}
