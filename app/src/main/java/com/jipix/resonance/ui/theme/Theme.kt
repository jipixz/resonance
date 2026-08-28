package com.jipix.resonance.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the platform overscroll's event handling — a scroll dragged past a list's
 * edge is still absorbed locally — while dropping its visual stretch/glow. Without
 * that absorption, dragging past the end of a list *inside* a bottom sheet (the
 * queue) leaks the leftover drag into the sheet's own nested-scroll connection,
 * which nudges the whole sheet and springs it back — a bigger, worse "jump" than
 * the stretch it was meant to replace. [com.jipix.resonance.ui.library.ScrollEndFade]
 * supplies the boundary cue instead.
 */
private class QuietOverscrollFactory(private val platform: OverscrollFactory) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        platform.createOverscrollEffect().withoutVisualEffect()

    override fun hashCode(): Int = platform.hashCode()
    override fun equals(other: Any?): Boolean =
        other is QuietOverscrollFactory && other.platform == platform
}

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
    val platformOverscroll = rememberPlatformOverscrollFactory()
    val quietOverscroll = remember(platformOverscroll) { QuietOverscrollFactory(platformOverscroll) }

    MaterialTheme(
        colorScheme = scheme,
        typography = ResonanceTypography,
    ) {
        // The platform's stretch overscroll reads as too strong for this app's
        // motion — lists jump rather than ease. Scroll edges get a quieter
        // gradient cue of their own instead (see ScrollEndFade).
        CompositionLocalProvider(LocalOverscrollFactory provides quietOverscroll, content = content)
    }
}
