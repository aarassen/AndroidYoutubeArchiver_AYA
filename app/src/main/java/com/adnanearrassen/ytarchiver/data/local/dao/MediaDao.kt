package com.adnanearrassen.ytarchiver.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import kotlinx.coroutines.flow.Flow

/** An in-progress media row plus the playlist it belongs to (if any). */
data class InProgressRow(
    @Embedded val media: ArchivedMediaEntity,
    val playlistId: Long?,
)

/** A channel/uploader with its item count. */
data class ChannelRow(
    val uploader: String,
    val count: Int,
)

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: ArchivedMediaEntity): Long

    @Update
    suspend fun update(media: ArchivedMediaEntity)

    @Query("SELECT * FROM archived_media ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ArchivedMediaEntity>>

    @Query("SELECT * FROM archived_media WHERE kind = :kind ORDER BY addedAt DESC")
    fun observeByKind(kind: MediaKind): Flow<List<ArchivedMediaEntity>>

    // --- "Standalone" = not a member of any playlist. Used for the main feed so
    //     playlist videos are shown via their playlist card, not individually. ---
    @Query(
        """SELECT * FROM archived_media
           WHERE id NOT IN (SELECT mediaId FROM playlist_items)
           ORDER BY addedAt DESC"""
    )
    fun observeStandalone(): Flow<List<ArchivedMediaEntity>>

    @Query(
        """SELECT * FROM archived_media
           WHERE kind = :kind AND id NOT IN (SELECT mediaId FROM playlist_items)
           ORDER BY addedAt DESC"""
    )
    fun observeStandaloneByKind(kind: MediaKind): Flow<List<ArchivedMediaEntity>>

    @Query("SELECT * FROM archived_media ORDER BY addedAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<ArchivedMediaEntity>>

    @Query(
        """SELECT * FROM archived_media
           WHERE playbackPositionMs > 0 AND lastWatchedAt IS NOT NULL
           ORDER BY lastWatchedAt DESC LIMIT :limit"""
    )
    fun observeContinueWatching(limit: Int): Flow<List<ArchivedMediaEntity>>

    /** In-progress items with their playlist id (null = standalone), newest first. */
    @Query(
        """SELECT m.*, (SELECT pi.playlistId FROM playlist_items pi WHERE pi.mediaId = m.id LIMIT 1) AS playlistId
           FROM archived_media m
           WHERE m.playbackPositionMs > 0 AND m.lastWatchedAt IS NOT NULL
           ORDER BY m.lastWatchedAt DESC LIMIT :limit"""
    )
    fun observeInProgress(limit: Int): Flow<List<InProgressRow>>

    @Query("UPDATE archived_media SET playbackPositionMs = 0, lastWatchedAt = NULL WHERE id = :id")
    suspend fun clearProgress(id: Long)

    @Query(
        """UPDATE archived_media SET playbackPositionMs = 0, lastWatchedAt = NULL
           WHERE id IN (SELECT mediaId FROM playlist_items WHERE playlistId = :playlistId)"""
    )
    suspend fun clearPlaylistProgress(playlistId: Long)

    @Query("SELECT * FROM archived_media WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<ArchivedMediaEntity>>

    @Query("SELECT * FROM archived_media WHERE addedAt >= :since ORDER BY addedAt DESC")
    fun observeAddedSince(since: Long): Flow<List<ArchivedMediaEntity>>

    @Query(
        """SELECT * FROM archived_media
           WHERE title LIKE '%' || :query || '%'
              OR uploader LIKE '%' || :query || '%'
           ORDER BY addedAt DESC"""
    )
    fun search(query: String): Flow<List<ArchivedMediaEntity>>

    @Query("SELECT * FROM archived_media WHERE id = :id")
    suspend fun getById(id: Long): ArchivedMediaEntity?

    @Query("SELECT * FROM archived_media WHERE sourceUrl = :url LIMIT 1")
    suspend fun findBySourceUrl(url: String): ArchivedMediaEntity?

    @Query("SELECT * FROM archived_media WHERE filePath = :path LIMIT 1")
    suspend fun getByFilePath(path: String): ArchivedMediaEntity?

    @Query("SELECT COUNT(*) FROM archived_media")
    suspend fun count(): Int

    // --- Channels (grouped by uploader) ---
    @Query(
        """SELECT uploader AS uploader, COUNT(*) AS count FROM archived_media
           WHERE uploader IS NOT NULL AND uploader != ''
           GROUP BY uploader ORDER BY uploader COLLATE NOCASE"""
    )
    fun observeChannels(): Flow<List<ChannelRow>>

    @Query("SELECT * FROM archived_media WHERE uploader = :name ORDER BY addedAt DESC")
    fun observeByChannel(name: String): Flow<List<ArchivedMediaEntity>>

    @Query("SELECT * FROM archived_media WHERE uploader = :name")
    suspend fun getByChannelOnce(name: String): List<ArchivedMediaEntity>

    @Query("UPDATE archived_media SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE archived_media SET playbackPositionMs = :positionMs, lastWatchedAt = :watchedAt WHERE id = :id")
    suspend fun updatePlayback(id: Long, positionMs: Long, watchedAt: Long)

    @Query("DELETE FROM archived_media WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM archived_media ORDER BY fileSizeBytes DESC LIMIT :limit")
    suspend fun largest(limit: Int): List<ArchivedMediaEntity>

    @Query("SELECT * FROM archived_media")
    suspend fun getAllOnce(): List<ArchivedMediaEntity>

    @Query(
        """SELECT * FROM archived_media
           WHERE id NOT IN (SELECT mediaId FROM playlist_items)
           ORDER BY addedAt DESC"""
    )
    suspend fun getStandaloneOnce(): List<ArchivedMediaEntity>

    @Query("SELECT COALESCE(SUM(fileSizeBytes),0) FROM archived_media WHERE kind = :kind")
    suspend fun totalSizeForKind(kind: MediaKind): Long
}
