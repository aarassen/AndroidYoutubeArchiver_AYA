package com.adnanearrassen.ytarchiver.python

import com.adnanearrassen.ytarchiver.domain.model.DownloadProgress
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parses the JSON progress payloads emitted by the Python download hook. */
object ProgressParser {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Payload(
        val status: String = "DOWNLOADING",
        val progress: Float = 0f,
        val speed: Long = 0,
        val downloaded: Long = 0,
        val total: Long = 0,
        val eta: Long = 0,
        val filename: String? = null,
    )

    fun parse(raw: String): DownloadProgress {
        val p = json.decodeFromString(Payload.serializer(), raw)
        val status = runCatching { DownloadStatus.valueOf(p.status) }
            .getOrDefault(DownloadStatus.DOWNLOADING)
        return DownloadProgress(
            status = status,
            progress = p.progress.coerceIn(0f, 1f),
            speedBytesPerSec = p.speed,
            downloadedBytes = p.downloaded,
            totalBytes = p.total,
            etaSeconds = p.eta,
            filename = p.filename,
        )
    }
}
