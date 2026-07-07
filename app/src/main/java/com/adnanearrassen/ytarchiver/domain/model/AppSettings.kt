package com.adnanearrassen.ytarchiver.domain.model

/**
 * All user-configurable defaults, persisted via DataStore. Immutable snapshot;
 * the SettingsRepository exposes it as a Flow and offers granular updaters.
 */
data class AppSettings(
    // --- Video defaults ---
    val videoResolution: Resolution = Resolution.P1080,
    val videoFrameRate: FrameRate = FrameRate.ANY,
    val videoCodec: VideoCodec = VideoCodec.ANY,
    val videoAudioCodec: VideoAudioCodec = VideoAudioCodec.ANY,
    val videoContainer: Container = Container.MP4,
    val preferHdr: Boolean = false,
    val downloadSubtitles: Boolean = false,
    val embedSubtitles: Boolean = false,
    val downloadThumbnail: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val smartFallback: Boolean = true,

    // --- Music defaults ---
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val audioBitrate: AudioBitrate = AudioBitrate.K256,
    val audioSampleRate: SampleRate = SampleRate.SOURCE,
    val embedAlbumArt: Boolean = true,
    val embedMusicMetadata: Boolean = true,
    val normalizeVolume: Boolean = false,

    // --- Download behaviour ---
    val askDownloadTypeEveryTime: Boolean = true,
    val defaultDownloadType: DownloadType = DownloadType.VIDEO,
    val rememberLastDownloadType: Boolean = true,
    val lastDownloadType: DownloadType = DownloadType.VIDEO,
    val autoStartAfterAnalysis: Boolean = false,
    val maxSimultaneousDownloads: Int = 2,
    val maxRetries: Int = 3,
    val retryIntervalSeconds: Int = 10,
    val autoRetryFailed: Boolean = true,
    val autoResumeInterrupted: Boolean = true,

    // --- Network / power ---
    val wifiOnly: Boolean = false,
    val allowCellular: Boolean = true,
    val batterySaver: Boolean = false,

    // --- Storage ---
    val downloadPath: String? = null,        // null => app default media dir
    val deleteSourceAfterConversion: Boolean = true,

    // --- Updates ---
    val autoUpdateYtDlp: Boolean = true,
    val autoUpdateFfmpeg: Boolean = false,

    // --- Web server ---
    val webServerPassword: String = "",
    val webServerHttpsEnabled: Boolean = false,
    val webServerHttpsOnly: Boolean = false,

    // --- Appearance ---
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.RED,
    val dynamicColor: Boolean = false,
    val languageTag: String = "system",
)
