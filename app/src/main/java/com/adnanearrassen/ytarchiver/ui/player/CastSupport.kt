package com.adnanearrassen.ytarchiver.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext

/** Snapshot of discoverable cast routes + selection actions. */
class CastRouteState(
    val routes: List<MediaRouter.RouteInfo>,
    val connectedName: String?,
    val select: (MediaRouter.RouteInfo) -> Unit,
    val disconnect: () -> Unit,
) {
    val isConnected: Boolean get() = connectedName != null
}

/**
 * Discovers Cast devices via [MediaRouter] and exposes select/disconnect. Active
 * scanning only runs while [active] is true (i.e. the chooser dialog is open) to
 * save battery. Self-contained in Compose — no FragmentActivity/theme needed.
 */
@Composable
fun rememberCastRoutes(active: Boolean): CastRouteState {
    val context = LocalContext.current
    val castContext = remember { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
    val router = remember { runCatching { MediaRouter.getInstance(context) }.getOrNull() }
    val selector = remember { castContext?.mergedSelector ?: MediaRouteSelector.EMPTY }

    val routes = remember { mutableStateListOf<MediaRouter.RouteInfo>() }
    var connectedName by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val r = router ?: return
        routes.clear()
        routes.addAll(r.routes.filter { it.matchesSelector(selector) && !it.isDefault && it.isEnabled })
        val sel = r.selectedRoute
        connectedName = sel.takeIf { !it.isDefault && it.matchesSelector(selector) }?.name
    }

    DisposableEffect(active, router) {
        if (router == null) return@DisposableEffect onDispose {}
        val cb = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        val flags = if (active) MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        else MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        router.addCallback(selector, cb, flags)
        refresh()
        onDispose { router.removeCallback(cb) }
    }

    return CastRouteState(
        routes = routes,
        connectedName = connectedName,
        select = { route -> router?.selectRoute(route); refresh() },
        disconnect = { router?.unselect(MediaRouter.UNSELECT_REASON_STOPPED); refresh() },
    )
}

@Composable
fun CastDeviceDialog(state: CastRouteState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cast to device") },
        text = {
            Column {
                if (state.routes.isEmpty()) {
                    Text("Searching for TVs & speakers…", style = MaterialTheme.typography.bodyMedium)
                }
                state.routes.forEach { route ->
                    val selected = route.name == state.connectedName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.select(route); onDismiss() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (selected) Icons.Filled.CastConnected else Icons.Filled.Cast,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(route.name, style = MaterialTheme.typography.bodyLarge)
                            route.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (selected) Icon(Icons.Filled.Check, contentDescription = "Connected")
                    }
                }
            }
        },
        confirmButton = {
            if (state.isConnected) {
                TextButton(onClick = { state.disconnect(); onDismiss() }) { Text("Disconnect") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
