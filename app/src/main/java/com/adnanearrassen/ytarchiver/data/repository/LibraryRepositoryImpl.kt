package com.adnanearrassen.ytarchiver.data.repository

import android.os.StatFs
import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistItemCrossRef
import com.adnanearrassen.ytarchiver.data.mapper.toDomain
import com.adnanearrassen.ytarchiver.data.mapper.toHistoryRecord
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.DownloadHistoryRecord
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import com.adnanearrassen.ytarchiver.domain.model.StorageBreakdown
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val storageLocator: StorageLocator,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LibraryRepository {

    // NOTE: toDomain() touches the filesystem (File.exists()). These maps MUST
    // run off the main thread — flowOn(io) keeps that disk I/O away from the UI
    // dispatcher (viewModelScope collects on Main), otherwise the feed janks /
    // freezes as the library grows after each download.
    override fun observeAll(): Flow<List<ArchivedMedia>> =
        mediaDao.observeAll().map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeByKind(kind: MediaKind): Flow<List<ArchivedMedia>> =
        mediaDao.observeByKind(kind).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeStandalone(): Flow<List<ArchivedMedia>> =
        mediaDao.observeStandalone().map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeStandaloneByKind(kind: MediaKind): Flow<List<ArchivedMedia>> =
        mediaDao.observeStandaloneByKind(kind).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observePlaylistWithStats(playlistId).map { it?.toDomain() }.flowOn(io)

    override fun observePlaylistItems(playlistId: Long): Flow<List<ArchivedMedia>> =
        playlistDao.observeItems(playlistId).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeRecentlyAdded(limit: Int): Flow<List<ArchivedMedia>> =
        mediaDao.observeRecentlyAdded(limit).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeContinueWatching(limit: Int): Flow<List<ArchivedMedia>> =
        mediaDao.observeContinueWatching(limit).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeFavorites(): Flow<List<ArchivedMedia>> =
        mediaDao.observeFavorites().map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun observeDownloadedToday(): Flow<List<ArchivedMedia>> =
        mediaDao.observeAddedSince(startOfToday()).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override fun search(query: String): Flow<List<ArchivedMedia>> =
        mediaDao.search(query).map { it.map { e -> e.toDomain() } }.flowOn(io)

    override suspend fun getById(id: Long): ArchivedMedia? = withContext(io) {
        mediaDao.getById(id)?.toDomain()
    }

    override suspend fun toggleFavorite(id: Long) = withContext(io) {
        mediaDao.toggleFavorite(id)
    }

    override suspend fun updatePlaybackPosition(id: Long, positionMs: Long) = withContext(io) {
        mediaDao.updatePlayback(id, positionMs, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long, deleteFile: Boolean) = withContext(io) {
        if (deleteFile) {
            mediaDao.getById(id)?.let { entity ->
                // Remove the whole resource group (media + thumbnail + subtitles
                // + metadata json), which all share the media's base name.
                deleteResourceGroup(entity.filePath)
                // Also clean up a thumbnail stored elsewhere (older downloads).
                entity.thumbnailPath
                    ?.takeIf { File(it).parent != File(entity.filePath).parent }
                    ?.let { runCatching { File(it).delete() } }
            }
        }
        mediaDao.delete(id)
    }

    private fun deleteResourceGroup(mediaFilePath: String) {
        val media = File(mediaFilePath)
        val base = media.nameWithoutExtension
        val dir = media.parentFile
        if (dir != null) {
            dir.listFiles { f -> f.isFile && f.name.startsWith("$base.") }
                ?.forEach { runCatching { it.delete() } }
        } else {
            runCatching { media.delete() }
        }
    }

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylistsWithStats().map { it.map { p -> p.toDomain() } }.flowOn(io)

    override suspend fun createPlaylist(name: String, description: String?): Long = withContext(io) {
        playlistDao.insert(
            PlaylistEntity(name = name, description = description, createdAt = System.currentTimeMillis())
        )
    }

    override suspend fun renamePlaylist(id: Long, name: String) = withContext(io) {
        playlistDao.rename(id, name)
    }

    override suspend fun deletePlaylist(id: Long) = withContext(io) {
        playlistDao.delete(id)
    }

    override suspend fun addToPlaylist(playlistId: Long, mediaId: Long) = withContext(io) {
        val position = playlistDao.maxPosition(playlistId) + 1
        playlistDao.addItem(
            PlaylistItemCrossRef(playlistId, mediaId, position, System.currentTimeMillis())
        )
    }

    override suspend fun removeFromPlaylist(playlistId: Long, mediaId: Long) = withContext(io) {
        playlistDao.removeItem(playlistId, mediaId)
    }

    override suspend fun setPlaylistPinned(id: Long, pinned: Boolean) = withContext(io) {
        playlistDao.setPinned(id, pinned)
    }

    override suspend fun mergePlaylists(sourceId: Long, targetId: Long) = withContext(io) {
        val sourceItems = playlistDao.itemsOf(sourceId)
        var position = playlistDao.maxPosition(targetId) + 1
        sourceItems.forEach { item ->
            playlistDao.addItem(
                PlaylistItemCrossRef(targetId, item.mediaId, position++, System.currentTimeMillis())
            )
        }
        playlistDao.delete(sourceId)
    }

    override suspend fun storageBreakdown(): StorageBreakdown = withContext(io) {
        val videoBytes = mediaDao.totalSizeForKind(MediaKind.VIDEO) +
            mediaDao.totalSizeForKind(MediaKind.SHORT) +
            mediaDao.totalSizeForKind(MediaKind.LIVE)
        val musicBytes = mediaDao.totalSizeForKind(MediaKind.MUSIC)
        val all = mediaDao.getAllOnce()
        val thumbBytes = all.mapNotNull { it.thumbnailPath }
            .sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        val largest = mediaDao.largest(10).map { it.toDomain() }
        val broken = all.filter { !File(it.filePath).exists() }.map { it.toDomain(fileExists = false) }

        val stat = StatFs(storageLocator.mediaRoot().absolutePath)
        StorageBreakdown(
            totalBytes = videoBytes + musicBytes + thumbBytes,
            videoBytes = videoBytes,
            musicBytes = musicBytes,
            thumbnailBytes = thumbBytes,
            deviceFreeBytes = stat.availableBytes,
            deviceTotalBytes = stat.totalBytes,
            largestItems = largest,
            brokenItems = broken,
        )
    }

    override fun observeHistory(): Flow<List<DownloadHistoryRecord>> =
        downloadDao.observeHistory().map { it.map { d -> d.toHistoryRecord() } }.flowOn(io)

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
