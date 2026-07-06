package com.adnanearrassen.ytarchiver

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.adnanearrassen.ytarchiver.core.common.NotificationChannels
import com.adnanearrassen.ytarchiver.python.PythonRuntime
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.repository.EngineUpdateRepository
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.adnanearrassen.ytarchiver.core.common.ApplicationScope

/**
 * Application entry point.
 *
 *  - Bootstraps Hilt.
 *  - Provides a WorkManager [Configuration] backed by [HiltWorkerFactory] so
 *    download workers can be constructor-injected.
 *  - Starts the embedded Python runtime.
 *  - Kicks off an opportunistic yt-dlp update check on cold start.
 */
@HiltAndroidApp
class YtArchiverApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var pythonRuntime: PythonRuntime
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var engineUpdateRepository: EngineUpdateRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        pythonRuntime.start()
        maybeAutoUpdateEngine()
    }

    private fun maybeAutoUpdateEngine() = appScope.launch {
        val settings: AppSettings = settingsRepository.settings.first()
        if (!settings.autoUpdateYtDlp) return@launch
        runCatching {
            val result = engineUpdateRepository.checkForUpdates()
            if (result is com.adnanearrassen.ytarchiver.domain.model.OpResult.Success &&
                result.data.updateAvailable
            ) {
                engineUpdateRepository.update()
            }
        }
    }
}
