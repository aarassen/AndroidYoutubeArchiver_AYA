package com.adnanearrassen.ytarchiver.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.UpdateState
import com.adnanearrassen.ytarchiver.domain.repository.EngineUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EngineUpdateViewModel @Inject constructor(
    private val repository: EngineUpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateState.Idle)

    private val _installedVersion = MutableStateFlow<String?>(null)
    val installedVersion: StateFlow<String?> = _installedVersion.asStateFlow()

    init {
        viewModelScope.launch { _installedVersion.value = repository.installedVersion() }
    }

    fun checkForUpdates() = viewModelScope.launch { repository.checkForUpdates() }

    fun update() = viewModelScope.launch {
        repository.update()
        _installedVersion.value = repository.installedVersion()
    }
}
