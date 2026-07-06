package com.adnanearrassen.ytarchiver.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.domain.model.Playlist

/** Cover thumbnail with the YouTube-style "playlist" badge showing item count. */
@Composable
private fun PlaylistCover(playlist: Playlist, modifier: Modifier = Modifier) {
    Box(modifier) {
        Thumbnail(url = playlist.thumbnailPath, modifier = Modifier.fillMaxWidth())
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 12.dp),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${playlist.itemCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Full-width playlist card, sized exactly like a normal video card so playlists
 * sit naturally in the same scrollable feed.
 */
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        PlaylistCover(playlist, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("Playlist · ${playlist.itemCount} videos")
                        if (playlist.totalDurationSeconds > 0) {
                            append(" · ")
                            append(Formatters.duration(playlist.totalDurationSeconds))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDelete != null || onToggleFavorite != null) {
                MediaOverflowMenu(playlist.isFavorite, onToggleFavorite, onDelete)
            }
        }
    }
}
