package com.adnanearrassen.ytarchiver.data.scan

import android.media.MediaMetadataRetriever
import android.util.Log
import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistItemCrossRef
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a storage rescan. */
data class LibraryScanResult(val importedMedia: Int, val importedPlaylists: Int) {
    val isEmpty: Boolean get() = importedMedia == 0 && importedPlaylists == 0
}

/**
 * Rebuilds the library database from files already on disk. Because media +
 * every side-resource lives in the PUBLIC Downloads/YTArchiver folder (which
 * survives an uninstall), a fresh install can recover the whole library by
 * walking those folders and re-reading each item's `.info.json` sidecar.
 *
 * Idempotent: files already indexed (matched by absolute path) are skipped, so
 * it is safe to run on every cold start and as a manual "rescan" action.
 */
@Singleton
class MediaLibraryScanner @Inject constructor(
    private val mediaDao: MediaDao,
    private val playlistDao: PlaylistDao,
    private val storageLocator: StorageLocator,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun scan(): LibraryScanResult = withContext(io) {
        val customPath = runCatching { settingsRepository.settings.first().downloadPath }.getOrNull()
        // Scan the active root and the default public root (in case they differ).
        val roots = listOfNotNull(storageLocator.root(customPath), storageLocator.mediaRoot())
            .distinctBy { it.absolutePath }

        var media = 0
        var playlists = 0
        for (root in roots) {
            // Standalone videos + music (files sit directly in these folders).
            media += scanFolder(File(root, "Videos"), defaultKind = MediaKind.VIDEO)
            media += scanFolder(File(root, "Music"), defaultKind = MediaKind.MUSIC)

            // Playlists: each subfolder is one playlist.
            val playlistsDir = File(root, "Playlists")
            playlistsDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
                val added = scanPlaylistFolder(dir)
                if (added > 0) { media += added; playlists++ }
            }
        }
        Log.i(TAG, "Storage scan imported $media media, $playlists playlists")
        LibraryScanResult(media, playlists)
    }

    /** Imports every not-yet-indexed media file in [folder] as standalone media. */
    private suspend fun scanFolder(folder: File, defaultKind: MediaKind): Int {
        val files = folder.listFiles { f -> f.isFile && isMedia(f) } ?: return 0
        var count = 0
        for (file in files) {
            if (mediaDao.getByFilePath(file.absolutePath) != null) continue
            mediaDao.insert(buildEntity(file, defaultKind))
            count++
        }
        return count
    }

    /** Imports a playlist folder, creating/reusing the playlist and linking items. */
    private suspend fun scanPlaylistFolder(dir: File): Int {
        val files = (dir.listFiles { f -> f.isFile && isMedia(f) } ?: return 0)
            .sortedWith(compareBy({ infoFor(it)?.playlistIndex ?: Int.MAX_VALUE }, { it.name.lowercase() }))
        if (files.isEmpty()) return 0

        val name = dir.name
        val playlistId = playlistDao.findByName(name)?.id
            ?: playlistDao.insert(PlaylistEntity(name = name, createdAt = files.minOf { it.lastModified() }))

        var count = 0
        files.forEachIndexed { index, file ->
            val existing = mediaDao.getByFilePath(file.absolutePath)
            val mediaId = if (existing != null) existing.id
            else { count++; mediaDao.insert(buildEntity(file, MediaKind.VIDEO)) }
            val position = infoFor(file)?.playlistIndex?.let { it - 1 } ?: index
            playlistDao.addItem(
                PlaylistItemCrossRef(playlistId, mediaId, position, System.currentTimeMillis())
            )
        }
        return count
    }

    /** Builds a media row from a file + its `.info.json`/thumbnail sidecars. */
    private fun buildEntity(file: File, defaultKind: MediaKind): ArchivedMediaEntity {
        val info = infoFor(file)
        val kind = if (isAudioExt(file.extension)) MediaKind.MUSIC else defaultKind
        val thumb = siblingIfExists(file, "jpg") ?: siblingIfExists(file, "webp")
            ?: siblingIfExists(file, "png")

        val infoDuration = info?.duration?.toLong() ?: 0L
        val (probedDuration, probedHeight) =
            if (infoDuration <= 0L || info?.height == null) probe(file) else infoDuration to info.height

        val id = info?.id?.takeIf { it.isNotBlank() }
        val sourceUrl = info?.webpageUrl ?: info?.originalUrl
            ?: id?.let { "https://youtu.be/$it" }
            ?: "local://${file.absolutePath}"

        val height = info?.height ?: probedHeight
        return ArchivedMediaEntity(
            title = info?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            uploader = info?.channel ?: info?.uploader,
            channelId = null,
            kind = kind,
            filePath = file.absolutePath,
            thumbnailPath = thumb?.absolutePath,
            durationSeconds = if (infoDuration > 0) infoDuration else probedDuration,
            fileSizeBytes = file.length(),
            resolutionLabel = height?.let { "${it}p" },
            codec = null,
            sourceUrl = sourceUrl,
            addedAt = file.lastModified(),
        )
    }

    private fun infoFor(file: File): InfoJson? {
        val sidecar = File(file.parentFile, file.nameWithoutExtension + ".info.json")
        if (!sidecar.exists()) return null
        return runCatching { json.decodeFromString(InfoJson.serializer(), sidecar.readText()) }.getOrNull()
    }

    private fun siblingIfExists(file: File, ext: String): File? =
        File(file.parentFile, file.nameWithoutExtension + "." + ext).takeIf { it.exists() }

    /** Extracts duration (seconds) and video height via the platform retriever. */
    private fun probe(file: File): Pair<Long, Int?> = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            (durMs / 1000) to h
        } finally {
            runCatching { r.release() }
        }
    }.getOrDefault(0L to null)

    private fun isMedia(f: File): Boolean =
        isAudioExt(f.extension) || isVideoExt(f.extension)

    private fun isAudioExt(ext: String) = ext.lowercase() in AUDIO_EXT
    private fun isVideoExt(ext: String) = ext.lowercase() in VIDEO_EXT

    @Serializable
    private data class InfoJson(
        val id: String? = null,
        val title: String? = null,
        val uploader: String? = null,
        val channel: String? = null,
        val duration: Double? = null,
        val height: Int? = null,
        @SerialName("webpage_url") val webpageUrl: String? = null,
        @SerialName("original_url") val originalUrl: String? = null,
        @SerialName("playlist_index") val playlistIndex: Int? = null,
    )

    companion object {
        private const val TAG = "MediaLibraryScanner"
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "flac", "wav", "weba")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "ts", "flv", "3gp")
    }
}
