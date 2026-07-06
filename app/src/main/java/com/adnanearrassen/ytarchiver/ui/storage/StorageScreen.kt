package com.adnanearrassen.ytarchiver.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.StorageBreakdown
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.core.common.Formatters

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val _breakdown = MutableStateFlow<StorageBreakdown?>(null)
    val breakdown: StateFlow<StorageBreakdown?> = _breakdown.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _breakdown.value = libraryRepository.storageBreakdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val breakdown by viewModel.breakdown.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        val data = breakdown
        if (data == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Archive size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.bytes(data.totalBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val used = (data.deviceTotalBytes - data.deviceFreeBytes).toFloat()
                        val ratio = if (data.deviceTotalBytes > 0) used / data.deviceTotalBytes else 0f
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Formatters.bytes(data.deviceFreeBytes)} free of ${Formatters.bytes(data.deviceTotalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                StatRow("Videos", data.videoBytes)
                StatRow("Music", data.musicBytes)
                StatRow("Thumbnails", data.thumbnailBytes)
                Spacer(Modifier.height(16.dp))
            }
            if (data.largestItems.isNotEmpty()) {
                item {
                    Text("Largest downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                items(data.largestItems, key = { it.id }) { media ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(media.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Formatters.bytes(media.fileSizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (data.brokenItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Missing / broken files (${data.brokenItems.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                items(data.brokenItems, key = { "broken-${it.id}" }) { media ->
                    Text(media.title, Modifier.padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(Formatters.bytes(bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
