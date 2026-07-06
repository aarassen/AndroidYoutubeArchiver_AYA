package com.adnanearrassen.ytarchiver.domain.model

/**
 * A queued/active/finished download as surfaced to the Download Manager UI.
 * Mirrors the persisted `DownloadEntity` but with live progress fields.
 */
data class DownloadItem(
    val id: Long,
    val sourceUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val type: DownloadType,
    val status: DownloadStatus,
    val queuePosition: Int,
    val progress: Float,            // 0f..1f
    val speedBytesPerSec: Long,
    val avgSpeedBytesPerSec: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val etaSeconds: Long,
    val errorMessage: String?,
    val outputPath: String?,
    val createdAt: Long,
) {
    val remainingBytes: Long get() = (totalBytes - downloadedBytes).coerceAtLeast(0)
    val percent: Int get() = (progress * 100).toInt().coerceIn(0, 100)
    val canPause: Boolean get() = status == DownloadStatus.DOWNLOADING
    val canResume: Boolean get() = status == DownloadStatus.PAUSED
    val canRetry: Boolean get() = status == DownloadStatus.FAILED
    val canCancel: Boolean
        get() = status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.PAUSED ||
            status == DownloadStatus.QUEUED
}

/** A live progress snapshot emitted by the Python bridge during a download. */
data class DownloadProgress(
    val status: DownloadStatus,
    val progress: Float,
    val speedBytesPerSec: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val etaSeconds: Long,
    val filename: String? = null,
)
