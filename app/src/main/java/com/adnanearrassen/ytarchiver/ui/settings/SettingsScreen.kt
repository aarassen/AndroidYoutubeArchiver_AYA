package com.adnanearrassen.ytarchiver.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.domain.model.AudioBitrate
import com.adnanearrassen.ytarchiver.domain.model.AudioFormat
import com.adnanearrassen.ytarchiver.domain.model.Container
import com.adnanearrassen.ytarchiver.domain.model.DownloadType
import com.adnanearrassen.ytarchiver.domain.model.FrameRate
import com.adnanearrassen.ytarchiver.domain.model.Resolution
import com.adnanearrassen.ytarchiver.domain.model.ThemeColor
import com.adnanearrassen.ytarchiver.domain.model.ThemeMode
import com.adnanearrassen.ytarchiver.domain.model.VideoCodec
import com.adnanearrassen.ytarchiver.ui.components.EnumDropdown
import com.adnanearrassen.ytarchiver.ui.components.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenEngineUpdate: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStorage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val webServer by viewModel.webServerState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // File picker for a Netscape-format cookies.txt.
    val cookiesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importCookies(uri) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { SettingsSection("Video defaults") }
            item {
                EnumDropdown("Resolution", settings.videoResolution, Resolution.entries, { it.label }) {
                    viewModel.update { s -> s.copy(videoResolution = it) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                EnumDropdown("Frame rate", settings.videoFrameRate, FrameRate.entries, { it.label }) {
                    viewModel.update { s -> s.copy(videoFrameRate = it) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                EnumDropdown("Video codec", settings.videoCodec, VideoCodec.entries, { it.label }) {
                    viewModel.update { s -> s.copy(videoCodec = it) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                EnumDropdown("Container", settings.videoContainer, Container.entries, { it.label }) {
                    viewModel.update { s -> s.copy(videoContainer = it) }
                }
            }
            item { SwitchRow("Prefer HDR", settings.preferHdr) { v -> viewModel.update { it.copy(preferHdr = v) } } }
            item { SwitchRow("Download subtitles", settings.downloadSubtitles) { v -> viewModel.update { it.copy(downloadSubtitles = v) } } }
            item { SwitchRow("Embed subtitles", settings.embedSubtitles) { v -> viewModel.update { it.copy(embedSubtitles = v) } } }
            item { SwitchRow("Embed thumbnail", settings.embedThumbnail) { v -> viewModel.update { it.copy(embedThumbnail = v) } } }
            item { SwitchRow("Embed metadata", settings.embedMetadata) { v -> viewModel.update { it.copy(embedMetadata = v) } } }
            item { SwitchRow("Smart quality fallback", settings.smartFallback) { v -> viewModel.update { it.copy(smartFallback = v) } } }

            item { SettingsSection("Music defaults") }
            item {
                EnumDropdown("Audio format", settings.audioFormat, AudioFormat.entries, { it.label }) {
                    viewModel.update { s -> s.copy(audioFormat = it) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                EnumDropdown("Bitrate", settings.audioBitrate, AudioBitrate.entries, { it.label }) {
                    viewModel.update { s -> s.copy(audioBitrate = it) }
                }
            }
            item { SwitchRow("Embed album art", settings.embedAlbumArt) { v -> viewModel.update { it.copy(embedAlbumArt = v) } } }
            item { SwitchRow("Embed metadata", settings.embedMusicMetadata) { v -> viewModel.update { it.copy(embedMusicMetadata = v) } } }
            item { SwitchRow("Normalize volume", settings.normalizeVolume) { v -> viewModel.update { it.copy(normalizeVolume = v) } } }

            item { SettingsSection("Download behaviour") }
            item {
                EnumDropdown("Default type", settings.defaultDownloadType, DownloadType.entries, { it.label }) {
                    viewModel.update { s -> s.copy(defaultDownloadType = it) }
                }
            }
            item { SwitchRow("Ask every time (Video or Music)", settings.askDownloadTypeEveryTime) { v -> viewModel.update { it.copy(askDownloadTypeEveryTime = v) } } }
            item { SwitchRow("Remember last download type", settings.rememberLastDownloadType) { v -> viewModel.update { it.copy(rememberLastDownloadType = v) } } }
            item { SwitchRow("Auto-start after analysis", settings.autoStartAfterAnalysis) { v -> viewModel.update { it.copy(autoStartAfterAnalysis = v) } } }
            item { SwitchRow("Auto-retry failed downloads", settings.autoRetryFailed) { v -> viewModel.update { it.copy(autoRetryFailed = v) } } }
            item { SwitchRow("Auto-resume interrupted", settings.autoResumeInterrupted) { v -> viewModel.update { it.copy(autoResumeInterrupted = v) } } }
            item {
                StepperRow(
                    "Max simultaneous downloads",
                    settings.maxSimultaneousDownloads,
                    range = 1..5,
                ) { v -> viewModel.update { it.copy(maxSimultaneousDownloads = v) } }
            }
            item {
                StepperRow("Max retries", settings.maxRetries, range = 0..10) { v ->
                    viewModel.update { it.copy(maxRetries = v) }
                }
            }

            item { SettingsSection("Network & power") }
            item { SwitchRow("Wi-Fi only", settings.wifiOnly) { v -> viewModel.update { it.copy(wifiOnly = v) } } }
            item { SwitchRow("Allow cellular", settings.allowCellular) { v -> viewModel.update { it.copy(allowCellular = v) } } }
            item { SwitchRow("Battery saver", settings.batterySaver) { v -> viewModel.update { it.copy(batterySaver = v) } } }
            item { SwitchRow("Delete source after conversion", settings.deleteSourceAfterConversion) { v -> viewModel.update { it.copy(deleteSourceAfterConversion = v) } } }

            item { SettingsSection("Appearance") }
            item {
                EnumDropdown("Theme", settings.themeMode, ThemeMode.entries, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) {
                    viewModel.update { s -> s.copy(themeMode = it) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                EnumDropdown("Accent color", settings.themeColor, ThemeColor.entries, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) {
                    viewModel.update { s -> s.copy(themeColor = it) }
                }
            }
            item { SwitchRow("Use dynamic (Material You) color", settings.dynamicColor) { v -> viewModel.update { it.copy(dynamicColor = v) } } }

            item { SettingsSection("Web server (LAN)") }
            item {
                SwitchRow(
                    title = "Run web server",
                    checked = webServer.running,
                    subtitle = "Browse, play and control downloads from any browser on your Wi-Fi",
                ) { viewModel.toggleWebServer() }
            }
            if (webServer.running) {
                item {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        webServer.httpsUrl?.let {
                            Text("HTTPS: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        webServer.httpUrl?.let {
                            Text("HTTP: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = settings.webServerPassword,
                    onValueChange = { v -> viewModel.update { it.copy(webServerPassword = v) } },
                    label = { Text("Password (blank = no password)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            item {
                SwitchRow(
                    title = "Enable HTTPS",
                    checked = settings.webServerHttpsEnabled,
                    subtitle = "Secure with a self-signed certificate (browser will warn once)",
                ) { v -> viewModel.update { it.copy(webServerHttpsEnabled = v) } }
            }
            if (settings.webServerHttpsEnabled) {
                item {
                    SwitchRow(
                        title = "HTTPS only",
                        checked = settings.webServerHttpsOnly,
                        subtitle = "Disable plain HTTP; force secure access",
                    ) { v -> viewModel.update { it.copy(webServerHttpsOnly = v) } }
                }
            }

            item { SettingsSection("yt-dlp engine") }
            item {
                SwitchRow(
                    title = "Bypass age & login restrictions",
                    checked = settings.bypassRestrictions,
                    subtitle = "If a download is blocked (age-restricted / \"sign in to confirm\"), retry using alternate device players (android, iOS, TV)",
                ) { v -> viewModel.update { it.copy(bypassRestrictions = v) } }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                CookiesRow(
                    hasCookies = settings.cookiesPath != null,
                    onImport = { cookiesPicker.launch(arrayOf("text/plain", "text/*", "application/octet-stream", "*/*")) },
                    onClear = viewModel::clearCookies,
                )
            }

            item { SettingsSection("Updates & data") }
            item { SwitchRow("Auto-update yt-dlp engine", settings.autoUpdateYtDlp) { v -> viewModel.update { it.copy(autoUpdateYtDlp = v) } } }
            item { NavRow("yt-dlp engine & updates", onClick = onOpenEngineUpdate) }
            item { NavRow("Download history", onClick = onOpenHistory) }
            item { NavRow("Storage manager", onClick = onOpenStorage) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun CookiesRow(hasCookies: Boolean, onImport: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "Import a cookies.txt (Netscape format) to download age-restricted, members-only or private videos you can access when signed in. Export one with a browser extension like \"Get cookies.txt\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasCookies) "Status: cookies loaded ✓" else "Status: no cookies",
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasCookies) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onImport) {
                Text(if (hasCookies) "Replace cookies.txt" else "Import cookies.txt")
            }
            if (hasCookies) {
                OutlinedButton(onClick = onClear) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.IconButton(
            onClick = { if (value > range.first) onChange(value - 1) },
        ) { Text("–", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.IconButton(
            onClick = { if (value < range.last) onChange(value + 1) },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
