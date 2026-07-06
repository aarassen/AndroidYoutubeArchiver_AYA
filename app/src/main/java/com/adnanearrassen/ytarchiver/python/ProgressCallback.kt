package com.adnanearrassen.ytarchiver.python

/**
 * Passed into the Python `download()` function; yt-dlp's progress hook calls
 * [onProgress] with a JSON payload on the Python thread. Keep the method simple
 * (single String arg) so Chaquopy can invoke it cleanly from Python.
 */
fun interface ProgressCallback {
    fun onProgress(json: String)
}
