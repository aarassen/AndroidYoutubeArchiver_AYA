package com.adnanearrassen.ytarchiver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.adnanearrassen.ytarchiver.domain.model.MediaKind

@Entity(
    tableName = "archived_media",
    indices = [Index("kind"), Index("addedAt"), Index("lastWatchedAt"), Index("isFavorite")],
)
data class ArchivedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val isFavorite: Boolean = false,
    val playbackPositionMs: Long = 0,
    val lastWatchedAt: Long? = null,
    val addedAt: Long,
)
