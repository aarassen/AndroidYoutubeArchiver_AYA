package com.adnanearrassen.ytarchiver.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnanearrassen.ytarchiver.domain.model.AppSettings
import com.adnanearrassen.ytarchiver.domain.model.AudioBitrate
import com.adnanearrassen.ytarchiver.domain.model.AudioFormat
import com.adnanearrassen.ytarchiver.domain.model.Container
import com.adnanearrassen.ytarchiver.domain.model.DownloadOptions
import com.adnanearrassen.ytarchiver.domain.model.FrameRate
import com.adnanearrassen.ytarchiver.domain.model.Resolution
import com.adnanearrassen.ytarchiver.domain.model.SampleRate
import com.adnanearrassen.ytarchiver.domain.model.VideoAudioCodec
import com.adnanearrassen.ytarchiver.domain.model.VideoCodec
import com.adnanearrassen.ytarchiver.domain.usecase.DefaultOptions
import com.adnanearrassen.ytarchiver.ui.components.EnumDropdown
import com.adnanearrassen.ytarchiver.ui.components.SwitchRow

/**
 * The two-tap download sheet from the spec: primary "Download Video" / "Download
 * Music" buttons that use saved defaults, plus an Advanced section that lets the
 * user override everything for just this download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onDownloadVideo: (DownloadOptions.Video) -> Unit,
    onDownloadMusic: (DownloadOptions.Music) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var advancedOpen by remember { mutableStateOf(false) }
    var videoOptions by remember { mutableStateOf(DefaultOptions.video(settings)) }
    var musicOptions by remember { mutableStateOf(DefaultOptions.music(settings)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Download", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a format. One tap uses your saved defaults.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onDownloadVideo(videoOptions) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Download Video", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onDownloadMusic(musicOptions) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
                Text("  Download Music", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Text(if (advancedOpen) "  Hide Advanced Options" else "  Advanced Options")
            }

            AnimatedVisibility(visible = advancedOpen) {
                Column(Modifier.padding(top = 12.dp)) {
                    AdvancedVideoOptions(videoOptions) { videoOptions = it }
                    Spacer(Modifier.height(20.dp))
                    AdvancedMusicOptions(musicOptions) { musicOptions = it }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdvancedVideoOptions(
    options: DownloadOptions.Video,
    onChange: (DownloadOptions.Video) -> Unit,
) {
    Text("Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Resolution", options.resolution, Resolution.entries, { it.label }) {
        onChange(options.copy(resolution = it))
    }
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Frame rate", options.frameRate, FrameRate.entries, { it.label }) {
        onChange(options.copy(frameRate = it))
    }
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Video codec", options.videoCodec, VideoCodec.entries, { it.label }) {
        onChange(options.copy(videoCodec = it))
    }
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Audio codec", options.audioCodec, VideoAudioCodec.entries, { it.label }) {
        onChange(options.copy(audioCodec = it))
    }
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Container", options.container, Container.entries, { it.label }) {
        onChange(options.copy(container = it))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = options.outputFileName.orEmpty(),
        onValueChange = { onChange(options.copy(outputFileName = it.ifBlank { null })) },
        label = { Text("Output filename (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SwitchRow("Prefer HDR", options.preferHdr) { onChange(options.copy(preferHdr = it)) }
    SwitchRow("Download subtitles", options.downloadSubtitles) { onChange(options.copy(downloadSubtitles = it)) }
    SwitchRow("Embed subtitles", options.embedSubtitles) { onChange(options.copy(embedSubtitles = it)) }
    SwitchRow("Embed thumbnail", options.embedThumbnail) { onChange(options.copy(embedThumbnail = it)) }
    SwitchRow("Embed metadata", options.embedMetadata) { onChange(options.copy(embedMetadata = it)) }
    SwitchRow("Smart fallback", options.smartFallback) { onChange(options.copy(smartFallback = it)) }
}

@Composable
private fun AdvancedMusicOptions(
    options: DownloadOptions.Music,
    onChange: (DownloadOptions.Music) -> Unit,
) {
    Text("Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Audio format", options.format, AudioFormat.entries, { it.label }) {
        onChange(options.copy(format = it))
    }
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Bitrate", options.bitrate, AudioBitrate.entries, { it.label }) {
        onChange(options.copy(bitrate = it))
    }
    Spacer(Modifier.height(8.dp))
    EnumDropdown("Sample rate", options.sampleRate, SampleRate.entries, { it.label }) {
        onChange(options.copy(sampleRate = it))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = options.outputFileName.orEmpty(),
        onValueChange = { onChange(options.copy(outputFileName = it.ifBlank { null })) },
        label = { Text("Output filename (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SwitchRow("Embed album art", options.embedThumbnail) { onChange(options.copy(embedThumbnail = it)) }
    SwitchRow("Embed metadata", options.embedMetadata) { onChange(options.copy(embedMetadata = it)) }
    SwitchRow("Normalize volume", options.normalizeVolume) { onChange(options.copy(normalizeVolume = it)) }
}
