package com.adnanearrassen.ytarchiver.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where archived media and all of its side-resources (thumbnail,
 * subtitles, metadata) are stored, and keeps them together in a structured,
 * file-manager-visible layout:
 *
 *     <root>/Videos/<title>.mp4
 *     <root>/Videos/<title>.jpg          (thumbnail)
 *     <root>/Videos/<title>.en.srt       (subtitles)
 *     <root>/Videos/<title>.info.json    (metadata)
 *     <root>/Music/<title>.mp3
 *     <root>/Music/<title>.jpg
 *     <root>/Playlists/<playlist name>/<title>.mp4  (+ its resources)
 *
 * `<root>` defaults to the public **Downloads/YTArchiver** directory (visible in
 * the system file manager, survives uninstall). The Downloads collection is used
 * rather than Movies/Music because it accepts files of any type — yt-dlp writes
 * `.part`/`.ytdl`/`.json`/subtitle files that the media collections reject on
 * Android 11+. A user-chosen path (Settings > download path) overrides the root.
 */
@Singleton
class StorageLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val appFolderName = "YTArchiver"

    /** The default public root: Downloads/YTArchiver. */
    private fun defaultRoot(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), appFolderName)

    /** The active root — a user override if provided, else the default. */
    fun root(customPath: String? = null): File =
        (customPath?.takeIf { it.isNotBlank() }?.let { File(it) } ?: defaultRoot())
            .apply { mkdirs() }

    fun videoDir(customPath: String? = null): File =
        File(root(customPath), "Videos").apply { mkdirs() }

    fun musicDir(customPath: String? = null): File =
        File(root(customPath), "Music").apply { mkdirs() }

    /** Each playlist download gets its own subfolder, sanitized. */
    fun playlistDir(playlistName: String, customPath: String? = null): File =
        File(File(root(customPath), "Playlists"), sanitize(playlistName)).apply { mkdirs() }

    /** Public root used for storage-usage stats. */
    fun mediaRoot(): File = defaultRoot().apply { mkdirs() }

    // --- App-private working storage (cleared on uninstall) ---
    fun tempDir(): File = File(context.cacheDir, "downloads").apply { mkdirs() }
    fun engineDir(): File = File(context.filesDir, "engine").apply { mkdirs() }

    /** Resolve a per-download folder override (e.g. a playlist folder). */
    fun resolveOrDefault(userPath: String?, default: File): File =
        userPath?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } } ?: default

    /**
     * Path of a side-resource that lives next to [mediaFilePath] and shares its
     * base name, e.g. sibling("/…/My Video.mp4", "jpg") -> "/…/My Video.jpg".
     */
    fun sibling(mediaFilePath: String, extension: String): File {
        val media = File(mediaFilePath)
        return File(media.parentFile, media.nameWithoutExtension + "." + extension)
    }

    /** Registers a finished file with MediaStore so it appears in file/media apps. */
    fun registerWithMediaStore(vararg paths: String) {
        val existing = paths.filter { it.isNotBlank() && File(it).exists() }.toTypedArray()
        if (existing.isNotEmpty()) {
            runCatching { MediaScannerConnection.scanFile(context, existing, null, null) }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "Playlist" }
}
