package com.adnanearrassen.ytarchiver.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.core.common.UrlUtils
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.DownloadType
import com.adnanearrassen.ytarchiver.domain.model.MediaInfo
import com.adnanearrassen.ytarchiver.domain.model.OpResult
import com.adnanearrassen.ytarchiver.domain.repository.DownloadRepository
import com.adnanearrassen.ytarchiver.domain.repository.MediaAnalyzer
import com.adnanearrassen.ytarchiver.domain.repository.SettingsRepository
import com.adnanearrassen.ytarchiver.domain.usecase.DefaultOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadUiState(
    val url: String = "",
    val isAnalyzing: Boolean = false,
    val info: MediaInfo? = null,
    val error: String? = null,
    val enqueuedMessage: String? = null,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val analyzer: MediaAnalyzer,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun onUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(url = value, error = null)
    }

    /** Called when a link is shared/opened into the app: fill the field and
     *  immediately analyze it. Overwrites any partially-typed URL because the
     *  share is an explicit user action. */
    fun prefill(url: String) {
        android.util.Log.d("YTShare", "Prefill + analyze shared url: $url")
        _uiState.value = _uiState.value.copy(url = url, info = null, error = null)
        analyze()
    }

    fun analyze() {
        val raw = _uiState.value.url.trim()
        val url = UrlUtils.firstUrlIn(raw) ?: raw
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Paste a YouTube URL first")
            return
        }
        _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null, info = null)
        viewModelScope.launch {
            when (val result = analyzer.analyze(url)) {
                is OpResult.Success ->
                    _uiState.value = _uiState.value.copy(isAnalyzing = false, info = result.data)
                is OpResult.Error ->
                    _uiState.value = _uiState.value.copy(isAnalyzing = false, error = result.message)
            }
        }
    }

    /** One-tap video download using saved defaults (or provided overrides). */
    fun downloadVideo(overrides: DownloadOptions.Video? = null) =
        enqueue(DownloadType.VIDEO, overrides)

    /** One-tap music download using saved defaults (or provided overrides). */
    fun downloadMusic(overrides: DownloadOptions.Music? = null) =
        enqueue(DownloadType.MUSIC, overrides)

    private fun enqueue(type: DownloadType, overrides: DownloadOptions?) {
        val info = _uiState.value.info ?: return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val options = overrides ?: when (type) {
                DownloadType.VIDEO -> DefaultOptions.video(settings)
                DownloadType.MUSIC -> DefaultOptions.music(settings)
            }

            val count = if (info.isPlaylist) {
                downloadRepository.enqueuePlaylist(info, options).size
            } else {
                downloadRepository.enqueue(
                    sourceUrl = info.url,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    options = options,
                )
                1
            }

            if (settings.rememberLastDownloadType) {
                settingsRepository.update { it.copy(lastDownloadType = type) }
            }

            val label = if (type == DownloadType.MUSIC) "track" else "video"
            _uiState.value = _uiState.value.copy(
                enqueuedMessage = if (count > 1) "Queued $count items" else "Queued 1 $label",
            )
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(enqueuedMessage = null)
    }

    fun clear() {
        _uiState.value = DownloadUiState()
    }
}
