package com.adnanearrassen.ytarchiver.domain.model

/** An item that has been fully archived to local storage. */
data class ArchivedMedia(
    val id: Long,
    val title: String,
    val uploader: String?,
    val channelId: String?,
    val kind: MediaKind,
    val filePath: String,
    val thumbnailPath: String?,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val resolutionLabel: String?,
    val codec: String?,
    val sourceUrl: String,
    val isFavorite: Boolean,
    val playbackPositionMs: Long,
    val lastWatchedAt: Long?,
    val addedAt: Long,
    val fileExists: Boolean = true,
) {
    /** 0f..1f resume progress for "Continue Watching". */
    val watchProgress: Float
        get() = if (durationSeconds > 0)
            (playbackPositionMs / 1000f / durationSeconds).coerceIn(0f, 1f) else 0f

    val isPartiallyWatched: Boolean get() = watchProgress in 0.02f..0.95f
}

data class Playlist(
    val id: Long,
    val name: String,
    val description: String?,
    val thumbnailPath: String?,
    val itemCount: Int,
    val totalDurationSeconds: Long,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val createdAt: Long,
)

data class StorageBreakdown(
    val totalBytes: Long,
    val videoBytes: Long,
    val musicBytes: Long,
    val thumbnailBytes: Long,
    val deviceFreeBytes: Long,
    val deviceTotalBytes: Long,
    val largestItems: List<ArchivedMedia>,
    val brokenItems: List<ArchivedMedia>,
)

/** A row in the download history log. */
data class DownloadHistoryRecord(
    val id: Long,
    val title: String,
    val sourceUrl: String,
    val type: DownloadType,
    val videoQuality: String?,
    val audioQuality: String?,
    val fileSizeBytes: Long,
    val startedAt: Long,
    val completedAt: Long?,
    val conversionMillis: Long,
    val succeeded: Boolean,
) {
    val downloadMillis: Long get() = (completedAt ?: startedAt) - startedAt
}
