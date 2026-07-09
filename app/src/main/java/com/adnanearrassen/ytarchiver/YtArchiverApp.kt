package com.adnanearrassen.ytarchiver

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.adnanearrassen.ytarchiver.core.common.NotificationChannels
import com.adnanearrassen.ytarchiver.python.PythonRuntime
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.repository.EngineUpdateRepository
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
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
class YtArchiverApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var pythonRuntime: PythonRuntime
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var engineUpdateRepository: EngineUpdateRepository
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /** Coil image loader that can also decode a frame from local video files,
     *  so video cards always have a thumbnail even without a saved image. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictModeLogging()
        NotificationChannels.createAll(this)
        // Start Python OFF the main thread — Chaquopy loads native libs + reads
        // from disk, which froze the UI on launch when done synchronously here.
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            pythonRuntime.start()
            maybeAutoUpdateEngine()
        }
        // Recover the library from public storage after a reinstall (files there
        // survive uninstall; the DB does not). Only runs when the index is empty.
        maybeRestoreLibrary()
    }

    private fun maybeRestoreLibrary() = appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            if (libraryRepository.isEmpty()) libraryRepository.restoreFromStorage()
        }
    }

    /** Logs (does not crash) any accidental disk/network work on the main thread,
     *  which shows up in Logcat under the "StrictMode" tag — useful for tracking
     *  down UI-freeze / not-clickable issues. Debug builds only. */
    private fun enableStrictModeLogging() {
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
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
