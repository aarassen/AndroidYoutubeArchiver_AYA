package com.adnanearrassen.ytarchiver.storage

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where archived media, thumbnails and the Python working directory
 * live.
 *
 * Media is written to public, file-manager-visible folders under the shared
 * **Downloads** directory:
 *
 *     Download/YTArchiver/Videos
 *     Download/YTArchiver/Music
 *     Download/YTArchiver/Playlists/<playlist name>
 *
 * The Downloads collection is used (rather than Movies/Music) because it permits
 * files of any type — yt-dlp needs to write `.part`/`.ytdl` temp files during a
 * download, which the media collections (Movies/Music) reject on Android 11+.
 * Files an app creates here are readable/writable by that app via the File API
 * through FUSE without extra permissions, and stay on the device after uninstall.
 *
 * Thumbnails and the updatable engine stay in app-private internal storage.
 */
@Singleton
class StorageLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val rootFolderName = "YTArchiver"

    private fun downloadsRoot(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), rootFolderName)

    /** Public root of the archive (used for storage stats). */
    fun mediaRoot(): File = downloadsRoot().apply { mkdirs() }

    fun videoDir(): File = File(downloadsRoot(), "Videos").apply { mkdirs() }
    fun musicDir(): File = File(downloadsRoot(), "Music").apply { mkdirs() }

    /** Base folder for playlist downloads; each playlist gets its own subfolder. */
    fun playlistDir(playlistName: String): File =
        File(File(downloadsRoot(), "Playlists"), sanitize(playlistName)).apply { mkdirs() }

    // App-private (not shown in the file manager, cleared on uninstall).
    fun thumbnailDir(): File = File(context.filesDir, "thumbnails").apply { mkdirs() }
    fun tempDir(): File = File(context.cacheDir, "downloads").apply { mkdirs() }
    fun engineDir(): File = File(context.filesDir, "engine").apply { mkdirs() }

    /** Resolve a user-provided folder override, falling back to a default. */
    fun resolveOrDefault(userPath: String?, default: File): File =
        userPath?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } } ?: default

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "Playlist" }
}
