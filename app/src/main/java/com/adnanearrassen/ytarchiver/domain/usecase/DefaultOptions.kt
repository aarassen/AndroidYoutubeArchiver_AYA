package com.adnanearrassen.ytarchiver.domain.usecase

import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions

/**
 * Derives the concrete per-download options from the user's saved defaults.
 * The Advanced Options sheet starts from these and lets the user override.
 */
object DefaultOptions {

    fun video(settings: AppSettings): DownloadOptions.Video = DownloadOptions.Video(
        resolution = settings.videoResolution,
        frameRate = settings.videoFrameRate,
        videoCodec = settings.videoCodec,
        audioCodec = settings.videoAudioCodec,
        container = settings.videoContainer,
        preferHdr = settings.preferHdr,
        smartFallback = settings.smartFallback,
        downloadSubtitles = settings.downloadSubtitles,
        embedSubtitles = settings.embedSubtitles,
        downloadThumbnail = settings.downloadThumbnail,
        embedThumbnail = settings.embedThumbnail,
        embedMetadata = settings.embedMetadata,
    )

    fun music(settings: AppSettings): DownloadOptions.Music = DownloadOptions.Music(
        format = settings.audioFormat,
        bitrate = settings.audioBitrate,
        sampleRate = settings.audioSampleRate,
        embedThumbnail = settings.embedAlbumArt,
        embedMetadata = settings.embedMusicMetadata,
        normalizeVolume = settings.normalizeVolume,
    )
}
