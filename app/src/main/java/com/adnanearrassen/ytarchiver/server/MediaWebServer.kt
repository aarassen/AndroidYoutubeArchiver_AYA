package com.adnanearrassen.ytarchiver.server

import android.content.Context
import android.util.Log
import com.adnanearrassen.ytarchiver.core.common.ApplicationScope
import com.adnanearrassen.ytarchiver.data.local.dao.MediaDao
import com.adnanearrassen.ytarchiver.data.local.dao.PlaylistDao
import android.util.Base64
import com.adnanearrassen.ytarchiver.domain.model.ContinueItem
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.domain.repository.MediaAnalyzer
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.domain.usecase.DefaultOptions
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.FileInputStream

/**
 * Embedded HTTP server that exposes the offline archive over the LAN:
 *  - a YouTube-like web UI (served from assets/web),
 *  - JSON APIs for the library / playlists / download queue,
 *  - byte-range media streaming so videos/music seek in the browser,
 *  - a download-control endpoint to queue new YouTube URLs.
 */
class MediaWebServer(
    port: Int,
    private val context: Context,
    private val mediaDao: MediaDao,
    private val playlistDao: PlaylistDao,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val analyzer: MediaAnalyzer,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    /** Shared secret that lets a Chromecast fetch /media & /thumb without the
     *  Basic-auth password (which a Cast device can't send). */
    private val castToken: String,
    /** When true this instance ONLY serves tokenized /media & /thumb — used for
     *  the plain-HTTP endpoint while the web UI is forced to HTTPS-only. */
    private val castOnly: Boolean = false,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            val castRequest = isCastMediaRequest(uri) && hasValidCastToken(session)
            // Cast-only endpoint: reject everything that isn't a tokenized fetch.
            if (castOnly && !castRequest) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "HTTPS only")
            }
            if (!castRequest && !isAuthorized(session)) return unauthorized()
            when {
                uri == "/" || uri == "/index.html" -> asset("web/index.html", "text/html")
                uri == "/app.js" -> asset("web/app.js", "application/javascript")
                uri == "/style.css" -> asset("web/style.css", "text/css")
                uri == "/api/library" -> json(libraryJson())
                uri == "/api/continue" -> json(continueJson())
                uri == "/api/playlists" -> json(playlistsJson())
                uri == "/api/queue" -> json(queueJson())
                uri == "/api/download" -> handleDownload(session)
                uri == "/api/progress/clear" -> handleClearProgress(session)
                uri.startsWith("/media/") -> streamMedia(uri.removePrefix("/media/"), session)
                uri.startsWith("/thumb/") -> streamThumb(uri.removePrefix("/thumb/"))
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "serve error", t)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", t.message ?: "error")
        }
    }

    // --- Auth --------------------------------------------------------------

    private fun isCastMediaRequest(uri: String): Boolean =
        uri.startsWith("/media/") || uri.startsWith("/thumb/")

    private fun hasValidCastToken(session: IHTTPSession): Boolean {
        val token = session.parameters["token"]?.firstOrNull() ?: return false
        return token.isNotEmpty() && token == castToken
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val password = runBlocking { settingsRepository.settings.first().webServerPassword }
        if (password.isBlank()) return true
        val header = session.headers["authorization"] ?: return false
        if (!header.startsWith("Basic ", ignoreCase = true)) return false
        val decoded = runCatching {
            String(Base64.decode(header.substring(6).trim(), Base64.DEFAULT))
        }.getOrNull() ?: return false
        // "username:password" — any username, the password must match.
        return decoded.substringAfter(':', "") == password
    }

    private fun unauthorized(): Response {
        val res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Authentication required")
        res.addHeader("WWW-Authenticate", "Basic realm=\"YT Archiver\"")
        return res
    }

    // --- API payloads ------------------------------------------------------

    private fun libraryJson(): String {
        val media = runBlocking { mediaDao.getStandaloneOnce() }
        val arr = buildJsonArray {
            media.forEach { m ->
                add(buildJsonObject {
                    put("id", m.id)
                    put("title", m.title)
                    put("uploader", m.uploader ?: "")
                    put("kind", m.kind.name)
                    put("durationSeconds", m.durationSeconds)
                    put("sizeBytes", m.fileSizeBytes)
                    put("positionMs", m.playbackPositionMs)
                    put("hasThumb", m.thumbnailPath != null)
                })
            }
        }
        return arr.toString()
    }

    private fun continueJson(): String {
        val items = runBlocking { libraryRepository.observeContinueItems(20).first() }
        val arr = buildJsonArray {
            items.forEach { entry ->
                when (entry) {
                    is ContinueItem.Video -> add(buildJsonObject {
                        put("type", "video")
                        put("id", entry.media.id)
                        put("title", entry.media.title)
                        put("uploader", entry.media.uploader ?: "")
                        put("kind", entry.media.kind.name)
                        put("durationSeconds", entry.media.durationSeconds)
                        put("positionMs", entry.media.playbackPositionMs)
                        put("progress", entry.media.watchProgress)
                        put("hasThumb", entry.media.thumbnailPath != null)
                    })
                    is ContinueItem.Playlist -> add(buildJsonObject {
                        put("type", "playlist")
                        put("playlistId", entry.playlist.id)
                        put("playlistName", entry.playlist.name)
                        put("id", entry.resumeMedia.id)
                        put("title", entry.resumeMedia.title)
                        put("uploader", entry.resumeMedia.uploader ?: "")
                        put("kind", entry.resumeMedia.kind.name)
                        put("durationSeconds", entry.resumeMedia.durationSeconds)
                        put("positionMs", entry.resumeMedia.playbackPositionMs)
                        put("progress", entry.resumeMedia.watchProgress)
                        put("hasThumb", entry.resumeMedia.thumbnailPath != null)
                    })
                }
            }
        }
        return arr.toString()
    }

    private fun playlistsJson(): String {
        val playlists = runBlocking { playlistDao.getPlaylistsWithStatsOnce() }
        val arr = buildJsonArray {
            playlists.forEach { p ->
                val items = runBlocking { playlistDao.getItems(p.id) }
                val cover = items.firstOrNull { it.thumbnailPath != null } ?: items.firstOrNull()
                add(buildJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    put("itemCount", p.itemCount)
                    put("durationSeconds", p.totalDurationSeconds)
                    put("coverId", cover?.id ?: -1L)
                    put("coverHasThumb", cover?.thumbnailPath != null)
                    putJsonArray("items") {
                        items.forEach { m ->
                            add(buildJsonObject {
                                put("id", m.id)
                                put("title", m.title)
                                put("uploader", m.uploader ?: "")
                                put("kind", m.kind.name)
                                put("durationSeconds", m.durationSeconds)
                                put("positionMs", m.playbackPositionMs)
                                put("hasThumb", m.thumbnailPath != null)
                            })
                        }
                    }
                })
            }
        }
        return arr.toString()
    }

    private fun queueJson(): String {
        val queue = runBlocking { downloadRepository.observeQueue().first() }
        val arr = buildJsonArray {
            queue.forEach { d ->
                add(buildJsonObject {
                    put("title", d.title)
                    put("status", d.status.name)
                    put("percent", d.percent)
                    put("type", d.type.name)
                })
            }
        }
        return arr.toString()
    }

    // --- Download control --------------------------------------------------

    private fun handleDownload(session: IHTTPSession): Response {
        runCatching { session.parseBody(HashMap()) }
        val url = session.parameters["url"]?.firstOrNull()?.trim()
        val type = session.parameters["type"]?.firstOrNull()?.lowercase() ?: "video"
        if (url.isNullOrBlank()) {
            return json("""{"ok":false,"error":"url required"}""", Response.Status.BAD_REQUEST)
        }
        appScope.launch {
            runCatching {
                val settings = settingsRepository.settings.first()
                when (val result = analyzer.analyze(url)) {
                    is OpResult.Success -> {
                        val info = result.data
                        val options: DownloadOptions =
                            if (type == "music") DefaultOptions.music(settings)
                            else DefaultOptions.video(settings)
                        if (info.isPlaylist) downloadRepository.enqueuePlaylist(info, options)
                        else downloadRepository.enqueue(
                            info.url, info.title, info.uploader, info.thumbnailUrl, options,
                        )
                    }
                    is OpResult.Error -> Log.w(TAG, "web download analyze failed: ${result.message}")
                }
            }
        }
        return json("""{"ok":true,"status":"queued"}""")
    }

    private fun handleClearProgress(session: IHTTPSession): Response {
        runCatching { session.parseBody(HashMap()) }
        val id = session.parameters["id"]?.firstOrNull()?.toLongOrNull()
        val playlistId = session.parameters["playlistId"]?.firstOrNull()?.toLongOrNull()
        appScope.launch {
            runCatching {
                if (playlistId != null) libraryRepository.clearPlaylistWatchProgress(playlistId)
                else if (id != null) libraryRepository.clearWatchProgress(id)
            }
        }
        return json("""{"ok":true}""")
    }

    // --- File streaming ----------------------------------------------------

    private fun streamMedia(idPart: String, session: IHTTPSession): Response {
        val id = idPart.toLongOrNull() ?: return notFound()
        val media = runBlocking { mediaDao.getById(id) } ?: return notFound()
        val file = File(media.filePath)
        if (!file.exists()) return notFound()
        return serveFileWithRange(file, mimeOf(file), session)
    }

    private fun streamThumb(idPart: String): Response {
        val id = idPart.toLongOrNull() ?: return notFound()
        val media = runBlocking { mediaDao.getById(id) } ?: return notFound()
        val path = media.thumbnailPath ?: return notFound()
        val file = File(path)
        if (!file.exists()) return notFound()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", FileInputStream(file), file.length())
    }

    /** Serves a file honouring the Range header (206 Partial Content) so the
     *  browser's <video>/<audio> can seek. */
    private fun serveFileWithRange(file: File, mime: String, session: IHTTPSession): Response {
        val fileLength = file.length()
        val range = session.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.substring(6).split("-")
            val start = spec.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = spec.getOrNull(1)?.toLongOrNull()?.takeIf { it in start until fileLength }
                ?: (fileLength - 1)
            val length = end - start + 1
            val stream = FileInputStream(file).apply { skip(start) }
            val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, length)
            res.addHeader("Accept-Ranges", "bytes")
            res.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            return res
        }
        val res = newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), fileLength)
        res.addHeader("Accept-Ranges", "bytes")
        return res
    }

    // --- Helpers -----------------------------------------------------------

    private fun asset(path: String, mime: String): Response =
        runCatching {
            val bytes = context.assets.open(path).use { it.readBytes() }
            newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong())
        }.getOrElse { notFound() }

    private fun json(body: String, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json", body)

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    companion object {
        private const val TAG = "MediaWebServer"
        const val DEFAULT_PORT = 8080
    }
}

/** Convenience so callers can treat MediaKind uniformly. */
fun MediaKind.isAudio(): Boolean = this == MediaKind.MUSIC
