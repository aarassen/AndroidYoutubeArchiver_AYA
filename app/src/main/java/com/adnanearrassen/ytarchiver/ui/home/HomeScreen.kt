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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import com.adnanearrassen.ytarchiver.ui.components.CompactVideoCard
import com.adnanearrassen.ytarchiver.ui.components.EmptyState
import com.adnanearrassen.ytarchiver.ui.components.MusicRow
import com.adnanearrassen.ytarchiver.ui.components.SectionHeader

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMedia: (Long) -> Unit,
    onQuickDownload: () -> Unit,
    onSeeStorage: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YT Archiver", fontWeight = FontWeight.Bold) },
                actions = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        modifier = Modifier.padding(end = 16.dp),
                    )
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
        if (state.isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Your archive is empty",
                    subtitle = "Tap Download to save your first video, playlist or track for offline viewing.",
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (state.continueWatching.isNotEmpty()) {
                item { SectionHeader("Continue watching") }
                item { HorizontalMediaRow(state.continueWatching, onOpenMedia) }
            }
            if (state.downloadedToday.isNotEmpty()) {
                item { SectionHeader("Downloaded today") }
                item { HorizontalMediaRow(state.downloadedToday, onOpenMedia) }
            }
            item { SectionHeader("Recently downloaded") }
            item { HorizontalMediaRow(state.recentlyAdded, onOpenMedia) }

            if (state.favorites.isNotEmpty()) {
                item { SectionHeader("Favorites") }
                item { HorizontalMediaRow(state.favorites, onOpenMedia) }
            }

            if (state.music.isNotEmpty()) {
                item { SectionHeader("Music") }
                items(state.music, key = { "music-${it.id}" }) { track ->
                    MusicRow(media = track, onClick = { onOpenMedia(track.id) })
                }
            }

            item {
                SectionHeader(
                    "Storage",
                    action = {
                        Text(
                            "Manage",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(4.dp),
                        )
                    },
                )
            }
            item {
                val totalBytes = (state.recentlyAdded + state.music).distinctBy { it.id }
                    .sumOf { it.fileSizeBytes }
                Text(
                    text = "Using ${Formatters.bytes(totalBytes)} across ${state.recentlyAdded.size} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun HorizontalMediaRow(
    items: List<ArchivedMedia>,
    onOpenMedia: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { media ->
            CompactVideoCard(media = media, onClick = { onOpenMedia(media.id) })
        }
    }
    Spacer(Modifier.height(8.dp))
}
