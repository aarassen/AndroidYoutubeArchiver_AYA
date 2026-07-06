package com.adnanearrassen.ytarchiver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import kotlinx.coroutines.flow.Flow

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
