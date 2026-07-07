package com.adnanearrassen.ytarchiver.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.DownloadItem
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadManagerViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val queue: StateFlow<List<DownloadItem>> = downloadRepository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(id: Long) = viewModelScope.launch { downloadRepository.pause(id) }
    fun resume(id: Long) = viewModelScope.launch { downloadRepository.resume(id) }
    fun cancel(id: Long) = viewModelScope.launch { downloadRepository.cancel(id) }
    fun retry(id: Long) = viewModelScope.launch { downloadRepository.retry(id) }
    fun moveUp(id: Long) = viewModelScope.launch { downloadRepository.moveUp(id) }
    fun moveDown(id: Long) = viewModelScope.launch { downloadRepository.moveDown(id) }
    fun clearFinished() = viewModelScope.launch { downloadRepository.clearFinished() }
    fun clearQueued() = viewModelScope.launch { downloadRepository.clearQueued() }
}
