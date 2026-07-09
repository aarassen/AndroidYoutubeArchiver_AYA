package com.adnanearrassen.ytarchiver.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.server.WebServerManager
import com.adnanearrassen.ytarchiver.ui.navigation.Routes
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val libraryRepository: LibraryRepository,
    private val webServerManager: WebServerManager,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val startMediaId: Long =
        savedStateHandle.get<String>(Routes.ARG_MEDIA_ID)?.toLongOrNull() ?: -1L
    private val playlistId: Long? =
        savedStateHandle.get<String>(Routes.ARG_PLAYLIST_ID)?.toLongOrNull()?.takeIf { it > 0 }

    /** The local ExoPlayer (renders to the on-screen surface). */
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    // --- Cast (Chromecast) ---------------------------------------------------
    // CastContext requires Google Play services; guard so cast-less devices work.
    private val castContext: CastContext? =
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    private val castPlayer: CastPlayer? = castContext?.let { CastPlayer(it) }

    /** Whether this device can cast at all (shows/hides the cast button). */
    val castAvailable: Boolean = castPlayer != null

    /** The player the UI should drive — local, or the Cast player while casting. */
    private val _activePlayer = MutableStateFlow<Player>(player)
    val activePlayer: StateFlow<Player> = _activePlayer.asStateFlow()

    /** Friendly device name while casting, else null. */
    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val _media = MutableStateFlow<ArchivedMedia?>(null)
    val media: StateFlow<ArchivedMedia?> = _media.asStateFlow()

    /** Whether the current item has downloaded subtitle sidecar files. */
    private val _hasSubtitles = MutableStateFlow(false)
    val hasSubtitles: StateFlow<Boolean> = _hasSubtitles.asStateFlow()

    /** Playlist navigation state (multiple items in the queue). */
    private val _isPlaylist = MutableStateFlow(false)
    val isPlaylist: StateFlow<Boolean> = _isPlaylist.asStateFlow()
    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()
    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()

    /** The playback queue (a single item, or all items of a playlist in order). */
    private var queue: List<ArchivedMedia> = emptyList()
    private var trackedIndex = 0

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // On auto-advance, mark the finished item as fully watched.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                queue.getOrNull(trackedIndex)?.let { finished ->
                    viewModelScope.launch {
                        libraryRepository.updatePlaybackPosition(finished.id, finished.durationSeconds * 1000)
                    }
                }
            }
            trackedIndex = _activePlayer.value.currentMediaItemIndex
            queue.getOrNull(trackedIndex)?.let { item ->
                _media.value = item
                _hasSubtitles.value = subtitleFiles(item.filePath).isNotEmpty()
            }
            updateNavState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistCurrentPosition()
        }
    }

    private fun updateNavState() {
        _hasNext.value = _activePlayer.value.hasNextMediaItem()
        _hasPrevious.value = _activePlayer.value.hasPreviousMediaItem()
    }

    // --- Cast bridging -------------------------------------------------------

    private fun setupCast() {
        val cast = castPlayer ?: return
        cast.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() { viewModelScope.launch { transferToCast() } }
            override fun onCastSessionUnavailable() { transferToLocal() }
        })
        // Track title/nav changes when the queue auto-advances on the TV too.
        cast.addListener(listener)
    }

    /** Moves playback onto the TV: streams each item via the LAN web server. */
    private suspend fun transferToCast() {
        val cast = castPlayer ?: return
        if (queue.isEmpty()) return
        // Build tokenized URLs off the main thread (starts the server if needed).
        val items = withContext(io) {
            queue.mapNotNull { m -> webServerManager.castMediaUrl(m.id)?.let { m to it } }
        }
        if (items.size != queue.size) {
            // Couldn't produce a cast URL for every item (no HTTP endpoint). Stay
            // local rather than casting a partial/broken queue.
            return
        }
        val startIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val startPos = player.currentPosition.coerceAtLeast(0)
        player.pause()
        cast.setMediaItems(items.map { (m, url) -> castItemFor(m, url) }, startIndex, startPos)
        cast.playWhenReady = true
        cast.prepare()
        _activePlayer.value = cast
        _castDeviceName.value =
            castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: "TV"
        updateNavState()
    }

    /** Returns playback to the phone at the TV's current position. */
    private fun transferToLocal() {
        val cast = castPlayer ?: return
        val idx = cast.currentMediaItemIndex.coerceAtLeast(0)
        val pos = cast.currentPosition.coerceAtLeast(0)
        cast.stop()
        player.seekTo(idx, pos)
        player.playWhenReady = true
        _activePlayer.value = player
        _castDeviceName.value = null
        updateNavState()
    }

    private fun castItemFor(media: ArchivedMedia, mediaUrl: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(media.title)
            .setArtist(media.uploader)
            .apply { webServerManager.castArtUrl(media.id)?.let { setArtworkUri(Uri.parse(it)) } }
            .setMediaType(
                if (media.kind == MediaKind.MUSIC) MediaMetadata.MEDIA_TYPE_MUSIC
                else MediaMetadata.MEDIA_TYPE_VIDEO
            )
            .build()
        return MediaItem.Builder()
            .setUri(mediaUrl)
            .setMimeType(castMimeType(media))
            .setMediaMetadata(meta)
            .build()
    }

    /** A concrete MIME type the Cast receiver understands, from the extension. */
    private fun castMimeType(media: ArchivedMedia): String {
        return when (File(media.filePath).extension.lowercase()) {
            "mp3" -> MimeTypes.AUDIO_MPEG
            "m4a", "aac" -> MimeTypes.AUDIO_AAC
            "opus", "ogg", "oga" -> MimeTypes.AUDIO_OPUS
            "flac" -> MimeTypes.AUDIO_FLAC
            "wav" -> MimeTypes.AUDIO_WAV
            "webm" -> MimeTypes.VIDEO_WEBM
            "mkv" -> MimeTypes.VIDEO_MATROSKA
            else -> if (media.kind == MediaKind.MUSIC) MimeTypes.AUDIO_MPEG else MimeTypes.VIDEO_MP4
        }
    }

    fun playNext() = _activePlayer.value.seekToNextMediaItem()
    fun playPrevious() = _activePlayer.value.seekToPreviousMediaItem()

    init {
        viewModelScope.launch {
            queue = if (playlistId != null) libraryRepository.getPlaylistItems(playlistId)
            else listOfNotNull(libraryRepository.getById(startMediaId))
            val validQueue = queue.filter { File(it.filePath).exists() }
            queue = validQueue
            if (queue.isEmpty()) return@launch

            val startIndex = queue.indexOfFirst { it.id == startMediaId }.let { if (it >= 0) it else 0 }
            trackedIndex = startIndex
            val startItem = queue[startIndex]

            player.setMediaItems(queue.map { buildMediaItem(it) }, startIndex, startItem.playbackPositionMs)
            player.prepare()
            player.playWhenReady = true

            _media.value = startItem
            _hasSubtitles.value = subtitleFiles(startItem.filePath).isNotEmpty()
            _isPlaylist.value = queue.size > 1
            updateNavState()
            player.addListener(listener)

            // If a Cast session is already live when the player opens, hand off.
            setupCast()
            if (castPlayer?.isCastSessionAvailable == true) transferToCast()
        }

        // Persist the current item's position periodically.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                persistCurrentPosition()
            }
        }
    }

    /** Show or hide subtitles for the current item. */
    fun setSubtitlesEnabled(enabled: Boolean) {
        val lang = _media.value?.let { subtitleFiles(it.filePath).firstOrNull()?.second }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .setPreferredTextLanguage(if (enabled) lang else null)
            .build()
    }

    private fun buildMediaItem(media: ArchivedMedia): MediaItem {
        val builder = MediaItem.Builder().setUri(File(media.filePath).toURI().toString())
        val subs = subtitleFiles(media.filePath).map { (file, lang) ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(mimeFor(file))
                .setLanguage(lang)
                .build()
        }
        if (subs.isNotEmpty()) builder.setSubtitleConfigurations(subs)
        return builder.build()
    }

    /** Finds sidecar subtitle files next to the media (e.g. "Title.en.srt"). */
    private fun subtitleFiles(mediaPath: String): List<Pair<File, String?>> {
        val f = File(mediaPath)
        val base = f.nameWithoutExtension
        val dir = f.parentFile ?: return emptyList()
        return dir.listFiles { file ->
            file.isFile &&
                (file.extension.equals("srt", true) || file.extension.equals("vtt", true)) &&
                file.name.startsWith("$base.")
        }?.map { sub ->
            val lang = sub.name.removePrefix("$base.").substringBeforeLast('.').ifBlank { null }
            sub to lang
        } ?: emptyList()
    }

    private fun mimeFor(file: File): String =
        if (file.extension.equals("vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP

    private fun persistCurrentPosition() {
        val item = queue.getOrNull(trackedIndex) ?: _media.value ?: return
        val pos = _activePlayer.value.currentPosition
        viewModelScope.launch { libraryRepository.updatePlaybackPosition(item.id, pos) }
    }

    override fun onCleared() {
        persistCurrentPosition()
        player.removeListener(listener)
        player.release()
        // Releasing the CastPlayer does NOT end the Cast session — playback
        // keeps going on the TV after the player screen is closed.
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.removeListener(listener)
        castPlayer?.release()
        super.onCleared()
    }
}
