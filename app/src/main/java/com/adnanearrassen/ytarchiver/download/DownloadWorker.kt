package com.adnanearrassen.ytarchiver.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.data.local.entity.DownloadEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistItemCrossRef
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
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val playlistDao: PlaylistDao,
    private val ytDlpService: YtDlpService,
    private val storageLocator: StorageLocator,
    private val settingsRepository: SettingsRepository,
    private val notifier: DownloadNotifier,
    private val scheduler: DownloadScheduler,
    private val httpClient: OkHttpClient,
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

        val settings = settingsRepository.settings.first()
        val outputDir = resolveOutputDir(options, settings.downloadPath)

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
                // Permanent failures (private/removed/region/age) can never
                // succeed on retry — mark failed immediately so the queue moves on.
                val permanent = isPermanentFailure(outcome.message)
                if (!permanent && settings.autoRetryFailed && entity.retryCount < settings.maxRetries) {
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
        // Save the thumbnail next to the media (same base name) as a resource,
        // then register the media + every side-resource (thumbnail, subtitles,
        // metadata json) with MediaStore so they appear in the file manager.
        val thumbnailPath = saveThumbnailNextTo(filePath, entity.thumbnailUrl)
        storageLocator.registerWithMediaStore(*siblingFiles(filePath).toTypedArray())
        val fileSize = if (sizeBytes > 0) sizeBytes else file.length()
        // Dedup: if this source was already downloaded, update that row instead
        // of adding a duplicate to Home/Library.
        val existing = mediaDao.findBySourceUrl(entity.sourceUrl)
        val mediaId = if (existing != null) {
            mediaDao.update(
                existing.copy(
                    title = resolvedTitle,
                    kind = kind,
                    filePath = filePath,
                    thumbnailPath = thumbnailPath ?: existing.thumbnailPath,
                    durationSeconds = durationSeconds.takeIf { it > 0 } ?: existing.durationSeconds,
                    fileSizeBytes = fileSize,
                    resolutionLabel = resolutionLabel ?: existing.resolutionLabel,
                    codec = codec ?: existing.codec,
                )
            )
            existing.id
        } else {
            mediaDao.insert(
                ArchivedMediaEntity(
                    title = resolvedTitle,
                    uploader = entity.uploader,
                    channelId = null,
                    kind = kind,
                    filePath = filePath,
                    thumbnailPath = thumbnailPath,
                    durationSeconds = durationSeconds,
                    fileSizeBytes = fileSize,
                    resolutionLabel = resolutionLabel,
                    codec = codec,
                    sourceUrl = entity.sourceUrl,
                    addedAt = now,
                )
            )
        }
        // Link into its playlist at the original index so order is preserved.
        entity.playlistId?.let { playlistId ->
            playlistDao.addItem(
                PlaylistItemCrossRef(
                    playlistId = playlistId,
                    mediaId = mediaId,
                    position = entity.playlistIndex,
                    addedAt = now,
                )
            )
        }
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

    private fun isPermanentFailure(message: String): Boolean {
        val m = message.lowercase()
        return listOf(
            "private", "unavailable", "removed", "deleted", "copyright",
            "region", "not available in your country", "sign in", "age",
            "members-only", "members only",
        ).any { m.contains(it) }
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

    /**
     * Downloads the remote thumbnail and saves it NEXT TO the media file, sharing
     * its base name (e.g. "My Video.jpg" beside "My Video.mp4"). YouTube often
     * serves WEBP; we decode and re-encode to JPEG so it renders everywhere.
     * Returns the local path, or null on failure (thumbnails are best-effort).
     */
    private fun saveThumbnailNextTo(mediaFilePath: String, url: String?): String? {
        if (url.isNullOrBlank()) {
            Log.d(TAG, "No thumbnail URL for $mediaFilePath")
            return null
        }
        return runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Thumbnail fetch failed HTTP ${response.code} for $url")
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                val file = storageLocator.sibling(mediaFilePath, "jpg")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    file.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    bitmap.recycle()
                } else {
                    // Couldn't decode (unlikely) — keep the raw bytes as a fallback.
                    file.writeBytes(bytes)
                }
                Log.i(TAG, "Saved thumbnail (${file.length()} bytes) -> ${file.absolutePath}")
                file.absolutePath
            }
        }.getOrElse {
            Log.w(TAG, "Thumbnail save error for $mediaFilePath: ${it.message}")
            null
        }
    }

    /** All files that share the media's base name in its folder: the media
     *  itself, thumbnail, subtitles (.srt/.vtt) and metadata (.info.json). */
    private fun siblingFiles(mediaFilePath: String): List<String> {
        val media = File(mediaFilePath)
        val base = media.nameWithoutExtension
        val dir = media.parentFile ?: return listOf(mediaFilePath)
        return dir.listFiles { f -> f.isFile && f.name.startsWith("$base.") }
            ?.map { it.absolutePath }
            ?.ifEmpty { listOf(mediaFilePath) }
            ?: listOf(mediaFilePath)
    }

    private fun resolveOutputDir(options: DownloadOptions, downloadPath: String?): File {
        val default = if (options.type == DownloadType.MUSIC) storageLocator.musicDir(downloadPath)
        else storageLocator.videoDir(downloadPath)
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
