package com.adnanearrassen.ytarchiver.di

import com.adnanearrassen.ytarchiver.domain.repository.EngineUpdateRepository
import com.adnanearrassen.ytarchiver.domain.repository.MediaAnalyzer
import com.adnanearrassen.ytarchiver.python.YtDlpMediaAnalyzer
import com.adnanearrassen.ytarchiver.python.update.EngineUpdateRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the yt-dlp/Python-backed engine services. */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds @Singleton
    abstract fun bindMediaAnalyzer(impl: YtDlpMediaAnalyzer): MediaAnalyzer

    @Binds @Singleton
    abstract fun bindEngineUpdateRepository(impl: EngineUpdateRepositoryImpl): EngineUpdateRepository
}
