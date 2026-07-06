package com.adnanearrassen.ytarchiver.core.common

/** Lightweight helpers for recognising and extracting YouTube URLs. */
object UrlUtils {

    private val YT_HOSTS = listOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtu.be", "music.youtube.com",
    )

    private val URL_REGEX = Regex("""https?://\S+""")

    fun isYouTubeUrl(text: String): Boolean {
        val url = firstUrlIn(text) ?: return false
        return YT_HOSTS.any { url.contains(it, ignoreCase = true) }
    }

    /** Extracts the first URL from shared text (share sheets often add captions). */
    fun firstUrlIn(text: String): String? =
        URL_REGEX.find(text.trim())?.value?.trimEnd('.', ',', ')', ']')

    fun isPlaylistUrl(url: String): Boolean =
        url.contains("list=", ignoreCase = true) || url.contains("/playlist", ignoreCase = true)
}
