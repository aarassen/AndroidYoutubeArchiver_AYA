package com.adnanearrassen.ytarchiver.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.adnanearrassen.ytarchiver.core.common.Formatters
import kotlinx.coroutines.delay

private enum class GestureSide { NONE, BRIGHTNESS, VOLUME }

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val hasSubtitles by viewModel.hasSubtitles.collectAsStateWithLifecycle()
    val isPlaylist by viewModel.isPlaylist.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val hasPrevious by viewModel.hasPrevious.collectAsStateWithLifecycle()
    val castDeviceName by viewModel.castDeviceName.collectAsStateWithLifecycle()
    var subtitlesEnabled by remember { mutableStateOf(false) }
    val localPlayer = viewModel.player
    // The player controls should drive: local ExoPlayer, or the Cast player while
    // casting to a TV.
    val player by viewModel.activePlayer.collectAsStateWithLifecycle()
    var showCastDialog by remember { mutableStateOf(false) }
    val castRoutes = rememberCastRoutes(active = showCastDialog)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // --- Player state ---
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    // --- Gesture (brightness/volume) state ---
    var gestureSide by remember { mutableStateOf(GestureSide.NONE) }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
        )
    }
    var volume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }
    var indicatorVisible by remember { mutableStateOf(false) }

    // Enter immersive fullscreen landscape; the rest of the app is portrait-locked
    // (see the manifest), so on exit we explicitly restore portrait.
    DisposableEffect(Unit) {
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.let {
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                // Return brightness control to the system.
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // Keep isPlaying / duration in sync with the player.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Poll position while the screen is up.
    LaunchedEffect(Unit) {
        while (true) {
            if (!scrubbing) position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    // Auto-hide controls after a few seconds of no interaction while playing.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Auto-hide the brightness/volume indicator shortly after the gesture ends.
    LaunchedEffect(indicatorVisible, gestureSide) {
        if (indicatorVisible && gestureSide == GestureSide.NONE) {
            delay(700)
            indicatorVisible = false
        }
    }

    // Re-apply the subtitle preference when the current item changes (playlist).
    LaunchedEffect(media?.id, subtitlesEnabled) {
        viewModel.setSubtitlesEnabled(subtitlesEnabled)
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video surface only — controls are drawn in Compose on top.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // The on-screen surface always shows the LOCAL player; while
                    // casting it's paused and the "Casting…" overlay takes over.
                    this.player = localPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture layer: tap toggles controls; vertical drag on the left half
        // controls brightness, on the right half controls volume (VLC-style).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val target = if (offset.x < size.width / 2)
                                (player.currentPosition - 10_000).coerceAtLeast(0)
                            else
                                (player.currentPosition + 10_000)
                            player.seekTo(target)
                        },
                    )
                }
                .pointerInput(maxVolume) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            gestureSide = if (offset.x < size.width / 2)
                                GestureSide.BRIGHTNESS else GestureSide.VOLUME
                            indicatorVisible = true
                        },
                        onDragEnd = { gestureSide = GestureSide.NONE },
                        onDragCancel = { gestureSide = GestureSide.NONE },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Up = increase. Full-height swipe ≈ full range.
                            val delta = -dragAmount / size.height
                            when (gestureSide) {
                                GestureSide.BRIGHTNESS -> {
                                    brightness = (brightness + delta).coerceIn(0.01f, 1f)
                                    activity?.window?.let { w ->
                                        w.attributes = w.attributes.apply { screenBrightness = brightness }
                                    }
                                    indicatorVisible = true
                                }
                                GestureSide.VOLUME -> {
                                    volume = (volume + delta).coerceIn(0f, 1f)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (volume * maxVolume).toInt(),
                                        0,
                                    )
                                    indicatorVisible = true
                                }
                                GestureSide.NONE -> {}
                            }
                        },
                    )
                }
        )

        // While casting, the local surface is blank — show a cast placeholder.
        castDeviceName?.let { name ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.CastConnected,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Casting to $name", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Brightness / volume level indicator (centered).
        AnimatedVisibility(
            visible = indicatorVisible && gestureSide != GestureSide.NONE,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            LevelIndicator(
                icon = if (gestureSide == GestureSide.BRIGHTNESS) Icons.Filled.Brightness6
                else Icons.AutoMirrored.Filled.VolumeUp,
                level = if (gestureSide == GestureSide.BRIGHTNESS) brightness else volume,
            )
        }

        // Controls overlay.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                title = media?.title.orEmpty(),
                isPlaying = isPlaying,
                position = if (scrubbing) scrubPosition.toLong() else position,
                duration = duration,
                showCastButton = viewModel.castAvailable,
                isCasting = castDeviceName != null,
                onCast = { showCastDialog = true },
                showSubtitleButton = hasSubtitles,
                subtitlesEnabled = subtitlesEnabled,
                onToggleSubtitles = { subtitlesEnabled = !subtitlesEnabled },
                showNextPrev = isPlaylist,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                onNext = { viewModel.playNext() },
                onPrevious = { viewModel.playPrevious() },
                onClose = onBack,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                    controlsVisible = true
                },
                onRewind = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                onForward = { player.seekTo(player.currentPosition + 10_000) },
                onScrub = { scrubbing = true; scrubPosition = it },
                onScrubFinished = {
                    player.seekTo(scrubPosition.toLong())
                    scrubbing = false
                },
            )
        }
    }

    if (showCastDialog) {
        CastDeviceDialog(state = castRoutes, onDismiss = { showCastDialog = false })
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    showCastButton: Boolean,
    isCasting: Boolean,
    onCast: () -> Unit,
    showSubtitleButton: Boolean,
    subtitlesEnabled: Boolean,
    onToggleSubtitles: () -> Unit,
    showNextPrev: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        // Top bar: close + title.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (showCastButton) {
                IconButton(onClick = onCast) {
                    Icon(
                        if (isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                        contentDescription = "Cast to TV",
                        tint = Color.White,
                    )
                }
            }
            if (showSubtitleButton) {
                IconButton(onClick = onToggleSubtitles) {
                    Icon(
                        if (subtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                        contentDescription = "Subtitles",
                        tint = Color.White,
                    )
                }
            }
        }

        // Center transport: rewind / play-pause / forward.
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showNextPrev) {
                IconButton(onClick = onPrevious, enabled = hasPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious, "Previous",
                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            IconButton(onClick = onRewind, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            IconButton(onClick = onForward, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            if (showNextPrev) {
                IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipNext, "Next",
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        }

        // Bottom: seek bar + time.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Formatters.duration(position / 1000), color = Color.White, style = MaterialTheme.typography.labelMedium)
            val maxValue = duration.toFloat().coerceAtLeast(1f)
            Slider(
                value = position.toFloat().coerceIn(0f, maxValue),
                onValueChange = onScrub,
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..maxValue,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.4f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text(Formatters.duration(duration / 1000), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Centered VLC-style level indicator with an icon and a vertical bar. */
@Composable
private fun LevelIndicator(icon: ImageVector, level: Float) {
    Column(
        modifier = Modifier
            .size(width = 78.dp, height = 190.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        // Vertical fill bar.
        Box(
            modifier = Modifier
                .width(8.dp)
                .weight(1f)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .background(Color.White, RoundedCornerShape(4.dp))
            )
        }
        Text("${(level * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Unwraps the Activity from a (possibly wrapped) Compose context. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
