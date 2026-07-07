package com.adnanearrassen.ytarchiver.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val libraryRepository: LibraryRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val startMediaId: Long =
        savedStateHandle.get<String>(Routes.ARG_MEDIA_ID)?.toLongOrNull() ?: -1L
    private val playlistId: Long? =
        savedStateHandle.get<String>(Routes.ARG_PLAYLIST_ID)?.toLongOrNull()?.takeIf { it > 0 }

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

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
            trackedIndex = player.currentMediaItemIndex
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
        _hasNext.value = player.hasNextMediaItem()
        _hasPrevious.value = player.hasPreviousMediaItem()
    }

    fun playNext() = player.seekToNextMediaItem()
    fun playPrevious() = player.seekToPreviousMediaItem()

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
        val pos = player.currentPosition
        viewModelScope.launch { libraryRepository.updatePlaybackPosition(item.id, pos) }
    }

    override fun onCleared() {
        persistCurrentPosition()
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }
}
