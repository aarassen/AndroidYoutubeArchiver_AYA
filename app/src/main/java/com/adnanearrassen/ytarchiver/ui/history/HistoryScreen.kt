package com.adnanearrassen.ytarchiver.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanearrassen.ytarchiver.domain.model.DownloadHistoryRecord
import com.adnanearrassen.ytarchiver.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.adnanearrassen.ytarchiver.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltViewModel
class HistoryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
) : ViewModel() {
    val history: StateFlow<List<DownloadHistoryRecord>> = libraryRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val dateFormat = rememberDateFormat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download history") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyState(title = "No history yet", modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(history, key = { it.id }) { record ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(record.type.label)
                            (record.videoQuality ?: record.audioQuality)?.let { append(" · $it") }
                            append(" · ${Formatters.bytes(record.fileSizeBytes)}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        buildString {
                            record.completedAt?.let { append(dateFormat.format(Date(it))) }
                            if (record.conversionMillis > 0) append(" · convert ${record.conversionMillis / 1000}s")
                            append(if (record.succeeded) " · ✓" else " · ✕")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.succeeded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun rememberDateFormat(): SimpleDateFormat =
    androidx.compose.runtime.remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }
