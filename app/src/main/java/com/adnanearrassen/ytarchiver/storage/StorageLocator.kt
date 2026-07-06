package com.adnanearrassen.ytarchiver.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves where archived media, thumbnails and the Python working directory
 * live. Uses app-scoped external storage by default (no runtime storage
 * permission needed on modern Android), but honours a user-configured path.
 */
@Singleton
class StorageLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun mediaRoot(): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }

    fun videoDir(): File = File(mediaRoot(), "Videos").apply { mkdirs() }
    fun musicDir(): File = File(mediaRoot(), "Music").apply { mkdirs() }
    fun thumbnailDir(): File = File(mediaRoot(), "Thumbnails").apply { mkdirs() }

    /** Temp dir yt-dlp downloads into before we move the finished file. */
    fun tempDir(): File = File(context.cacheDir, "downloads").apply { mkdirs() }

    /** Directory used to cache the updatable yt-dlp release binary/wheel. */
    fun engineDir(): File = File(context.filesDir, "engine").apply { mkdirs() }

    /** Resolve a user-provided folder override, falling back to a default. */
    fun resolveOrDefault(userPath: String?, default: File): File =
        userPath?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } } ?: default
}
