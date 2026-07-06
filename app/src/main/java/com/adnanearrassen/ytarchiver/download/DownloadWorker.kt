package com.adnanearrassen.ytarchiver.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.data.local.entity.DownloadEntity
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import com.adnanearrassen.ytarchiver.domain.model.DownloadType
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.python.YtDlpService
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Runs a single download end-to-end via the Python engine, streaming progress
 * back into Room and, on success, registering the finished file in the library.
 * Concurrency across the queue is orchestrated by [WorkManagerDownloadScheduler].
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val mediaDao: MediaDao,
    private val ytDlpService: YtDlpService,
    private val storageLocator: StorageLocator,
    private val settingsRepository: SettingsRepository,
    private val notifier: DownloadNotifier,
    private val scheduler: DownloadScheduler,
    private val json: Json,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        Log.i(TAG, "doWork start for download id=$id")
        if (id < 0) return Result.failure()
        val entity = downloadDao.getById(id) ?: run {
            Log.w(TAG, "No download row for id=$id")
            return Result.failure()
        }

        // Respect user actions taken before the worker started.
        if (entity.status == DownloadStatus.PAUSED || entity.status == DownloadStatus.CANCELED) {
            return Result.success()
        }

        setForegroundSafely(entity.title, 0, 0, indeterminate = true)
        downloadDao.update(
            entity.copy(status = DownloadStatus.DOWNLOADING, startedAt = System.currentTimeMillis())
        )

        val result = runDownload(entity)

        scheduler.scheduleQueue() // pull the next queued item
        return result
    }

    private suspend fun runDownload(entity: DownloadEntity): Result = coroutineScope {
        val options = runCatching {
            json.decodeFromString(DownloadOptions.serializer(), entity.optionsJson)
        }.getOrElse {
            markFailed(entity, "Invalid download options")
            return@coroutineScope Result.failure()
        }

        val outputDir = resolveOutputDir(options)

        // Persist progress off the Python callback thread via a conflated channel.
        val progressChannel = Channel<com.adnanearrassen.ytarchiver.domain.model.DownloadProgress>(Channel.CONFLATED)
        val persister = launch {
            progressChannel.consumeAsFlow().collect { p ->
                downloadDao.updateProgress(
                    id = entity.id,
                    progress = p.progress,
                    speed = p.speedBytesPerSec,
                    downloaded = p.downloadedBytes,
                    total = p.totalBytes,
                    eta = p.etaSeconds,
                    status = p.status,
                )
                setForegroundSafely(entity.title, p.percentInt(), p.speedBytesPerSec)
            }
        }

        val startNanos = System.nanoTime()
        val outcome = ytDlpService.download(
            url = entity.sourceUrl,
            options = options,
            outputDir = outputDir.absolutePath,
        ) { progress -> progressChannel.trySend(progress) }
        progressChannel.close()
        persister.join()

        when (outcome) {
            is OpResult.Success -> {
                registerCompleted(entity, options, outcome.data.filePath, outcome.data.fileSizeBytes,
                    conversionMillis = (System.nanoTime() - startNanos) / 1_000_000,
                    resolvedTitle = outcome.data.title ?: entity.title,
                    durationSeconds = outcome.data.durationSeconds)
                Result.success()
            }
            is OpResult.Error -> {
                Log.e(TAG, "Download failed for ${entity.sourceUrl}: ${outcome.message}")
                val settings = settingsRepository.settings.first()
                if (settings.autoRetryFailed && entity.retryCount < settings.maxRetries) {
                    downloadDao.update(
                        entity.copy(
                            status = DownloadStatus.QUEUED,
                            retryCount = entity.retryCount + 1,
                            errorMessage = outcome.message,
                        )
                    )
                    Result.retry()
                } else {
                    markFailed(entity, outcome.message)
                    Result.failure()
                }
            }
        }
    }

    private suspend fun registerCompleted(
        entity: DownloadEntity,
        options: DownloadOptions,
        filePath: String,
        sizeBytes: Long,
        conversionMillis: Long,
        resolvedTitle: String,
        durationSeconds: Long,
    ) {
        val file = File(filePath)
        val kind = if (options.type == DownloadType.MUSIC) MediaKind.MUSIC else MediaKind.VIDEO
        val resolutionLabel = (options as? DownloadOptions.Video)?.resolution?.label
        val codec = (options as? DownloadOptions.Video)?.videoCodec?.label

        val now = System.currentTimeMillis()
        mediaDao.insert(
            ArchivedMediaEntity(
                title = resolvedTitle,
                uploader = entity.uploader,
                channelId = null,
                kind = kind,
                filePath = filePath,
                thumbnailPath = null,
                durationSeconds = durationSeconds,
                fileSizeBytes = if (sizeBytes > 0) sizeBytes else file.length(),
                resolutionLabel = resolutionLabel,
                codec = codec,
                sourceUrl = entity.sourceUrl,
                addedAt = now,
            )
        )
        downloadDao.update(
            entity.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                outputPath = filePath,
                completedAt = now,
                conversionMillis = conversionMillis,
                errorMessage = null,
            )
        )
    }

    private suspend fun markFailed(entity: DownloadEntity, message: String) {
        downloadDao.update(
            entity.copy(
                status = DownloadStatus.FAILED,
                errorMessage = message,
                completedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun resolveOutputDir(options: DownloadOptions): File {
        val default = if (options.type == DownloadType.MUSIC) storageLocator.musicDir()
        else storageLocator.videoDir()
        val override = when (options) {
            is DownloadOptions.Video -> options.outputFolder
            is DownloadOptions.Music -> options.outputFolder
        }
        return storageLocator.resolveOrDefault(override, default)
    }

    private suspend fun setForegroundSafely(
        title: String,
        percent: Int,
        speed: Long,
        indeterminate: Boolean = false,
    ) {
        val id = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val notification = notifier.buildProgress(title, percent, speed, indeterminate)
        val info = ForegroundInfo(
            notifier.notificationIdFor(id),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        runCatching { setForeground(info) }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_DOWNLOAD_ID = "download_id"
        fun uniqueName(id: Long) = "download_$id"
        fun tag(id: Long) = "download_tag_$id"
    }
}

private fun com.adnanearrassen.ytarchiver.domain.model.DownloadProgress.percentInt(): Int =
    (progress * 100).toInt().coerceIn(0, 100)
