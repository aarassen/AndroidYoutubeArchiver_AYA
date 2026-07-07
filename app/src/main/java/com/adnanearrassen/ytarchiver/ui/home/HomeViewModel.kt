package com.adnanearrassen.ytarchiver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.ContinueItem
import com.adnanearrassen.ytarchiver.domain.model.FeedEntry
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
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

    fun sort(items: List<FeedEntry>): List<FeedEntry> = when (this) {
        RECENT -> items.sortedByDescending { it.sortTime }
        OLDEST -> items.sortedBy { it.sortTime }
        NAME -> items.sortedBy { it.sortName.lowercase() }
        SIZE -> items.sortedByDescending { it.sortSize }
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

    /**
     * The main vertical feed as a single sorted list of videos AND playlists.
     * "All" mixes both; the sort (newest/oldest/name/size) orders them together
     * so playlists aren't pinned to the top.
     */
    val feed: StateFlow<List<FeedEntry>> = combine(
        _chip.flatMapLatest { chip ->
            when (chip) {
                HomeChip.ALL -> libraryRepository.observeStandalone()
                HomeChip.VIDEOS -> libraryRepository.observeStandaloneByKind(MediaKind.VIDEO)
                HomeChip.MUSIC -> libraryRepository.observeStandaloneByKind(MediaKind.MUSIC)
                HomeChip.FAVORITES -> libraryRepository.observeFavorites()
                HomeChip.PLAYLISTS -> flowOf(emptyList())
            }
        },
        libraryRepository.observePlaylists(),
        _chip,
        _sort,
    ) { media, playlists, chip, order ->
        val entries = buildList<FeedEntry> {
            if (chip != HomeChip.PLAYLISTS) media.forEach { add(FeedEntry.Video(it)) }
            if (chip == HomeChip.ALL || chip == HomeChip.PLAYLISTS) {
                playlists.forEach { add(FeedEntry.PlaylistRow(it)) }
            }
        }
        order.sort(entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
