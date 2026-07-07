package com.adnanearrassen.ytarchiver.domain.model

/**
 * An entry in the "Continue watching" shelf: either a standalone video/track
 * to resume, or a playlist to continue from its last-watched item.
 */
sealed interface ContinueItem {
    data class Video(val media: ArchivedMedia) : ContinueItem

    /** A playlist to continue — represented by its single latest-watched video
     *  ([resumeMedia]); tapping resumes the playlist from that video. */
    data class Playlist(
        val playlist: com.adnanearrassen.ytarchiver.domain.model.Playlist,
        val resumeMedia: ArchivedMedia,
    ) : ContinueItem
}
