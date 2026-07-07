package com.adnanearrassen.ytarchiver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.adnanearrassen.ytarchiver.data.local.entity.DownloadEntity
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query(
        """SELECT * FROM downloads
           WHERE status NOT IN ('COMPLETED','CANCELED')
           ORDER BY queuePosition ASC, createdAt ASC"""
    )
    fun observeQueue(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING','ANALYZING','PROCESSING') ORDER BY queuePosition ASC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY queuePosition ASC")
    suspend fun byStatus(status: DownloadStatus): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('DOWNLOADING','ANALYZING','PROCESSING')")
    suspend fun activeCount(): Int

    @Query("SELECT COALESCE(MAX(queuePosition), 0) FROM downloads")
    suspend fun maxQueuePosition(): Int

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: DownloadStatus)

    @Query(
        """UPDATE downloads SET
             progress = :progress, speedBytesPerSec = :speed,
             downloadedBytes = :downloaded, totalBytes = :total,
             etaSeconds = :eta, status = :status
           WHERE id = :id"""
    )
    suspend fun updateProgress(
        id: Long,
        progress: Float,
        speed: Long,
        downloaded: Long,
        total: Long,
        eta: Long,
        status: DownloadStatus,
    )

    @Query("UPDATE downloads SET queuePosition = :position WHERE id = :id")
    suspend fun setQueuePosition(id: Long, position: Int)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','CANCELED')")
    suspend fun clearFinished()

    /** Ids of pending (not actively downloading, not completed) items — so their
     *  WorkManager jobs can be cancelled before the rows are deleted. */
    @Query("SELECT id FROM downloads WHERE status IN ('QUEUED','PAUSED','FAILED','ANALYZING')")
    suspend fun pendingIds(): List<Long>

    /** Removes every queued / paused / failed item (the whole pending queue). */
    @Query("DELETE FROM downloads WHERE status IN ('QUEUED','PAUSED','FAILED','ANALYZING')")
    suspend fun clearQueued()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    // --- History (completed + failed, most recent first) ---
    @Query("SELECT * FROM downloads WHERE completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun observeHistory(): Flow<List<DownloadEntity>>
}
