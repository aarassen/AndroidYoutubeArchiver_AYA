package com.adnanearrassen.ytarchiver.server

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.adnanearrassen.ytarchiver.core.common.ApplicationScope
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import com.adnanearrassen.ytarchiver.domain.repository.MediaAnalyzer
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

data class WebServerState(
    val running: Boolean = false,
    val url: String? = null,
)

/**
 * Owns the lifecycle of the embedded [MediaWebServer] and the foreground service
 * that keeps it alive in the background.
 */
@Singleton
class WebServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val playlistDao: PlaylistDao,
    private val downloadRepository: DownloadRepository,
    private val analyzer: MediaAnalyzer,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private var server: MediaWebServer? = null
    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    val port: Int = MediaWebServer.DEFAULT_PORT

    @Synchronized
    fun start() {
        if (server != null) return
        val s = MediaWebServer(
            port = port,
            context = context,
            mediaDao = mediaDao,
            playlistDao = playlistDao,
            downloadRepository = downloadRepository,
            analyzer = analyzer,
            settingsRepository = settingsRepository,
            appScope = appScope,
        )
        val started = runCatching { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        if (started.isFailure) {
            Log.e(TAG, "Failed to start web server", started.exceptionOrNull())
            runCatching { s.stop() }
            return
        }
        server = s
        _state.value = WebServerState(running = true, url = "http://${localIpAddress()}:$port")
        Log.i(TAG, "Web server started at ${_state.value.url}")
        ContextCompat.startForegroundService(context, Intent(context, WebServerService::class.java))
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        _state.value = WebServerState(running = false, url = null)
        context.stopService(Intent(context, WebServerService::class.java))
        Log.i(TAG, "Web server stopped")
    }

    fun toggle() {
        if (_state.value.running) stop() else start()
    }

    private fun localIpAddress(): String {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .firstOrNull { it.startsWith("192.") || it.startsWith("10.") || it.startsWith("172.") }
        }.getOrNull() ?: "127.0.0.1"
    }

    companion object {
        private const val TAG = "WebServerManager"
    }
}
