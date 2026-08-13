package com.noop.app

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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noop.features.today.TodayRoute
import com.noop.ui.AppRoot
import com.noop.ui.AppViewModel
import com.noop.ui.BackupSyncScreen
import com.noop.ui.DataSourcesScreen
import com.noop.ui.DevicesScreen
import com.noop.ui.JournalScreen
import com.noop.ui.NotificationsSettingsScreen
import com.noop.ui.SettingsScreen
import com.noop.ui.WeekInReviewScreen
import com.noop.ui.designsystem.NoopDesignTheme

private enum class RebuildDestination(val route: String, val label: String) {
    Today("today_v2", "Today"),
    Journal("journal_v2", "Journal"),
    Trends("trends_v2", "Trends"),
    You("you_v2", "You"),
}

private object RebuildRoute {
    const val JournalDetail = "journal/detail"
    const val WeekDetail = "trends/week"
    const val Devices = "you/devices"
    const val DataSources = "you/data-sources"
    const val Notifications = "you/notifications"
    const val Settings = "you/settings"
    const val Backup = "you/backup"
    const val MoreTools = "you/more-tools"
}

/** Android-first root for the NOOP 2.0 information architecture. */
@Composable
fun RebuildAppRoot(viewModel: AppViewModel) {
    NoopDesignTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route ?: RebuildDestination.Today.route
        val isPrimary = RebuildDestination.entries.any { it.route == currentRoute }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (isPrimary) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified,
                    ) {
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
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = nav,
                startDestination = RebuildDestination.Today.route,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                composable(RebuildDestination.Today.route) { TodayRoute(viewModel) }
                composable(RebuildDestination.Journal.route) {
                    JournalOverviewScreen(
                        viewModel = viewModel,
                        onOpenJournal = { nav.navigate(RebuildRoute.JournalDetail) },
                    )
                }
                composable(RebuildDestination.Trends.route) {
                    TrendsOverviewScreen(
                        viewModel = viewModel,
                        onOpenWeekReview = { nav.navigate(RebuildRoute.WeekDetail) },
                    )
                }
                composable(RebuildDestination.You.route) {
                    YouHubScreen(
                        onDevices = { nav.navigate(RebuildRoute.Devices) },
                        onDataSources = { nav.navigate(RebuildRoute.DataSources) },
                        onNotifications = { nav.navigate(RebuildRoute.Notifications) },
                        onSettings = { nav.navigate(RebuildRoute.Settings) },
                        onMoreTools = { nav.navigate(RebuildRoute.MoreTools) },
                    )
                }

                // Mature specialist screens sit one level below the coherent primary experience.
                composable(RebuildRoute.JournalDetail) { JournalScreen(viewModel) }
                composable(RebuildRoute.WeekDetail) { WeekInReviewScreen(viewModel) }
                composable(RebuildRoute.Devices) {
                    DevicesScreen(
                        viewModel = viewModel,
                        onUseFileImport = { nav.navigate(RebuildRoute.DataSources) },
                    )
                }
                composable(RebuildRoute.DataSources) { DataSourcesScreen(viewModel) }
                composable(RebuildRoute.Notifications) { NotificationsSettingsScreen(viewModel) }
                composable(RebuildRoute.Settings) {
                    SettingsScreen(
                        vm = viewModel,
                        onOpenBackupSync = { nav.navigate(RebuildRoute.Backup) },
                    )
                }
                composable(RebuildRoute.Backup) { BackupSyncScreen() }
                composable(RebuildRoute.MoreTools) { AppRoot(viewModel = viewModel) }
            }
        }
    }
}
