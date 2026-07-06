package com.adnanearrassen.ytarchiver.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.domain.model.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineUpdateScreen(
    onBack: () -> Unit,
    viewModel: EngineUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installed by viewModel.installedVersion.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("yt-dlp engine") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Installed version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(installed ?: "Unknown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    val latest = (state as? UpdateState.UpdateAvailable)?.info?.latestVersion
                        ?: (state as? UpdateState.UpToDate)?.version
                    if (latest != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Latest version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(latest, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is UpdateState.Checking ->
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                is UpdateState.Downloading -> {
                    Text("Downloading update…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Installing ->
                    Text("Installing…", style = MaterialTheme.typography.bodyMedium)
                is UpdateState.Failed ->
                    Text("Update failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                is UpdateState.UpToDate ->
                    Text("You're up to date.", color = MaterialTheme.colorScheme.primary)
                else -> {}
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                Text("Check for updates")
            }
            val updateAvailable = state is UpdateState.UpdateAvailable
            if (updateAvailable) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::update, modifier = Modifier.fillMaxWidth()) {
                    Text("Update now")
                }
            }

            (state as? UpdateState.UpdateAvailable)?.info?.changelog?.let { changelog ->
                Spacer(Modifier.height(24.dp))
                Text("Changelog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(changelog, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
