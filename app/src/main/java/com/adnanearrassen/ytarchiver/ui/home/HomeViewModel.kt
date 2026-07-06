package com.adnanearrassen.ytarchiver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeChip(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    FAVORITES("Favorites"),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _chip = MutableStateFlow(HomeChip.ALL)
    val chip: StateFlow<HomeChip> = _chip.asStateFlow()

    /** Horizontal "Continue watching" shelf shown above the feed. */
    val continueWatching: StateFlow<List<ArchivedMedia>> =
        libraryRepository.observeContinueWatching(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The main vertical feed, filtered by the selected chip. */
    val feed: StateFlow<List<ArchivedMedia>> = _chip
        .flatMapLatest { chip ->
            when (chip) {
                HomeChip.ALL -> libraryRepository.observeAll()
                HomeChip.VIDEOS -> libraryRepository.observeByKind(MediaKind.VIDEO)
                HomeChip.MUSIC -> libraryRepository.observeByKind(MediaKind.MUSIC)
                HomeChip.FAVORITES -> libraryRepository.observeFavorites()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setChip(chip: HomeChip) { _chip.value = chip }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        libraryRepository.toggleFavorite(id)
    }

    fun delete(id: Long) = viewModelScope.launch {
        libraryRepository.delete(id, deleteFile = true)
    }
}
