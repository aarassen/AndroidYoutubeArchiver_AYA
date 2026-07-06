package com.adnanearrassen.ytarchiver.python

import android.content.Context
import com.adnanearrassen.ytarchiver.storage.StorageLocator
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the embedded CPython interpreter (Chaquopy). Starting Python is a
 * one-time, process-wide operation; this wrapper makes it idempotent and
 * exposes the `yt_archiver` module.
 */
@Singleton
class PythonRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageLocator: StorageLocator,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        // Let a self-updated yt-dlp (if any) shadow the bundled one.
        runCatching {
            archiverModule().callAttr("configure", storageLocator.engineDir().absolutePath)
        }
    }

    private fun python(): Python {
        if (!started.get()) start()
        return Python.getInstance()
    }

    fun archiverModule(): PyObject = python().getModule("yt_archiver")

    /** Version string of the currently-active yt-dlp, or null if unavailable. */
    fun engineVersion(): String? =
        runCatching { archiverModule().callAttr("engine_version")?.toString() }
            .getOrNull()
            ?.takeIf { it != "None" && it.isNotBlank() }
}
