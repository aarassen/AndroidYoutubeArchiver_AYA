package com.adnanearrassen.ytarchiver.python.update

import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.domain.model.EngineVersionInfo
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.domain.model.UpdateState
import com.adnanearrassen.ytarchiver.domain.repository.EngineUpdateRepository
import com.adnanearrassen.ytarchiver.network.GithubReleaseApi
import com.adnanearrassen.ytarchiver.python.PythonRuntime
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the embedded yt-dlp up to date without reinstalling the app.
 *
 * Strategy: yt-dlp ships a self-contained *zipapp* asset named `yt-dlp` on each
 * GitHub release. We download it into [StorageLocator.engineDir]; on the next
 * `PythonRuntime.configure()` it is prepended to `sys.path`, so `import yt_dlp`
 * resolves to the newer code, shadowing the version bundled at build time.
 */
@Singleton
class EngineUpdateRepositoryImpl @Inject constructor(
    private val api: GithubReleaseApi,
    private val runtime: PythonRuntime,
    private val storageLocator: StorageLocator,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EngineUpdateRepository {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state = _state.asStateFlow()

    override suspend fun installedVersion(): String? = withContext(io) {
        runtime.engineVersion()
    }

    override suspend fun checkForUpdates(): OpResult<EngineVersionInfo> = withContext(io) {
        _state.value = UpdateState.Checking
        try {
            val release = api.latestRelease(
                GithubReleaseApi.YT_DLP_OWNER,
                GithubReleaseApi.YT_DLP_REPO,
            )
            val installed = runtime.engineVersion()
            val latest = release.tagName.removePrefix("v")
            val updateAvailable = installed == null || compareVersions(latest, installed) > 0
            val info = EngineVersionInfo(
                installedVersion = installed,
                latestVersion = latest,
                changelog = release.body,
                updateAvailable = updateAvailable,
            )
            _state.value =
                if (updateAvailable) UpdateState.UpdateAvailable(info)
                else UpdateState.UpToDate(installed)
            OpResult.Success(info)
        } catch (t: Throwable) {
            _state.value = UpdateState.Failed(t.message ?: "Update check failed")
            OpResult.Error(t.message ?: "Update check failed", t)
        }
    }

    override suspend fun update(): OpResult<String> = withContext(io) {
        try {
            val release = api.latestRelease(
                GithubReleaseApi.YT_DLP_OWNER,
                GithubReleaseApi.YT_DLP_REPO,
            )
            val asset = release.assets.firstOrNull { it.name == "yt-dlp" }
                ?: return@withContext OpResult.Error("Release has no yt-dlp zipapp asset")

            _state.value = UpdateState.Downloading(0f)
            val target = File(storageLocator.engineDir(), "yt-dlp")
            downloadTo(asset.downloadUrl, target) { progress ->
                _state.value = UpdateState.Downloading(progress)
            }

            _state.value = UpdateState.Installing
            // Re-point sys.path at the freshly downloaded engine.
            runtime.start()
            val version = runtime.engineVersion()
            _state.value = UpdateState.UpToDate(version)
            OpResult.Success(version ?: release.tagName)
        } catch (t: Throwable) {
            _state.value = UpdateState.Failed(t.message ?: "Update failed")
            OpResult.Error(t.message ?: "Update failed", t)
        }
    }

    private fun downloadTo(url: String, target: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            val tmp = File(target.parentFile, target.name + ".part")
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    /** Compares dotted date/semantic versions like 2024.08.06 or 1.2.3. */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".", "-").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".", "-").mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
