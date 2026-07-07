package com.adnanearrassen.ytarchiver.data.repository

import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.data.local.entity.DownloadEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistEntity
import com.adnanearrassen.ytarchiver.data.mapper.toDomain
import com.adnanearrassen.ytarchiver.domain.model.DownloadItem
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import com.adnanearrassen.ytarchiver.domain.model.MediaInfo
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.download.DownloadScheduler
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val dao: DownloadDao,
    private val playlistDao: PlaylistDao,
    private val scheduler: DownloadScheduler,
    private val storageLocator: StorageLocator,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DownloadRepository {

    override fun observeQueue(): Flow<List<DownloadItem>> =
        dao.observeQueue().map { list -> list.map { it.toDomain() } }.flowOn(io)

    override fun observeActive(): Flow<List<DownloadItem>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }.flowOn(io)

    override suspend fun enqueue(
        sourceUrl: String,
        title: String,
        uploader: String?,
        thumbnailUrl: String?,
        options: DownloadOptions,
    ): Long = withContext(io) {
        val position = dao.maxQueuePosition() + 1
        val id = dao.insert(
            DownloadEntity(
                sourceUrl = sourceUrl,
                title = title,
                uploader = uploader,
                thumbnailUrl = thumbnailUrl,
                type = options.type,
                optionsJson = json.encodeToString(DownloadOptions.serializer(), options),
                status = DownloadStatus.QUEUED,
                queuePosition = position,
                createdAt = System.currentTimeMillis(),
            )
        )
        scheduler.scheduleQueue()
        id
    }

    override suspend fun enqueuePlaylist(
        info: MediaInfo,
        options: DownloadOptions,
    ): List<Long> = withContext(io) {
        val entries = info.playlist?.entries.orEmpty()
        // Route every entry into a dedicated folder named after the playlist,
        // under the user's configured download root.
        val downloadPath = settingsRepository.settings.first().downloadPath
        val playlistName = info.playlist?.title ?: info.title
        val folder = storageLocator.playlistDir(playlistName, downloadPath).absolutePath
        val options = options.withOutputFolder(folder)
        val optionsJson = json.encodeToString(DownloadOptions.serializer(), options)
        val now = System.currentTimeMillis()

        // Reuse an existing playlist with the same name (avoids duplicates on
        // re-download); otherwise create it. Items are linked by the worker.
        val playlistId = playlistDao.findByName(playlistName)?.id
            ?: playlistDao.insert(PlaylistEntity(name = playlistName, createdAt = now))

        val ids = entries.mapIndexed { index, entry ->
            val position = dao.maxQueuePosition() + 1
            dao.insert(
                DownloadEntity(
                    sourceUrl = entry.url,
                    title = entry.title,
                    uploader = info.uploader,
                    thumbnailUrl = entry.thumbnailUrl,
                    type = options.type,
                    optionsJson = optionsJson,
                    status = DownloadStatus.QUEUED,
                    queuePosition = position,
                    createdAt = now,
                    playlistId = playlistId,
                    playlistIndex = index + 1,
                )
            )
        }
        scheduler.scheduleQueue()
        ids
    }

    private fun DownloadOptions.withOutputFolder(folder: String): DownloadOptions = when (this) {
        is DownloadOptions.Video -> copy(outputFolder = folder)
        is DownloadOptions.Music -> copy(outputFolder = folder)
    }

    override suspend fun pause(id: Long) = withContext(io) {
        dao.setStatus(id, DownloadStatus.PAUSED)
        scheduler.cancelWork(id)
    }

    override suspend fun resume(id: Long) = withContext(io) {
        dao.setStatus(id, DownloadStatus.QUEUED)
        scheduler.scheduleQueue()
    }

    override suspend fun cancel(id: Long) = withContext(io) {
        dao.setStatus(id, DownloadStatus.CANCELED)
        scheduler.cancelWork(id)
    }

    override suspend fun retry(id: Long) = withContext(io) {
        val item = dao.getById(id) ?: return@withContext
        dao.update(
            item.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                errorMessage = null,
                downloadedBytes = 0,
            )
        )
        scheduler.scheduleQueue()
    }

    override suspend fun moveUp(id: Long) = swapWithNeighbor(id, above = true)
    override suspend fun moveDown(id: Long) = swapWithNeighbor(id, above = false)

    private suspend fun swapWithNeighbor(id: Long, above: Boolean) = withContext(io) {
        val queue = dao.byStatus(DownloadStatus.QUEUED)
            .sortedBy { it.queuePosition }
        val index = queue.indexOfFirst { it.id == id }
        if (index < 0) return@withContext
        val neighborIndex = if (above) index - 1 else index + 1
        if (neighborIndex !in queue.indices) return@withContext
        val a = queue[index]
        val b = queue[neighborIndex]
        dao.setQueuePosition(a.id, b.queuePosition)
        dao.setQueuePosition(b.id, a.queuePosition)
    }

    override suspend fun clearFinished() = withContext(io) {
        dao.clearFinished()
    }
}
