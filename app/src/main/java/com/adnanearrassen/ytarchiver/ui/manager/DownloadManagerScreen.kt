package com.adnanearrassen.ytarchiver.ui.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.domain.model.DownloadItem
import com.adnanearrassen.ytarchiver.domain.model.DownloadStatus
import com.adnanearrassen.ytarchiver.ui.components.EmptyState
import com.adnanearrassen.ytarchiver.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    viewModel: DownloadManagerViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var confirmClearQueue by remember { mutableStateOf(false) }
    val pendingCount = queue.count {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED ||
            it.status == DownloadStatus.FAILED || it.status == DownloadStatus.ANALYZING
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                actions = {
                    if (pendingCount > 0) {
                        TextButton(onClick = { confirmClearQueue = true }) { Text("Clear queue") }
                    }
                    TextButton(onClick = viewModel::clearFinished) { Text("Clear finished") }
                },
            )
        },
    ) { padding ->
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(title = "No active downloads", subtitle = "Queued and in-progress downloads show up here.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(queue, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onPause = { viewModel.pause(item.id) },
                    onResume = { viewModel.resume(item.id) },
                    onCancel = { viewModel.cancel(item.id) },
                    onRetry = { viewModel.retry(item.id) },
                    onMoveUp = { viewModel.moveUp(item.id) },
                    onMoveDown = { viewModel.moveDown(item.id) },
                )
            }
        }
    }

    if (confirmClearQueue) {
        AlertDialog(
            onDismissRequest = { confirmClearQueue = false },
            title = { Text("Clear queue?") },
            text = { Text("Remove all $pendingCount queued, paused and failed downloads. In-progress downloads are not affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearQueued(); confirmClearQueue = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearQueue = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row {
                Thumbnail(
                    url = item.thumbnailUrl,
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.uploader ?: item.type.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        statusLine(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(item.status),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${item.percent}% · ${Formatters.bytes(item.downloadedBytes)} / ${Formatters.bytes(item.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (item.status == DownloadStatus.DOWNLOADING)
                        "${Formatters.speed(item.speedBytesPerSec)} · ${Formatters.eta(item.etaSeconds)}"
                    else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.canPause) IconButton(onPause) { Icon(Icons.Filled.Pause, "Pause") }
                if (item.canResume) IconButton(onResume) { Icon(Icons.Filled.PlayArrow, "Resume") }
                if (item.canRetry) IconButton(onRetry) { Icon(Icons.Filled.Refresh, "Retry") }
                if (item.canCancel) IconButton(onCancel) { Icon(Icons.Filled.Close, "Cancel") }
                Spacer(Modifier.weight(1f))
                IconButton(onMoveUp) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                IconButton(onMoveDown) { Icon(Icons.Filled.ArrowDownward, "Move down") }
            }
        }
    }
}

private fun statusLine(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Queued · position ${item.queuePosition}"
    DownloadStatus.ANALYZING -> "Analyzing…"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.PROCESSING -> "Processing (remux / convert)…"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed: ${item.errorMessage ?: "unknown error"}"
    DownloadStatus.CANCELED -> "Canceled"
}

@Composable
private fun statusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
