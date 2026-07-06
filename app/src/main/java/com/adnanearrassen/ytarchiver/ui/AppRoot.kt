package com.adnanearrassen.ytarchiver.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adnanearrassen.ytarchiver.ui.download.DownloadScreen
import com.adnanearrassen.ytarchiver.ui.history.HistoryScreen
import com.adnanearrassen.ytarchiver.ui.home.HomeScreen
import com.adnanearrassen.ytarchiver.ui.library.LibraryScreen
import com.adnanearrassen.ytarchiver.ui.manager.DownloadManagerScreen
import com.adnanearrassen.ytarchiver.ui.navigation.Routes
import com.adnanearrassen.ytarchiver.ui.navigation.TopLevelDestination
import com.adnanearrassen.ytarchiver.ui.player.PlayerScreen
import com.adnanearrassen.ytarchiver.ui.settings.SettingsScreen
import com.adnanearrassen.ytarchiver.ui.storage.StorageScreen
import com.adnanearrassen.ytarchiver.ui.update.EngineUpdateScreen

@Composable
fun AppRoot(
    incomingUrl: String?,
    onUrlConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // SINGLE entry point for switching between the five top-level tabs.
    //
    // Every tab switch — bottom bar, the Home "Download" FAB, the "go to
    // downloads" action after queuing, and shared links — MUST go through here.
    // The saveState/restoreState back stack only stays consistent if *all*
    // navigations to a top-level destination use identical options; mixing a
    // plain navigate() (the old FAB path) corrupted that bookkeeping and left
    // the Home tab "clickable but not responding".
    val switchTab = remember(navController) {
        { route: String -> navController.switchTopLevel(route) }
    }

    LaunchedEffect(currentRoute) { Log.d("YTNav", "Current route = $currentRoute") }

    // A shared link routes straight to the Download tab, pre-filled.
    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) switchTab(TopLevelDestination.DOWNLOAD.route)
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
                            onClick = { switchTab(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
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
                    onQuickDownload = { switchTab(TopLevelDestination.DOWNLOAD.route) },
                    onSeeStorage = { navController.navigate(Routes.STORAGE) },
                )
            }
            composable(TopLevelDestination.DOWNLOAD.route) {
                DownloadScreen(
                    prefilledUrl = incomingUrl,
                    onUrlConsumed = onUrlConsumed,
                    onGoToManager = { switchTab(TopLevelDestination.MANAGER.route) },
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

/**
 * Navigates to a top-level tab with the standard single-top, state-preserving
 * options. Guards against re-navigating to the tab you're already on (which,
 * combined with restoreState, could otherwise no-op in a confusing way).
 */
private fun NavController.switchTopLevel(route: String) {
    if (currentDestination?.route == route) return
    Log.d("YTNav", "switchTab -> $route")
    navigate(route) {
        // Pop back to the start destination so the back stack doesn't grow with
        // every tab switch; save each tab's state so it's restored on return.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
