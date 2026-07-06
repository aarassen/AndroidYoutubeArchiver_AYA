package com.adnanearrassen.ytarchiver.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.server.WebServerManager
import com.adnanearrassen.ytarchiver.server.WebServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val webServerManager: WebServerManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val webServerState: StateFlow<WebServerState> = webServerManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WebServerState())

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    fun toggleWebServer() = webServerManager.toggle()
}
