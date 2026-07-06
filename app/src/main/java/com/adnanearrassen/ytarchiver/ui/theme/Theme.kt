package com.adnanearrassen.ytarchiver.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * User-selectable accent seeds (mirrors [com.adnanearrassen.ytarchiver.domain.model.ThemeColor]).
 * Kept as a plain enum here so the theme module has no dependency on domain.
 */
enum class AccentSeed(val seed: Color) {
    Red(BrandRed),
    Blue(AccentBlue),
    Purple(AccentPurple),
    Green(AccentGreen),
    Orange(AccentOrange),
    Pink(AccentPink),
}

private fun darkSchemeFor(seed: Color) = darkColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondary = seed,
    tertiary = seed,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = StatusError,
)

private fun lightSchemeFor(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondary = seed,
    tertiary = seed,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = StatusError,
)

@Composable
fun YoutubeArchiverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: AccentSeed = AccentSeed.Red,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkSchemeFor(accent.seed)
        else -> lightSchemeFor(accent.seed)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
