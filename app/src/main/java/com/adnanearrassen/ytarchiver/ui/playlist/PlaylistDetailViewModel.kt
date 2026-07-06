package com.adnanearrassen.ytarchiver.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import com.adnanearrassen.ytarchiver.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: Long =
        savedStateHandle.get<String>(Routes.ARG_PLAYLIST_ID)?.toLongOrNull() ?: -1L

    val playlist: StateFlow<Playlist?> = libraryRepository.observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Items in playlist order (indexed). */
    val items: StateFlow<List<ArchivedMedia>> = libraryRepository.observePlaylistItems(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        libraryRepository.toggleFavorite(id)
    }

    fun delete(id: Long) = viewModelScope.launch {
        libraryRepository.delete(id, deleteFile = true)
    }
}
