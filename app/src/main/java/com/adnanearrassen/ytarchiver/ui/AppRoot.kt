package com.adnanearrassen.ytarchiver.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adnanearrassen.ytarchiver.ui.download.DownloadScreen
import com.adnanearrassen.ytarchiver.ui.home.HomeScreen
import com.adnanearrassen.ytarchiver.ui.library.LibraryScreen
import com.adnanearrassen.ytarchiver.ui.manager.DownloadManagerScreen
import com.adnanearrassen.ytarchiver.ui.navigation.Routes
import com.adnanearrassen.ytarchiver.ui.navigation.TopLevelDestination
import com.adnanearrassen.ytarchiver.ui.player.PlayerScreen
import com.adnanearrassen.ytarchiver.ui.settings.SettingsScreen
import com.adnanearrassen.ytarchiver.ui.storage.StorageScreen
import com.adnanearrassen.ytarchiver.ui.history.HistoryScreen
import com.adnanearrassen.ytarchiver.ui.update.EngineUpdateScreen

@Composable
fun AppRoot(
    incomingUrl: String?,
    onUrlConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(currentRoute) { Log.d("YTNav", "Current route = $currentRoute") }

    // A shared link routes straight to the Download tab, pre-filled.
    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            navController.navigate(TopLevelDestination.DOWNLOAD.route) {
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val destination = backStackEntry?.destination
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                Log.d("YTNav", "Bottom nav tapped -> ${dest.route}")
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenMedia = { navController.navigate(Routes.player(it)) },
                    onQuickDownload = { navController.navigate(TopLevelDestination.DOWNLOAD.route) },
                    onSeeStorage = { navController.navigate(Routes.STORAGE) },
                )
            }
            composable(TopLevelDestination.DOWNLOAD.route) {
                DownloadScreen(
                    prefilledUrl = incomingUrl,
                    onUrlConsumed = onUrlConsumed,
                    onGoToManager = { navController.navigate(TopLevelDestination.MANAGER.route) },
                )
            }
            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    onOpenMedia = { navController.navigate(Routes.player(it)) },
                )
            }
            composable(TopLevelDestination.MANAGER.route) {
                DownloadManagerScreen()
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenEngineUpdate = { navController.navigate(Routes.ENGINE_UPDATE) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenStorage = { navController.navigate(Routes.STORAGE) },
                )
            }
            composable("${Routes.PLAYER}/{${Routes.ARG_MEDIA_ID}}") {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STORAGE) {
                StorageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ENGINE_UPDATE) {
                EngineUpdateScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
