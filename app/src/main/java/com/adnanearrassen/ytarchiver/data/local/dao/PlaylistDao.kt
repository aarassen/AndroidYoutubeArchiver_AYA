package com.adnanearrassen.ytarchiver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistItemCrossRef
import kotlinx.coroutines.flow.Flow

/** Projection used to render playlist cards without loading every item. */
data class PlaylistWithStats(
    val id: Long,
    val name: String,
    val description: String?,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val createdAt: Long,
    val itemCount: Int,
    val totalDurationSeconds: Long,
)

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query(
        """SELECT p.id, p.name, p.description, p.thumbnailPath, p.isFavorite, p.isPinned, p.createdAt,
                  COUNT(pi.mediaId) AS itemCount,
                  COALESCE(SUM(m.durationSeconds), 0) AS totalDurationSeconds
           FROM playlists p
           LEFT JOIN playlist_items pi ON pi.playlistId = p.id
           LEFT JOIN archived_media m ON m.id = pi.mediaId
           GROUP BY p.id
           ORDER BY p.isPinned DESC, p.createdAt DESC"""
    )
    fun observePlaylistsWithStats(): Flow<List<PlaylistWithStats>>

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE playlists SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(ref: PlaylistItemCrossRef)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeItem(playlistId: Long, mediaId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query(
        """SELECT m.* FROM archived_media m
           INNER JOIN playlist_items pi ON pi.mediaId = m.id
           WHERE pi.playlistId = :playlistId
           ORDER BY pi.position ASC"""
    )
    fun observeItems(playlistId: Long): Flow<List<ArchivedMediaEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun itemsOf(playlistId: Long): List<PlaylistItemCrossRef>
}
