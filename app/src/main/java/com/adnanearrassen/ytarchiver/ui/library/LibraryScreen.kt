package com.adnanearrassen.ytarchiver.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.adnanearrassen.ytarchiver.ui.components.ConfirmDeleteDialog
import com.adnanearrassen.ytarchiver.ui.components.EmptyState
import com.adnanearrassen.ytarchiver.ui.components.MusicRow
import com.adnanearrassen.ytarchiver.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenMedia: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ArchivedMedia?>(null) }

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

            if (items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        EmptyState(title = "Nothing here yet", subtitle = "Downloads will appear in your library.")
                    }
                }
            } else {
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

    pendingDelete?.let { media ->
        ConfirmDeleteDialog(
            title = media.title,
            onConfirm = { viewModel.delete(media.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
