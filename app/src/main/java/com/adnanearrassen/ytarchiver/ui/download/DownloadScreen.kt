package com.adnanearrassen.ytarchiver.ui.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.domain.model.ChannelPreview
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

            state.channelPreview?.let { channel ->
                Spacer(Modifier.height(20.dp))
                ChannelPreviewSection(
                    channel = channel,
                    busy = state.isAnalyzing,
                    onDownload = { includeVideos, playlistUrls, asMusic ->
                        viewModel.downloadChannelSelection(includeVideos, playlistUrls, asMusic)
                        onGoToManager()
                    },
                )
            }

            if (state.channelPreview == null) state.info?.let { info ->
                Spacer(Modifier.height(20.dp))
                MediaInfoCard(info)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showSheet = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        if (info.isPlaylist) "Download playlist (${info.playlist?.itemCount ?: 0})" else "Download",
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
private fun ChannelPreviewSection(
    channel: ChannelPreview,
    busy: Boolean,
    onDownload: (includeVideos: Boolean, playlistUrls: List<String>, asMusic: Boolean) -> Unit,
) {
    var includeVideos by remember(channel) { mutableStateOf(channel.videoCount > 0) }
    var selected by remember(channel) { mutableStateOf(emptySet<String>()) }
    var asMusic by remember(channel) { mutableStateOf(false) }
    val selectedCount = (if (includeVideos) 1 else 0) + selected.size

    Card {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(url = channel.thumbnailUrl, modifier = Modifier.width(96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${channel.videoCount} videos · ${channel.playlists.size} playlists",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Choose what to download", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))

            // All videos row
            if (channel.videoCount > 0) {
                SelectableRow(
                    checked = includeVideos,
                    title = "All videos",
                    subtitle = "${channel.videoCount} videos",
                    onToggle = { includeVideos = !includeVideos },
                )
            }

            // Playlists
            if (channel.playlists.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Playlists (${channel.playlists.size})",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                channel.playlists.forEach { pl ->
                    SelectableRow(
                        checked = pl.url in selected,
                        title = pl.title,
                        subtitle = null,
                        onToggle = {
                            selected = if (pl.url in selected) selected - pl.url else selected + pl.url
                        },
                    )
                }
            } else {
                Text(
                    "This channel has no public playlists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            // Format
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !asMusic, onClick = { asMusic = false }, label = { Text("Video") })
                FilterChip(selected = asMusic, onClick = { asMusic = true }, label = { Text("Music") })
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onDownload(includeVideos, selected.toList(), asMusic) },
                enabled = !busy && selectedCount > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (selectedCount > 0) "Download ($selectedCount selected)" else "Select something",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableRow(
    checked: Boolean,
    title: String,
    subtitle: String?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
