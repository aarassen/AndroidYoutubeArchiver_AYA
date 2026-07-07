package com.adnanearrassen.ytarchiver.domain.model

/**
 * Result of analysing a channel URL: its videos tab plus the playlists it
 * publishes, so the UI can offer a "pick what to download" confirmation screen.
 */
data class ChannelPreview(
    val name: String,
    val thumbnailUrl: String?,
    val videoCount: Int,
    /** The analysed channel-videos playlist, ready to enqueue as a whole. */
    val videos: MediaInfo?,
    val playlists: List<ChannelPlaylistPreview>,
)

data class ChannelPlaylistPreview(
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
)
