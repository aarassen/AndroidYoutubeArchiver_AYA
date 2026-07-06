package com.adnanearrassen.ytarchiver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adnanearrassen.ytarchiver.core.common.UrlUtils
import com.adnanearrassen.ytarchiver.domain.model.ThemeColor
import com.adnanearrassen.ytarchiver.domain.model.ThemeMode
import com.adnanearrassen.ytarchiver.ui.AppRoot
import com.adnanearrassen.ytarchiver.ui.theme.AccentSeed
import com.adnanearrassen.ytarchiver.ui.theme.YoutubeArchiverTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialSharedUrl = extractSharedUrl(intent)

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()

            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            YoutubeArchiverTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                accent = settings.themeColor.toAccentSeed(),
            ) {
                val sharedUrl = remember { mutableStateOf(initialSharedUrl) }
                AppRoot(
                    incomingUrl = sharedUrl.value,
                    onUrlConsumed = { sharedUrl.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A new share while the app is alive re-launches AppRoot with the URL
        // by recreating; simplest robust behaviour for this skeleton.
        extractSharedUrl(intent)?.let { recreate() }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        intent ?: return null
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        return UrlUtils.firstUrlIn(raw)
    }
}

private fun ThemeColor.toAccentSeed(): AccentSeed = when (this) {
    ThemeColor.RED -> AccentSeed.Red
    ThemeColor.BLUE -> AccentSeed.Blue
    ThemeColor.PURPLE -> AccentSeed.Purple
    ThemeColor.GREEN -> AccentSeed.Green
    ThemeColor.ORANGE -> AccentSeed.Orange
    ThemeColor.PINK -> AccentSeed.Pink
}
