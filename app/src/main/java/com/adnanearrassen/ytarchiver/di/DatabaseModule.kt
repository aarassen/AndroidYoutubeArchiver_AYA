package com.adnanearrassen.ytarchiver.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.adnanearrassen.ytarchiver.data.local.YtArchiverDatabase
import com.adnanearrassen.ytarchiver.data.local.dao.DownloadDao
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): YtArchiverDatabase =
        Room.databaseBuilder(context, YtArchiverDatabase::class.java, YtArchiverDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun mediaDao(db: YtArchiverDatabase): MediaDao = db.mediaDao()
    @Provides fun downloadDao(db: YtArchiverDatabase): DownloadDao = db.downloadDao()
    @Provides fun playlistDao(db: YtArchiverDatabase): PlaylistDao = db.playlistDao()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
