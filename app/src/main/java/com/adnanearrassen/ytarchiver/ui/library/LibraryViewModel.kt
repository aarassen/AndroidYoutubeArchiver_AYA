package com.adnanearrassen.ytarchiver.ui.library

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val items: StateFlow<List<ArchivedMedia>> =
        combine(_filter, _query) { filter, query -> filter to query }
            .flatMapLatest { (filter, query) ->
                if (query.isNotBlank()) libraryRepository.search(query)
                else when (filter) {
                    LibraryFilter.ALL -> libraryRepository.observeAll()
                    LibraryFilter.VIDEOS -> libraryRepository.observeByKind(MediaKind.VIDEO)
                    LibraryFilter.MUSIC -> libraryRepository.observeByKind(MediaKind.MUSIC)
                    LibraryFilter.FAVORITES -> libraryRepository.observeFavorites()
                    LibraryFilter.RECENT -> libraryRepository.observeRecentlyAdded(50)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: LibraryFilter) { _filter.value = filter }
    fun setQuery(query: String) { _query.value = query }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        libraryRepository.toggleFavorite(id)
    }

    fun delete(id: Long) = viewModelScope.launch {
        libraryRepository.delete(id, deleteFile = true)
    }
}
