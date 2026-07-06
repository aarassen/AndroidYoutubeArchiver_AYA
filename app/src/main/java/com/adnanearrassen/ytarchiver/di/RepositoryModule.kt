package com.adnanearrassen.ytarchiver.di

import com.adnanearrassen.ytarchiver.data.datastore.SettingsRepositoryImpl
import com.adnanearrassen.ytarchiver.data.repository.DownloadRepositoryImpl
import com.adnanearrassen.ytarchiver.data.repository.LibraryRepositoryImpl
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.download.DownloadScheduler
import com.adnanearrassen.ytarchiver.download.WorkManagerDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds @Singleton
    abstract fun bindDownloadScheduler(impl: WorkManagerDownloadScheduler): DownloadScheduler
}
