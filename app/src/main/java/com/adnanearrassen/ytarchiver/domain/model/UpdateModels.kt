package com.adnanearrassen.ytarchiver.domain.model

/** State of the yt-dlp engine self-update system. */
data class EngineVersionInfo(
    val installedVersion: String?,
    val latestVersion: String?,
    val changelog: String?,
    val updateAvailable: Boolean,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val info: EngineVersionInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class UpToDate(val version: String?) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Generic wrapper for operations that can fail (analysis, updates, etc.). */
sealed interface OpResult<out T> {
    data class Success<T>(val data: T) : OpResult<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        /** True when the failure is permanent (retrying can never succeed). */
        val permanent: Boolean = false,
    ) : OpResult<Nothing>
}

inline fun <T> OpResult<T>.onSuccess(block: (T) -> Unit): OpResult<T> {
    if (this is OpResult.Success) block(data)
    return this
}

inline fun <T> OpResult<T>.onError(block: (String) -> Unit): OpResult<T> {
    if (this is OpResult.Error) block(message)
    return this
}
