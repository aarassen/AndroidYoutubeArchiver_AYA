package com.adnanearrassen.ytarchiver.python

import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.MediaInfo
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.python.dto.AnalyzeDto
import com.adnanearrassen.ytarchiver.python.dto.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a completed Python download. */
@Serializable
data class DownloadResultDto(
    val error: String? = null,
    /** True when the engine knows the URL can never succeed (private/removed/
     *  region/age/members-only) — the worker skips retrying it. */
    val unavailable: Boolean = false,
    val filePath: String = "",
    val fileSizeBytes: Long = 0,
    val title: String? = null,
    val uploader: String? = null,
    val durationSeconds: Long = 0,
    val id: String? = null,
)

/**
 * Thin, coroutine-friendly wrapper over the `yt_archiver` Python module. All
 * calls hop to the IO dispatcher because Chaquopy calls are blocking.
 */
@Singleton
class YtDlpService @Inject constructor(
    private val runtime: PythonRuntime,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun analyze(url: String): OpResult<MediaInfo> = withContext(io) {
        try {
            val raw = runtime.archiverModule().callAttr("analyze", url).toString()
            val dto = json.decodeFromString(AnalyzeDto.serializer(), raw)
            if (dto.error != null) OpResult.Error(dto.error)
            else OpResult.Success(dto.toDomain())
        } catch (t: Throwable) {
            OpResult.Error(t.message ?: "Analysis failed", t)
        }
    }

    /**
     * Runs a blocking download. [onProgress] is invoked repeatedly from the
     * Python thread with parsed progress. Returns the finished file result.
     */
    suspend fun download(
        url: String,
        options: DownloadOptions,
        outputDir: String,
        onProgress: (com.adnanearrassen.ytarchiver.domain.model.DownloadProgress) -> Unit,
    ): OpResult<DownloadResultDto> = withContext(io) {
        try {
            val optionsJson = OptionsTranslator.toJsonString(options)
            val callback = ProgressCallback { payload ->
                runCatching {
                    onProgress(ProgressParser.parse(payload))
                }
            }
            val raw = runtime.archiverModule()
                .callAttr("download", url, optionsJson, outputDir, callback)
                .toString()
            val dto = json.decodeFromString(DownloadResultDto.serializer(), raw)
            if (dto.error != null) OpResult.Error(dto.error, permanent = dto.unavailable)
            else OpResult.Success(dto)
        } catch (t: Throwable) {
            OpResult.Error(t.message ?: "Download failed", t)
        }
    }
}
