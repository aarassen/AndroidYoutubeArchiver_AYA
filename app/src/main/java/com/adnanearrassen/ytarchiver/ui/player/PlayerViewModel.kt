package com.adnanearrassen.ytarchiver.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val libraryRepository: LibraryRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<String>(Routes.ARG_MEDIA_ID)?.toLongOrNull() ?: -1L

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _media = MutableStateFlow<ArchivedMedia?>(null)
    val media: StateFlow<ArchivedMedia?> = _media.asStateFlow()

    init {
        viewModelScope.launch {
            val item = libraryRepository.getById(mediaId) ?: return@launch
            _media.value = item
            val exists = withContext(io) { File(item.filePath).exists() }
            if (exists) {
                player.setMediaItem(MediaItem.fromUri(File(item.filePath).toURI().toString()))
                player.prepare()
                if (item.playbackPositionMs > 0) player.seekTo(item.playbackPositionMs)
                player.playWhenReady = true
            }
        }
    }

    private fun persistPosition() {
        val item = _media.value ?: return
        val position = player.currentPosition
        viewModelScope.launch {
            libraryRepository.updatePlaybackPosition(item.id, position)
        }
    }

    override fun onCleared() {
        persistPosition()
        player.release()
        super.onCleared()
    }
}
