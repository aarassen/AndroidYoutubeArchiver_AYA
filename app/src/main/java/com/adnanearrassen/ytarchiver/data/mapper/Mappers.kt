package com.adnanearrassen.ytarchiver.data.mapper

import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistWithStats
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.data.local.entity.DownloadEntity
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.DownloadHistoryRecord
import com.adnanearrassen.ytarchiver.domain.model.DownloadItem
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import java.io.File

fun ArchivedMediaEntity.toDomain(fileExists: Boolean = File(filePath).exists()) = ArchivedMedia(
    id = id,
    title = title,
    uploader = uploader,
    channelId = channelId,
    kind = kind,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    durationSeconds = durationSeconds,
    fileSizeBytes = fileSizeBytes,
    resolutionLabel = resolutionLabel,
    codec = codec,
    sourceUrl = sourceUrl,
    isFavorite = isFavorite,
    playbackPositionMs = playbackPositionMs,
    lastWatchedAt = lastWatchedAt,
    addedAt = addedAt,
    fileExists = fileExists,
)

fun DownloadEntity.toDomain() = DownloadItem(
    id = id,
    sourceUrl = sourceUrl,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    type = type,
    status = status,
    queuePosition = queuePosition,
    progress = progress,
    speedBytesPerSec = speedBytesPerSec,
    avgSpeedBytesPerSec = avgSpeedBytesPerSec,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    etaSeconds = etaSeconds,
    errorMessage = errorMessage,
    outputPath = outputPath,
    createdAt = createdAt,
)

fun DownloadEntity.toHistoryRecord() = DownloadHistoryRecord(
    id = id,
    title = title,
    sourceUrl = sourceUrl,
    type = type,
    videoQuality = if (type.name == "VIDEO") resolutionOf(optionsJson) else null,
    audioQuality = if (type.name == "MUSIC") audioOf(optionsJson) else null,
    fileSizeBytes = totalBytes,
    startedAt = startedAt ?: createdAt,
    completedAt = completedAt,
    conversionMillis = conversionMillis,
    succeeded = status.name == "COMPLETED",
)

fun PlaylistWithStats.toDomain() = Playlist(
    id = id,
    name = name,
    description = description,
    thumbnailPath = thumbnailPath,
    itemCount = itemCount,
    totalDurationSeconds = totalDurationSeconds,
    isFavorite = isFavorite,
    isPinned = isPinned,
    createdAt = createdAt,
)

// Cheap best-effort extraction so history rows can show a quality label without
// fully deserializing the options blob.
private fun resolutionOf(json: String): String? =
    Regex(""""resolution":"(\w+)"""").find(json)?.groupValues?.getOrNull(1)

private fun audioOf(json: String): String? =
    Regex(""""format":"(\w+)"""").find(json)?.groupValues?.getOrNull(1)
