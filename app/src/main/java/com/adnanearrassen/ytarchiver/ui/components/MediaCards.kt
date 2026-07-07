package com.adnanearrassen.ytarchiver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnanearrassen.ytarchiver.core.common.Formatters
import com.adnanearrassen.ytarchiver.domain.model.ArchivedMedia
import java.io.File

/** A large, YouTube-style video card for vertical feeds. */
@Composable
fun VideoCard(
    media: ArchivedMedia,
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
        Thumbnail(
            // Prefer the saved image; fall back to a frame from the video file.
            url = media.thumbnailPath ?: media.filePath,
            durationSeconds = media.durationSeconds,
            watchProgress = media.watchProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (media.uploader?.firstOrNull() ?: '•').uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        media.uploader?.let { append(it); append(" · ") }
                        append(Formatters.bytes(media.fileSizeBytes))
                        media.resolutionLabel?.let { append(" · "); append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDelete != null || onToggleFavorite != null) {
                MediaOverflowMenu(media.isFavorite, onToggleFavorite, onDelete)
            } else if (media.isFavorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
        }
    }
}

/** A compact horizontal card for carousels (Continue watching). */
@Composable
fun CompactVideoCard(
    media: ArchivedMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    playlistBadge: Boolean = false,
) {
    Column(
        modifier = modifier
            .width(240.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            Thumbnail(
                url = media.thumbnailPath ?: media.filePath,
                durationSeconds = media.durationSeconds,
                watchProgress = media.watchProgress,
                modifier = Modifier.fillMaxWidth(),
            )
            if (playlistBadge) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Playlist", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (onRemove != null) RemoveBadge(onRemove, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = media.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = media.uploader ?: "Unknown",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A single-line music row with square artwork. */
@Composable
fun MusicRow(
    media: ArchivedMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp),
        ) {
            if (media.thumbnailPath != null) {
                AsyncImage(
                    model = File(media.thumbnailPath),
                    contentDescription = null,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.uploader ?: "Unknown artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = Formatters.duration(media.durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onDelete != null || onToggleFavorite != null) {
            MediaOverflowMenu(media.isFavorite, onToggleFavorite, onDelete)
        }
    }
}

/** A small circular ✕ badge, e.g. to remove a card from "Continue watching". */
@Composable
fun RemoveBadge(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(28.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Three-dot menu with Favorite/Unfavorite and Delete actions. */
@Composable
fun MediaOverflowMenu(
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onToggleFavorite != null) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove favorite" else "Add to favorites") },
                    leadingIcon = {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    onClick = { expanded = false; onToggleFavorite() },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { expanded = false; onDelete() },
                )
            }
        }
    }
}
