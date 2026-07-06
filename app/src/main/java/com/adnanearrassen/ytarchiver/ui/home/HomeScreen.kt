package com.adnanearrassen.ytarchiver.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.adnanearrassen.ytarchiver.ui.components.CompactVideoCard
import com.adnanearrassen.ytarchiver.ui.components.ConfirmDeleteDialog
import com.adnanearrassen.ytarchiver.ui.components.EmptyState
import com.adnanearrassen.ytarchiver.ui.components.MusicRow
import com.adnanearrassen.ytarchiver.ui.components.SectionHeader
import com.adnanearrassen.ytarchiver.ui.components.VideoCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMedia: (Long) -> Unit,
    onQuickDownload: () -> Unit,
    onSeeStorage: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val chip by viewModel.chip.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ArchivedMedia?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YT Archiver", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onQuickDownload) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search / add")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickDownload,
                icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                text = { Text("Download") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            // Filter chips (YouTube-style).
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeChip.entries.forEach { c ->
                        FilterChip(
                            selected = chip == c,
                            onClick = { viewModel.setChip(c) },
                            label = { Text(c.label) },
                        )
                    }
                }
            }

            // Continue watching shelf.
            if (continueWatching.isNotEmpty() && chip == HomeChip.ALL) {
                item { SectionHeader("Continue watching") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { "cw-${it.id}" }) { media ->
                            CompactVideoCard(media = media, onClick = { onOpenMedia(media.id) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (feed.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "Your archive is empty",
                            subtitle = "Tap Download to save your first video, playlist or track for offline viewing.",
                        )
                    }
                }
            } else {
                // Main vertical feed of large cards.
                items(feed, key = { it.id }) { media ->
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

    pendingDelete?.let { media ->
        ConfirmDeleteDialog(
            title = media.title,
            onConfirm = { viewModel.delete(media.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
