package com.adnanearrassen.ytarchiver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.data.local.entity.ArchivedMediaEntity
import com.adnanearrassen.ytarchiver.data.local.entity.DownloadEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistEntity
import com.adnanearrassen.ytarchiver.data.local.entity.PlaylistItemCrossRef

@Database(
    entities = [
        ArchivedMediaEntity::class,
        DownloadEntity::class,
        PlaylistEntity::class,
        PlaylistItemCrossRef::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class YtArchiverDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "yt_archiver.db"

        /** v2 tags downloads with their playlist + index so playlists keep order. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN playlistId INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN playlistIndex INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
