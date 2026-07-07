package com.adnanearrassen.ytarchiver.domain.repository

import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.Channel
import com.adnanearrassen.ytarchiver.domain.model.ContinueItem
import com.adnanearrassen.ytarchiver.domain.model.DownloadHistoryRecord
import com.adnanearrassen.ytarchiver.domain.model.DownloadItem
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.EngineVersionInfo
import com.adnanearrassen.ytarchiver.domain.model.MediaInfo
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import com.adnanearrassen.ytarchiver.domain.model.StorageBreakdown
import com.adnanearrassen.ytarchiver.domain.model.UpdateState
import kotlinx.coroutines.flow.Flow

/** Analyses URLs into [MediaInfo] via the yt-dlp engine. */
interface MediaAnalyzer {
    suspend fun analyze(url: String): OpResult<MediaInfo>
}

/** Owns the download queue and the persisted download records. */
interface DownloadRepository {
    fun observeQueue(): Flow<List<DownloadItem>>
    fun observeActive(): Flow<List<DownloadItem>>

    /** Enqueue a download for a single resolved media URL. Returns the row id. */
    suspend fun enqueue(
        sourceUrl: String,
        title: String,
        uploader: String?,
        thumbnailUrl: String?,
        options: DownloadOptions,
    ): Long

    /** Enqueue every entry of an already-analysed playlist. */
    suspend fun enqueuePlaylist(
        info: MediaInfo,
        options: DownloadOptions,
    ): List<Long>

    suspend fun pause(id: Long)
    suspend fun resume(id: Long)
    suspend fun cancel(id: Long)
    suspend fun retry(id: Long)
    suspend fun moveUp(id: Long)
    suspend fun moveDown(id: Long)
    suspend fun clearFinished()
}

/** The offline library: archived media + playlists + storage stats. */
interface LibraryRepository {
    fun observeAll(): Flow<List<ArchivedMedia>>
    fun observeByKind(kind: MediaKind): Flow<List<ArchivedMedia>>

    /** Media that is NOT part of any playlist (for the main feed). */
    fun observeStandalone(): Flow<List<ArchivedMedia>>
    fun observeStandaloneByKind(kind: MediaKind): Flow<List<ArchivedMedia>>

    /** Channels (uploaders) grouping. */
    fun observeChannels(): Flow<List<Channel>>
    fun observeByChannel(name: String): Flow<List<ArchivedMedia>>
    suspend fun deleteChannel(name: String)

    /** A single playlist and its items (ordered by playlist index). */
    fun observePlaylist(playlistId: Long): Flow<Playlist?>
    fun observePlaylistItems(playlistId: Long): Flow<List<ArchivedMedia>>
    /** One-shot ordered items — used to build the playlist playback queue. */
    suspend fun getPlaylistItems(playlistId: Long): List<ArchivedMedia>

    fun observeRecentlyAdded(limit: Int): Flow<List<ArchivedMedia>>
    fun observeContinueWatching(limit: Int): Flow<List<ArchivedMedia>>

    /** Continue-watching entries: standalone videos + playlists-in-progress. */
    fun observeContinueItems(limit: Int): Flow<List<ContinueItem>>
    suspend fun clearWatchProgress(id: Long)
    suspend fun clearPlaylistWatchProgress(playlistId: Long)
    fun observeFavorites(): Flow<List<ArchivedMedia>>
    fun observeDownloadedToday(): Flow<List<ArchivedMedia>>
    fun search(query: String): Flow<List<ArchivedMedia>>

    suspend fun getById(id: Long): ArchivedMedia?
    suspend fun toggleFavorite(id: Long)
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long)
    suspend fun delete(id: Long, deleteFile: Boolean)

    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String, description: String?): Long
    suspend fun renamePlaylist(id: Long, name: String)
    /** Deletes the playlist; if [deleteMedia] is true, also deletes every item
     *  in it (files included). */
    suspend fun deletePlaylist(id: Long, deleteMedia: Boolean)
    suspend fun togglePlaylistFavorite(id: Long)
    suspend fun addToPlaylist(playlistId: Long, mediaId: Long)
    suspend fun removeFromPlaylist(playlistId: Long, mediaId: Long)
    suspend fun setPlaylistPinned(id: Long, pinned: Boolean)
    suspend fun mergePlaylists(sourceId: Long, targetId: Long)

    suspend fun storageBreakdown(): StorageBreakdown
    fun observeHistory(): Flow<List<DownloadHistoryRecord>>
}

/** User settings, backed by DataStore. */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** yt-dlp engine version + self-update. */
interface EngineUpdateRepository {
    val state: Flow<UpdateState>
    suspend fun checkForUpdates(): OpResult<EngineVersionInfo>
    suspend fun update(): OpResult<String>
    suspend fun installedVersion(): String?
}
