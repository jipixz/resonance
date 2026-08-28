package com.jipix.resonance.ui.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dominant colour of the current cover, or null when there is nothing to sample
 * or the feature is switched off.
 *
 * The bitmap is decoded at 1/8 scale purely to feed Palette — a thumbnail is
 * plenty for averaging colour, and decoding a full 3000px cover on every track
 * change would be an obvious waste of battery.
 */
@Composable
fun rememberArtworkAccent(artworkUri: String?, enabled: Boolean): Color? {
    val context = LocalContext.current
    var accent by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkUri, enabled) {
        accent = if (!enabled || artworkUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { extractAccent(context, Uri.parse(artworkUri)) }
        }
    }

    return accent
}

private fun extractAccent(context: Context, uri: Uri): Color? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bitmap = BitmapFactory.decodeStream(stream, null, options) ?: return@use null
        val palette = Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()
        bitmap.recycle()
        // Vibrant reads as "the colour of this cover"; dominant is often the
        // background wash and can be near-black on dark artwork.
        val rgb = palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: return@use null
        Color(rgb)
    }
}.getOrNull()

/** Pulls a colour toward black by [amount], 0f leaving it untouched. */
fun Color.darken(amount: Float): Color = androidx.compose.ui.graphics.lerp(
    this,
    Color.Black,
    amount.coerceIn(0f, 1f),
)

/** Pushes a colour toward white by [amount], for elements that must stand out. */
fun Color.lighten(amount: Float): Color = androidx.compose.ui.graphics.lerp(
    this,
    Color.White,
    amount.coerceIn(0f, 1f),
)

/** Black or white, whichever stays readable on this background. */
fun Color.readableOn(): Color = if (luminance() > 0.4f) Color.Black else Color.White

/**
 * Same hue and lightness, less saturation. For elements that should read as
 * related to an accent colour without competing with whatever actually uses
 * it at full strength (e.g. skip buttons next to a filled play button).
 */
fun Color.desaturate(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] *= (1f - amount.coerceIn(0f, 1f))
    return Color(android.graphics.Color.HSVToColor(hsv))
}
