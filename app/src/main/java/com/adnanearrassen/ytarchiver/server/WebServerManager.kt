package com.adnanearrassen.ytarchiver.server

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.adnanearrassen.ytarchiver.core.common.ApplicationScope
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.domain.repository.MediaAnalyzer
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

data class WebServerState(
    val running: Boolean = false,
    val httpUrl: String? = null,
    val httpsUrl: String? = null,
    /** Plain-HTTP base URL usable by a Chromecast, even in HTTPS-only mode. */
    val castBaseUrl: String? = null,
) {
    val primaryUrl: String? get() = httpsUrl ?: httpUrl
}

/**
 * Owns the embedded [MediaWebServer] instances (plain HTTP and/or HTTPS) and the
 * foreground service that keeps them alive. HTTPS uses a persisted self-signed
 * certificate; a password (basic auth) and an "HTTPS only" toggle come from
 * settings and are re-applied live.
 */
@Singleton
class WebServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val playlistDao: PlaylistDao,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val analyzer: MediaAnalyzer,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private var httpServer: MediaWebServer? = null
    private var httpsServer: MediaWebServer? = null

    /** Stable per-process secret appended to cast URLs so a Chromecast can fetch
     *  media without the Basic-auth password. */
    private val castToken: String = java.util.UUID.randomUUID().toString().replace("-", "")

    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    init {
        // Re-apply the server when HTTPS-related settings change while running.
        appScope.launch {
            settingsRepository.settings
                .map { it.webServerHttpsEnabled to it.webServerHttpsOnly }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (_state.value.running) restart() }
        }
    }

    private fun newServer(port: Int, castOnly: Boolean = false) = MediaWebServer(
        port = port,
        context = context,
        mediaDao = mediaDao,
        playlistDao = playlistDao,
        downloadRepository = downloadRepository,
        libraryRepository = libraryRepository,
        analyzer = analyzer,
        settingsRepository = settingsRepository,
        appScope = appScope,
        castToken = castToken,
        castOnly = castOnly,
    )

    @Synchronized
    fun start() {
        if (httpServer != null || httpsServer != null) return
        val settings = runBlocking { settingsRepository.settings.first() }
        val ip = localIpAddress()
        var httpUrl: String? = null
        var httpsUrl: String? = null
        var castBaseUrl: String? = null

        if (settings.webServerHttpsEnabled) {
            val ssl = runCatching {
                SelfSignedTls.serverSocketFactory(File(context.filesDir, "web_keystore.p12"))
            }.onFailure { Log.e(TAG, "TLS setup failed", it) }.getOrNull()
            if (ssl != null) {
                val s = newServer(HTTPS_PORT)
                s.makeSecure(ssl, null)
                if (runCatching { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.isSuccess) {
                    httpsServer = s
                    httpsUrl = "https://$ip:$HTTPS_PORT"
                }
            }
        }

        // Always run a plain-HTTP endpoint. When HTTPS-only is on it serves ONLY
        // tokenized /media & /thumb (so a Chromecast can still stream), while the
        // browsable UI stays HTTPS-only.
        val castOnly = settings.webServerHttpsEnabled && settings.webServerHttpsOnly
        val s = newServer(HTTP_PORT, castOnly = castOnly)
        if (runCatching { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }.isSuccess) {
            httpServer = s
            castBaseUrl = "http://$ip:$HTTP_PORT"
            if (!castOnly) httpUrl = castBaseUrl
        }

        if (httpServer == null && httpsServer == null) {
            Log.e(TAG, "Failed to start any web server")
            return
        }
        _state.value = WebServerState(
            running = true, httpUrl = httpUrl, httpsUrl = httpsUrl, castBaseUrl = castBaseUrl,
        )
        Log.i(TAG, "Web server started http=$httpUrl https=$httpsUrl castBase=$castBaseUrl")
        ContextCompat.startForegroundService(context, Intent(context, WebServerService::class.java))
    }

    /** Ensures the server is running and returns a tokenized cast URL for the
     *  media file, or null if no HTTP endpoint could be started. */
    fun castMediaUrl(id: Long): String? {
        if (state.value.castBaseUrl == null) start()
        return state.value.castBaseUrl?.let { "$it/media/$id?token=$castToken" }
    }

    fun castArtUrl(id: Long): String? =
        state.value.castBaseUrl?.let { "$it/thumb/$id?token=$castToken" }

    @Synchronized
    fun stop() {
        httpServer?.stop(); httpServer = null
        httpsServer?.stop(); httpsServer = null
        _state.value = WebServerState()
        context.stopService(Intent(context, WebServerService::class.java))
        Log.i(TAG, "Web server stopped")
    }

    private fun restart() {
        stop(); start()
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
        const val HTTP_PORT = 8080
        const val HTTPS_PORT = 8443
    }
}
