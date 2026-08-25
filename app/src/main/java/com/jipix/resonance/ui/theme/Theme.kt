package com.jipix.resonance.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Collapses every surface tier onto true black. Keeps the dynamic accent colours
 * untouched, so the app still looks like Material You — it just stops lighting up
 * OLED pixels it does not need to. This is the night-listening mode.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1E1E1E),
)

@Composable
fun ResonanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You wallpaper colours. Only meaningful on API 31+. */
    dynamicColor: Boolean = true,
    /** True black surfaces. Only applied when the theme is already dark. */
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> FallbackDarkScheme
        else -> FallbackLightScheme
    }

    val scheme = if (darkTheme && amoled) base.toAmoled() else base

    MaterialTheme(
        colorScheme = scheme,
        typography = ResonanceTypography,
        content = content,
    )
}
