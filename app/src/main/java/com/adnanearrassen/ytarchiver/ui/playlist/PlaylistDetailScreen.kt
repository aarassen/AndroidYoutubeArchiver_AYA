package com.adnanearrassen.ytarchiver.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.ui.components.ConfirmDeleteDialog
import com.adnanearrassen.ytarchiver.ui.components.MediaOverflowMenu
import com.adnanearrassen.ytarchiver.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenMedia: (Long) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ArchivedMedia?>(null) }
    var showDeletePlaylist by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    val isFav = playlist?.isFavorite == true
                    IconButton(onClick = { viewModel.togglePlaylistFavorite() }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showDeletePlaylist = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                playlist?.let { p ->
                    Column(Modifier.padding(16.dp)) {
                        Thumbnail(url = p.thumbnailPath, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append("${p.itemCount} videos")
                                if (p.totalDurationSeconds > 0) {
                                    append(" · ")
                                    append(Formatters.duration(p.totalDurationSeconds))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(items, key = { _, m -> m.id }) { index, media ->
                PlaylistItemRow(
                    index = index + 1,
                    media = media,
                    onClick = { onOpenMedia(media.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(media.id) },
                    onDelete = { pendingDelete = media },
                )
            }
        }
    }

    pendingDelete?.let { media ->
        ConfirmDeleteDialog(
            title = media.title,
            onConfirm = { viewModel.delete(media.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showDeletePlaylist) {
        val p = playlist
        ConfirmDeleteDialog(
            title = p?.name ?: "Playlist",
            heading = "Delete playlist?",
            message = "\"${p?.name ?: "This playlist"}\" and its ${p?.itemCount ?: 0} downloaded videos will be permanently removed.",
            onConfirm = {
                viewModel.deletePlaylist()
                showDeletePlaylist = false
                onBack()
            },
            onDismiss = { showDeletePlaylist = false },
        )
    }
}

/** A numbered playlist row: index · thumbnail · title/uploader · overflow. */
@Composable
private fun PlaylistItemRow(
    index: Int,
    media: ArchivedMedia,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Text(
                "$index",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Thumbnail(
            url = media.thumbnailPath ?: media.filePath,
            durationSeconds = media.durationSeconds,
            watchProgress = media.watchProgress,
            modifier = Modifier.width(140.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                media.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                media.uploader ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MediaOverflowMenu(media.isFavorite, onToggleFavorite, onDelete)
    }
}
