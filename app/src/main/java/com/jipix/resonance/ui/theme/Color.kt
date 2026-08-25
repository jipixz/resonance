package com.jipix.resonance.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback palettes for devices without dynamic color, and the seed for the
 * "no wallpaper colour" case. Deliberately close to a neutral Google-app blue
 * so the app still reads as native when Material You is unavailable.
 */
private val SeedPrimary = Color(0xFF4C6FFF)

val FallbackLightScheme = lightColorScheme(
    primary = SeedPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF001551),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171B2C),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
)

val FallbackDarkScheme = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF1B2A7F),
    primaryContainer = Color(0xFF344397),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
)
