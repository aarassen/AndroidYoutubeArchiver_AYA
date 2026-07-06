package com.adnanearrassen.ytarchiver.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.domain.model.MediaInfo
import com.adnanearrassen.ytarchiver.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    prefilledUrl: String?,
    onUrlConsumed: () -> Unit,
    onGoToManager: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(prefilledUrl) {
        if (prefilledUrl != null) {
            viewModel.prefill(prefilledUrl)
            onUrlConsumed()
        }
    }

    LaunchedEffect(state.enqueuedMessage) {
        state.enqueuedMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Download", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("YouTube video, playlist or music URL") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { viewModel.onUrlChange(it) }
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::analyze,
                enabled = !state.isAnalyzing && state.url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isAnalyzing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Analyze", fontWeight = FontWeight.SemiBold)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Card {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            state.info?.let { info ->
                Spacer(Modifier.height(20.dp))
                MediaInfoCard(info)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showSheet = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        if (info.isPlaylist) "Download playlist (${info.playlist?.itemCount ?: 0})"
                        else "Download",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showSheet && state.info != null) {
        DownloadOptionsSheet(
            settings = settings,
            onDismiss = { showSheet = false },
            onDownloadVideo = {
                viewModel.downloadVideo(it)
                showSheet = false
                onGoToManager()
            },
            onDownloadMusic = {
                viewModel.downloadMusic(it)
                showSheet = false
                onGoToManager()
            },
        )
    }
}

@Composable
private fun MediaInfoCard(info: MediaInfo) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Thumbnail(url = info.thumbnailUrl, durationSeconds = info.durationSeconds)
            Spacer(Modifier.height(12.dp))
            Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    info.uploader?.let { append(it) }
                    if (info.isPlaylist) append(" · Playlist (${info.playlist?.itemCount ?: 0} items)")
                    info.viewCount?.let { append(" · ${Formatters.count(it)} views") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.videoFormats.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val maxHeight = info.videoFormats.mapNotNull { it.height }.maxOrNull()
                Text(
                    "Up to ${maxHeight?.let { "${it}p" } ?: "—"} · ${info.videoFormats.size} formats · ${info.audioFormats.size} audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
