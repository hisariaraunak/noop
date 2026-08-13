package com.noop.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noop.features.today.TodayRoute
import com.noop.ui.AppViewModel
import com.noop.ui.designsystem.NoopDesignTheme

private enum class RebuildDestination(val route: String, val label: String) {
    Today("today_v2", "Today"), Journal("journal_v2", "Journal"), Trends("trends_v2", "Trends"), You("you_v2", "You")
}

private object RebuildRoute {
    const val Recovery = "health/recovery"
    const val Sleep = "health/sleep"
    const val Strain = "health/strain"
    const val JournalEditor = "journal/editor"
    const val Workouts = "you/workouts"
    const val Devices = "you/devices"
    const val DataSources = "you/data-sources"
    const val Notifications = "you/notifications"
    const val Settings = "you/settings"
}

@Composable
fun RebuildAppRoot(viewModel: AppViewModel) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    var themeMode by remember { mutableStateOf(loadThemeMode(context)) }
    val dark = when (themeMode) { RebuildThemeMode.System -> systemDark; RebuildThemeMode.Light -> false; RebuildThemeMode.Dark -> true }

    NoopDesignTheme(darkTheme = dark) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route ?: RebuildDestination.Today.route
        val isPrimary = RebuildDestination.entries.any { it.route == currentRoute }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (isPrimary) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    RebuildDestination.entries.forEach { destination ->
                        val icon = when (destination) {
                            RebuildDestination.Today -> Icons.Filled.Home
                            RebuildDestination.Journal -> Icons.Filled.Edit
                            RebuildDestination.Trends -> Icons.Filled.Timeline
                            RebuildDestination.You -> Icons.Filled.Person
                        }
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { nav.navigate(destination.route) { launchSingleTop = true; restoreState = true; popUpTo(RebuildDestination.Today.route) { saveState = true } } },
                            icon = { Icon(icon, contentDescription = destination.label) }, label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(navController = nav, startDestination = RebuildDestination.Today.route, modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                composable(RebuildDestination.Today.route) {
                    TodayRoute(
                        viewModel,
                        onRecovery = { nav.navigate(RebuildRoute.Recovery) },
                        onSleep = { nav.navigate(RebuildRoute.Sleep) },
                        onStrain = { nav.navigate(RebuildRoute.Strain) },
                    )
                }
                composable(RebuildDestination.Journal.route) { JournalOverviewScreen(viewModel, onOpenJournal = { nav.navigate(RebuildRoute.JournalEditor) }) }
                composable(RebuildDestination.Trends.route) { TrendsOverviewScreen(viewModel) }
                composable(RebuildDestination.You.route) {
                    YouHubScreen(
                        onWorkouts = { nav.navigate(RebuildRoute.Workouts) },
                        onDevices = { nav.navigate(RebuildRoute.Devices) },
                        onDataSources = { nav.navigate(RebuildRoute.DataSources) },
                        onNotifications = { nav.navigate(RebuildRoute.Notifications) },
                        onSettings = { nav.navigate(RebuildRoute.Settings) },
                    )
                }
                composable(RebuildRoute.Recovery) { HealthDetailScreen(viewModel, HealthMetric.Recovery) }
                composable(RebuildRoute.Sleep) { HealthDetailScreen(viewModel, HealthMetric.Sleep) }
                composable(RebuildRoute.Strain) { HealthDetailScreen(viewModel, HealthMetric.Strain) }
                composable(RebuildRoute.JournalEditor) { JournalEditorV2(viewModel) }
                composable(RebuildRoute.Workouts) { WorkoutsV2Screen(viewModel) }
                composable(RebuildRoute.Devices) { DevicesV2Screen(viewModel) }
                composable(RebuildRoute.DataSources) { DataSourcesV2Screen(viewModel, onDevices = { nav.navigate(RebuildRoute.Devices) }) }
                composable(RebuildRoute.Notifications) { NotificationsV2Screen() }
                composable(RebuildRoute.Settings) {
                    SettingsV2Screen(themeMode = themeMode) { mode -> themeMode = mode; saveThemeMode(context, mode) }
                }
            }
        }
    }
}
