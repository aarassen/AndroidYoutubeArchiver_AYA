package com.adnanearrassen.ytarchiver.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level, bottom-bar destinations. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    DOWNLOAD("download", "Download", Icons.Outlined.Download),
    LIBRARY("library", "Library", Icons.Outlined.VideoLibrary),
    MANAGER("manager", "Downloads", Icons.Outlined.QueueMusic),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

/** Non-bottom-bar routes. */
object Routes {
    const val PLAYER = "player"        // player/{mediaId}
    const val PLAYLIST = "playlist"    // playlist/{playlistId}
    const val STORAGE = "storage"
    const val HISTORY = "history"
    const val ENGINE_UPDATE = "engine_update"

    fun player(mediaId: Long) = "$PLAYER/$mediaId"
    fun playlist(playlistId: Long) = "$PLAYLIST/$playlistId"

    const val ARG_MEDIA_ID = "mediaId"
    const val ARG_PLAYLIST_ID = "playlistId"

    /** Optional deep-link arg: a URL to pre-fill on the Download screen. */
    const val ARG_SHARED_URL = "sharedUrl"
}
