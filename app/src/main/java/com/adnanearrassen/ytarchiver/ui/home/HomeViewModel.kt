package com.adnanearrassen.ytarchiver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.ContinueItem
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeChip(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
}

enum class SortOrder(val label: String) {
    RECENT("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name (A–Z)"),
    SIZE("Largest first");

    fun sort(items: List<ArchivedMedia>): List<ArchivedMedia> = when (this) {
        RECENT -> items.sortedByDescending { it.addedAt }
        OLDEST -> items.sortedBy { it.addedAt }
        NAME -> items.sortedBy { it.title.lowercase() }
        SIZE -> items.sortedByDescending { it.fileSizeBytes }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _chip = MutableStateFlow(HomeChip.ALL)
    val chip: StateFlow<HomeChip> = _chip.asStateFlow()

    private val _sort = MutableStateFlow(SortOrder.RECENT)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    /** "Continue watching" shelf: standalone videos + playlists-in-progress. */
    val continueWatching: StateFlow<List<ContinueItem>> =
        libraryRepository.observeContinueItems(12)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Downloaded playlists, shown as their own cards (not scattered videos). */
    val playlists: StateFlow<List<Playlist>> =
        libraryRepository.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The main vertical feed — standalone media only (playlist members are
     *  represented by their playlist card, not listed individually). */
    val feed: StateFlow<List<ArchivedMedia>> = _chip
        .flatMapLatest { chip ->
            when (chip) {
                HomeChip.ALL -> libraryRepository.observeStandalone()
                HomeChip.VIDEOS -> libraryRepository.observeStandaloneByKind(MediaKind.VIDEO)
                HomeChip.MUSIC -> libraryRepository.observeStandaloneByKind(MediaKind.MUSIC)
                HomeChip.FAVORITES -> libraryRepository.observeFavorites()
                HomeChip.PLAYLISTS -> flowOf(emptyList())
            }
        }
        .combine(_sort) { items, order -> order.sort(items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setChip(chip: HomeChip) { _chip.value = chip }
    fun setSort(order: SortOrder) { _sort.value = order }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        libraryRepository.toggleFavorite(id)
    }

    fun delete(id: Long) = viewModelScope.launch {
        libraryRepository.delete(id, deleteFile = true)
    }

    fun removeContinueVideo(id: Long) = viewModelScope.launch {
        libraryRepository.clearWatchProgress(id)
    }

    fun removeContinuePlaylist(playlistId: Long) = viewModelScope.launch {
        libraryRepository.clearPlaylistWatchProgress(playlistId)
    }

    fun togglePlaylistFavorite(id: Long) = viewModelScope.launch {
        libraryRepository.togglePlaylistFavorite(id)
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch {
        libraryRepository.deletePlaylist(id, deleteMedia = true)
    }
}
