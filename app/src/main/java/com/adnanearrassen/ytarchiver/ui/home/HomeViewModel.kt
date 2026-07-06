package com.adnanearrassen.ytarchiver.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val recentlyAdded: List<ArchivedMedia> = emptyList(),
    val continueWatching: List<ArchivedMedia> = emptyList(),
    val music: List<ArchivedMedia> = emptyList(),
    val favorites: List<ArchivedMedia> = emptyList(),
    val downloadedToday: List<ArchivedMedia> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = recentlyAdded.isEmpty() && continueWatching.isEmpty() &&
            music.isEmpty() && favorites.isEmpty() && downloadedToday.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        libraryRepository.observeRecentlyAdded(20),
        libraryRepository.observeContinueWatching(10),
        libraryRepository.observeByKind(MediaKind.MUSIC),
        libraryRepository.observeFavorites(),
        combine(
            libraryRepository.observeDownloadedToday(),
            libraryRepository.observePlaylists(),
        ) { today, playlists -> today to playlists },
    ) { recent, continueW, music, favorites, (today, playlists) ->
        HomeUiState(
            recentlyAdded = recent,
            continueWatching = continueW,
            music = music.take(10),
            favorites = favorites.take(10),
            downloadedToday = today,
            playlists = playlists,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
