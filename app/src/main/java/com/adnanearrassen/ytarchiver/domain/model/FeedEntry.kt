package com.adnanearrassen.ytarchiver.domain.model

/**
 * A single row in the Home/Library feed — either a standalone media item or a
 * playlist. Carries common sort keys so playlists and videos can be sorted
 * together (by date added/created, name, or size) rather than grouped apart.
 */
sealed interface FeedEntry {
    val sortName: String
    val sortTime: Long
    val sortSize: Long

    data class Video(val media: ArchivedMedia) : FeedEntry {
        override val sortName get() = media.title
        override val sortTime get() = media.addedAt
        override val sortSize get() = media.fileSizeBytes
    }

    data class PlaylistRow(val playlist: Playlist) : FeedEntry {
        override val sortName get() = playlist.name
        override val sortTime get() = playlist.createdAt
        // Playlists have no single file size; keep them out of the way of the
        // "Largest first" ordering.
        override val sortSize get() = 0L
    }
}
