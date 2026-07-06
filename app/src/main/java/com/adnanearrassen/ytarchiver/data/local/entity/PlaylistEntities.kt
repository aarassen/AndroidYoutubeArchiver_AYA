package com.adnanearrassen.ytarchiver.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val thumbnailPath: String? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long,
)

/** Many-to-many join between playlists and archived media, with ordering. */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArchivedMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("mediaId")],
)
data class PlaylistItemCrossRef(
    val playlistId: Long,
    val mediaId: Long,
    val position: Int,
    val addedAt: Long,
)
