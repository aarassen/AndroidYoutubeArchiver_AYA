package com.adnanearrassen.ytarchiver.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.domain.model.MediaKind
import com.adnanearrassen.ytarchiver.domain.model.Playlist
import androidx.compose.ui.text.style.TextOverflow
import com.adnanearrassen.ytarchiver.ui.components.ConfirmDeleteDialog
import com.adnanearrassen.ytarchiver.ui.components.EmptyState
import com.adnanearrassen.ytarchiver.ui.components.MediaOverflowMenu
import com.adnanearrassen.ytarchiver.ui.components.MusicRow
import com.adnanearrassen.ytarchiver.ui.components.PlaylistCard
import com.adnanearrassen.ytarchiver.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenMedia: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ArchivedMedia?>(null) }
    var pendingDeletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pendingDeleteChannel by remember { mutableStateOf<com.adnanearrassen.ytarchiver.domain.model.Channel?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library", fontWeight = FontWeight.Bold) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search your archive") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { viewModel.setFilter(f) },
                            label = { Text(f.label) },
                        )
                    }
                }
            }

            // Channels filter: list channels, then drill into one.
            if (filter == LibraryFilter.CHANNELS && query.isBlank()) {
                if (selectedChannel == null) {
                    if (channels.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                                EmptyState(title = "No channels yet", subtitle = "Downloads are grouped by channel here.")
                            }
                        }
                    } else {
                        items(channels, key = { "ch-${it.name}" }) { ch ->
                            ChannelRow(
                                name = ch.name,
                                count = ch.itemCount,
                                onClick = { viewModel.openChannel(ch.name) },
                                onDelete = { pendingDeleteChannel = ch },
                            )
                        }
                    }
                    return@LazyColumn
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.TextButton(onClick = { viewModel.openChannel(null) }) {
                                Text("← Channels")
                            }
                            Text(selectedChannel ?: "", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    items(items, key = { it.id }) { media ->
                        if (media.kind == MediaKind.MUSIC) {
                            MusicRow(media = media, onClick = { onOpenMedia(media.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(media.id) }, onDelete = { pendingDelete = media })
                        } else {
                            VideoCard(media = media, onClick = { onOpenMedia(media.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(media.id) }, onDelete = { pendingDelete = media })
                        }
                    }
                    return@LazyColumn
                }
            }

            // "All" and "Playlists" include playlist cards; everything else is
            // media only. Playlists render first, then standalone media.
            val searching = query.isNotBlank()
            val showPlaylists = !searching &&
                (filter == LibraryFilter.ALL || filter == LibraryFilter.PLAYLISTS)
            val showMedia = searching || filter != LibraryFilter.PLAYLISTS
            val nothingToShow =
                (!showPlaylists || playlists.isEmpty()) && (!showMedia || items.isEmpty())

            if (nothingToShow) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = if (filter == LibraryFilter.PLAYLISTS) "No playlists yet" else "Nothing here yet",
                            subtitle = "Downloads will appear in your library.",
                        )
                    }
                }
            } else {
                if (showPlaylists) {
                    items(playlists, key = { "pl-${it.id}" }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            onToggleFavorite = { viewModel.togglePlaylistFavorite(playlist.id) },
                            onDelete = { pendingDeletePlaylist = playlist },
                        )
                    }
                }
                if (showMedia) {
                    items(items, key = { it.id }) { media ->
                        if (media.kind == MediaKind.MUSIC) {
                            MusicRow(
                                media = media,
                                onClick = { onOpenMedia(media.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(media.id) },
                                onDelete = { pendingDelete = media },
                            )
                        } else {
                            VideoCard(
                                media = media,
                                onClick = { onOpenMedia(media.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(media.id) },
                                onDelete = { pendingDelete = media },
                            )
                        }
                    }
                }
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

    pendingDeletePlaylist?.let { pl ->
        ConfirmDeleteDialog(
            title = pl.name,
            heading = "Delete playlist?",
            message = "\"${pl.name}\" and its ${pl.itemCount} downloaded videos will be permanently removed.",
            onConfirm = { viewModel.deletePlaylist(pl.id); pendingDeletePlaylist = null },
            onDismiss = { pendingDeletePlaylist = null },
        )
    }

    pendingDeleteChannel?.let { ch ->
        ConfirmDeleteDialog(
            title = ch.name,
            heading = "Delete channel?",
            message = "All ${ch.itemCount} downloaded items from \"${ch.name}\" will be permanently removed.",
            onConfirm = { viewModel.deleteChannel(ch.name); pendingDeleteChannel = null },
            onDismiss = { pendingDeleteChannel = null },
        )
    }
}

@Composable
private fun ChannelRow(name: String, count: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$count ${if (count == 1) "item" else "items"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MediaOverflowMenu(isFavorite = false, onToggleFavorite = null, onDelete = onDelete)
    }
}
