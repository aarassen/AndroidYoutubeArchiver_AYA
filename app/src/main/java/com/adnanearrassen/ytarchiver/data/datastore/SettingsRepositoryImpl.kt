package com.adnanearrassen.ytarchiver.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.model.AudioBitrate
import com.adnanearrassen.ytarchiver.domain.model.AudioFormat
import com.adnanearrassen.ytarchiver.domain.model.Container
import com.adnanearrassen.ytarchiver.domain.model.DownloadType
import com.adnanearrassen.ytarchiver.domain.model.FrameRate
import com.adnanearrassen.ytarchiver.domain.model.Resolution
import com.adnanearrassen.ytarchiver.domain.model.SampleRate
import com.adnanearrassen.ytarchiver.domain.model.ThemeColor
import com.adnanearrassen.ytarchiver.domain.model.ThemeMode
import com.adnanearrassen.ytarchiver.domain.model.VideoAudioCodec
import com.adnanearrassen.ytarchiver.domain.model.VideoCodec
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [SettingsRepository]. Enums are stored by name; a defensive
 * [enumOr] falls back to defaults if a stored value is ever unrecognised.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        dataStore.edit { prefs -> next.writeInto(prefs) }
    }

    // --- Mapping -----------------------------------------------------------

    private fun Preferences.toAppSettings() = AppSettings(
        videoResolution = enumOr(this[K.videoResolution], Resolution.P1080),
        videoFrameRate = enumOr(this[K.videoFrameRate], FrameRate.ANY),
        videoCodec = enumOr(this[K.videoCodec], VideoCodec.ANY),
        videoAudioCodec = enumOr(this[K.videoAudioCodec], VideoAudioCodec.ANY),
        videoContainer = enumOr(this[K.videoContainer], Container.MP4),
        preferHdr = this[K.preferHdr] ?: false,
        downloadSubtitles = this[K.downloadSubtitles] ?: false,
        embedSubtitles = this[K.embedSubtitles] ?: false,
        downloadThumbnail = this[K.downloadThumbnail] ?: true,
        embedThumbnail = this[K.embedThumbnail] ?: true,
        embedMetadata = this[K.embedMetadata] ?: true,
        smartFallback = this[K.smartFallback] ?: true,
        audioFormat = enumOr(this[K.audioFormat], AudioFormat.MP3),
        audioBitrate = enumOr(this[K.audioBitrate], AudioBitrate.K256),
        audioSampleRate = enumOr(this[K.audioSampleRate], SampleRate.SOURCE),
        embedAlbumArt = this[K.embedAlbumArt] ?: true,
        embedMusicMetadata = this[K.embedMusicMetadata] ?: true,
        normalizeVolume = this[K.normalizeVolume] ?: false,
        askDownloadTypeEveryTime = this[K.askType] ?: true,
        defaultDownloadType = enumOr(this[K.defaultType], DownloadType.VIDEO),
        rememberLastDownloadType = this[K.rememberType] ?: true,
        lastDownloadType = enumOr(this[K.lastType], DownloadType.VIDEO),
        autoStartAfterAnalysis = this[K.autoStart] ?: false,
        maxSimultaneousDownloads = this[K.maxSimul] ?: 2,
        maxRetries = this[K.maxRetries] ?: 3,
        retryIntervalSeconds = this[K.retryInterval] ?: 10,
        autoRetryFailed = this[K.autoRetry] ?: true,
        autoResumeInterrupted = this[K.autoResume] ?: true,
        wifiOnly = this[K.wifiOnly] ?: false,
        allowCellular = this[K.allowCellular] ?: true,
        batterySaver = this[K.batterySaver] ?: false,
        downloadPath = this[K.downloadPath],
        deleteSourceAfterConversion = this[K.deleteSource] ?: true,
        autoUpdateYtDlp = this[K.autoUpdateYtDlp] ?: true,
        autoUpdateFfmpeg = this[K.autoUpdateFfmpeg] ?: false,
        webServerPassword = this[K.webServerPassword] ?: "",
        webServerHttpsEnabled = this[K.webServerHttpsEnabled] ?: false,
        webServerHttpsOnly = this[K.webServerHttpsOnly] ?: false,
        themeMode = enumOr(this[K.themeMode], ThemeMode.SYSTEM),
        themeColor = enumOr(this[K.themeColor], ThemeColor.RED),
        dynamicColor = this[K.dynamicColor] ?: false,
        languageTag = this[K.language] ?: "system",
    )

    private fun AppSettings.writeInto(p: androidx.datastore.preferences.core.MutablePreferences) {
        p[K.videoResolution] = videoResolution.name
        p[K.videoFrameRate] = videoFrameRate.name
        p[K.videoCodec] = videoCodec.name
        p[K.videoAudioCodec] = videoAudioCodec.name
        p[K.videoContainer] = videoContainer.name
        p[K.preferHdr] = preferHdr
        p[K.downloadSubtitles] = downloadSubtitles
        p[K.embedSubtitles] = embedSubtitles
        p[K.downloadThumbnail] = downloadThumbnail
        p[K.embedThumbnail] = embedThumbnail
        p[K.embedMetadata] = embedMetadata
        p[K.smartFallback] = smartFallback
        p[K.audioFormat] = audioFormat.name
        p[K.audioBitrate] = audioBitrate.name
        p[K.audioSampleRate] = audioSampleRate.name
        p[K.embedAlbumArt] = embedAlbumArt
        p[K.embedMusicMetadata] = embedMusicMetadata
        p[K.normalizeVolume] = normalizeVolume
        p[K.askType] = askDownloadTypeEveryTime
        p[K.defaultType] = defaultDownloadType.name
        p[K.rememberType] = rememberLastDownloadType
        p[K.lastType] = lastDownloadType.name
        p[K.autoStart] = autoStartAfterAnalysis
        p[K.maxSimul] = maxSimultaneousDownloads
        p[K.maxRetries] = maxRetries
        p[K.retryInterval] = retryIntervalSeconds
        p[K.autoRetry] = autoRetryFailed
        p[K.autoResume] = autoResumeInterrupted
        p[K.wifiOnly] = wifiOnly
        p[K.allowCellular] = allowCellular
        p[K.batterySaver] = batterySaver
        downloadPath?.let { p[K.downloadPath] = it }
        p[K.deleteSource] = deleteSourceAfterConversion
        p[K.autoUpdateYtDlp] = autoUpdateYtDlp
        p[K.autoUpdateFfmpeg] = autoUpdateFfmpeg
        p[K.webServerPassword] = webServerPassword
        p[K.webServerHttpsEnabled] = webServerHttpsEnabled
        p[K.webServerHttpsOnly] = webServerHttpsOnly
        p[K.themeMode] = themeMode.name
        p[K.themeColor] = themeColor.name
        p[K.dynamicColor] = dynamicColor
        p[K.language] = languageTag
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private object K {
        val videoResolution = stringPreferencesKey("video_resolution")
        val videoFrameRate = stringPreferencesKey("video_frame_rate")
        val videoCodec = stringPreferencesKey("video_codec")
        val videoAudioCodec = stringPreferencesKey("video_audio_codec")
        val videoContainer = stringPreferencesKey("video_container")
        val preferHdr = booleanPreferencesKey("prefer_hdr")
        val downloadSubtitles = booleanPreferencesKey("download_subtitles")
        val embedSubtitles = booleanPreferencesKey("embed_subtitles")
        val downloadThumbnail = booleanPreferencesKey("download_thumbnail")
        val embedThumbnail = booleanPreferencesKey("embed_thumbnail")
        val embedMetadata = booleanPreferencesKey("embed_metadata")
        val smartFallback = booleanPreferencesKey("smart_fallback")
        val audioFormat = stringPreferencesKey("audio_format")
        val audioBitrate = stringPreferencesKey("audio_bitrate")
        val audioSampleRate = stringPreferencesKey("audio_sample_rate")
        val embedAlbumArt = booleanPreferencesKey("embed_album_art")
        val embedMusicMetadata = booleanPreferencesKey("embed_music_metadata")
        val normalizeVolume = booleanPreferencesKey("normalize_volume")
        val askType = booleanPreferencesKey("ask_type")
        val defaultType = stringPreferencesKey("default_type")
        val rememberType = booleanPreferencesKey("remember_type")
        val lastType = stringPreferencesKey("last_type")
        val autoStart = booleanPreferencesKey("auto_start")
        val maxSimul = intPreferencesKey("max_simultaneous")
        val maxRetries = intPreferencesKey("max_retries")
        val retryInterval = intPreferencesKey("retry_interval")
        val autoRetry = booleanPreferencesKey("auto_retry")
        val autoResume = booleanPreferencesKey("auto_resume")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val allowCellular = booleanPreferencesKey("allow_cellular")
        val batterySaver = booleanPreferencesKey("battery_saver")
        val downloadPath = stringPreferencesKey("download_path")
        val deleteSource = booleanPreferencesKey("delete_source")
        val autoUpdateYtDlp = booleanPreferencesKey("auto_update_ytdlp")
        val autoUpdateFfmpeg = booleanPreferencesKey("auto_update_ffmpeg")
        val webServerPassword = stringPreferencesKey("web_server_password")
        val webServerHttpsEnabled = booleanPreferencesKey("web_server_https_enabled")
        val webServerHttpsOnly = booleanPreferencesKey("web_server_https_only")
        val themeMode = stringPreferencesKey("theme_mode")
        val themeColor = stringPreferencesKey("theme_color")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val language = stringPreferencesKey("language")
    }
}
