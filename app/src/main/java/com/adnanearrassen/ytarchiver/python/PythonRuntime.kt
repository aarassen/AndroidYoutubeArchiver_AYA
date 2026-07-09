package com.adnanearrassen.ytarchiver.python

import android.content.Context
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the embedded CPython interpreter (Chaquopy). Starting Python loads native
 * libraries and does disk I/O, so it must NEVER run on the main thread (that
 * froze the UI on launch). [start] is @Synchronized and idempotent: call it from
 * a background thread at boot; any later caller (analyze/download on IO) blocks
 * here only until initialization is complete, never racing a half-started state.
 */
@Singleton
class PythonRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageLocator: StorageLocator,
) {
    @Volatile private var started = false

    @Synchronized
    fun start() {
        if (started) return
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        started = true
        // Let a self-updated yt-dlp (if any) shadow the bundled one.
        runCatching {
            archiverModule().callAttr("configure", storageLocator.engineDir().absolutePath)
        }
    }

    private fun python(): Python {
        if (!started) start()
        return Python.getInstance()
    }

    fun archiverModule(): PyObject = python().getModule("yt_archiver")

    /** Sets (or clears with null) the cookies.txt file yt-dlp uses for auth. */
    fun setCookies(path: String?) {
        runCatching { archiverModule().callAttr("set_cookies", path ?: "") }
    }

    /** Toggles retrying restricted downloads against alternate player clients. */
    fun setBypassEnabled(enabled: Boolean) {
        runCatching { archiverModule().callAttr("set_bypass_enabled", enabled) }
    }

    /** Version string of the currently-active yt-dlp, or null if unavailable. */
    fun engineVersion(): String? =
        runCatching { archiverModule().callAttr("engine_version")?.toString() }
            .getOrNull()
            ?.takeIf { it != "None" && it.isNotBlank() }
}
