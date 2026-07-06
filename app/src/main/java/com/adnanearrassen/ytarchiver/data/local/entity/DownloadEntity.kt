package com.adnanearrassen.ytarchiver.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import com.adnanearrassen.ytarchiver.domain.model.DownloadType

/**
 * A row in the download queue / download history. [optionsJson] holds the
 * serialized [com.adnanearrassen.ytarchiver.domain.model.DownloadOptions] so a
 * worker can reconstruct exactly what the user asked for, even after a restart.
 */
@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("queuePosition")],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val type: DownloadType,
    val optionsJson: String,
    val status: DownloadStatus,
    val queuePosition: Int,
    val progress: Float = 0f,
    val speedBytesPerSec: Long = 0,
    val avgSpeedBytesPerSec: Long = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val etaSeconds: Long = 0,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val outputPath: String? = null,
    val workId: String? = null,
    /** Non-null when this download is part of a playlist. */
    val playlistId: Long? = null,
    /** 1-based position of this item within its playlist (preserves order). */
    @ColumnInfo(defaultValue = "0") val playlistIndex: Int = 0,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val conversionMillis: Long = 0,
)
