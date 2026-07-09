package com.adnanearrassen.ytarchiver.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.core.common.IoDispatcher
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.server.WebServerManager
import com.adnanearrassen.ytarchiver.server.WebServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val webServerManager: WebServerManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val webServerState: StateFlow<WebServerState> = webServerManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WebServerState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    fun toggleWebServer() = webServerManager.toggle()

    /** Copies a user-picked cookies.txt into app storage and stores its path. */
    fun importCookies(uri: Uri) = viewModelScope.launch {
        val result = withContext(io) {
            runCatching {
                val target = File(context.filesDir, "cookies.txt")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } ?: error("Could not open the selected file")
                require(target.length() > 0) { "The selected file is empty" }
                target.absolutePath
            }
        }
        result.fold(
            onSuccess = { path ->
                settingsRepository.update { it.copy(cookiesPath = path) }
                _message.value = "Cookies imported"
            },
            onFailure = { _message.value = "Import failed: ${it.message}" },
        )
    }

    fun clearCookies() = viewModelScope.launch {
        withContext(io) { runCatching { File(context.filesDir, "cookies.txt").delete() } }
        settingsRepository.update { it.copy(cookiesPath = null) }
        _message.value = "Cookies removed"
    }

    fun consumeMessage() { _message.value = null }
}
