package com.jipix.resonance.ui.player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.delay
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
        Log.d("ArtworkAccent", "effect fired: enabled=$enabled artworkUri=$artworkUri")
        accent = if (!enabled || artworkUri.isNullOrBlank()) {
            null
        } else {
            val uri = Uri.parse(artworkUri)
            var result = withContext(Dispatchers.IO) { extractAccent(context, uri) }
            if (result == null) {
                // The legacy albumart provider occasionally returns a stream
                // that fails to decode right after a track transition — seen
                // live on device, silently, with no exception to log: the
                // cover displays fine via Coil's own separate load, but this
                // provider call comes back empty. A short-lived contention
                // issue reads as one retry away from working, not a dead end.
                delay(200)
                result = withContext(Dispatchers.IO) { extractAccent(context, uri) }
            }
            result
        }
        Log.d("ArtworkAccent", "effect settled: accent=$accent")
    }

    return accent
}

private fun extractAccent(context: Context, uri: Uri): Color? = runCatching {
    val stream = context.contentResolver.openInputStream(uri)
    if (stream == null) {
        Log.w("ArtworkAccent", "openInputStream returned null for $uri")
        return@runCatching null
    }
    stream.use {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bitmap = BitmapFactory.decodeStream(it, null, options)
        if (bitmap == null) {
            Log.w("ArtworkAccent", "decodeStream returned null for $uri")
            return@use null
        }
        val palette = Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()
        bitmap.recycle()
        // Vibrant reads as "the colour of this cover"; the rest of this list
        // is what used to be a hard stop at dominantSwatch — a wash that is
        // itself near-black (or otherwise swatch-less under Palette's default
        // filters) used to mean no tint at all rather than falling further
        // back to *some* colour from the cover.
        val candidates = listOfNotNull(
            palette.vibrantSwatch,
            palette.dominantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch,
        )
        // A cover that's mostly grey (concrete, smoke, a black-and-white
        // photo) otherwise had its dominant swatch win by sheer area even
        // when a real, saturated colour was also present elsewhere in the
        // list — prefer the first swatch that actually reads as a colour,
        // and only fall back to a grey one if that's truly all there is.
        val swatch = candidates.firstOrNull { it.hsl[1] >= 0.15f } ?: candidates.firstOrNull()
        val rgb = swatch?.rgb
        if (rgb == null) {
            Log.w("ArtworkAccent", "No swatch of any kind for $uri (${palette.swatches.size} swatches)")
            return@use null
        }
        Log.d("ArtworkAccent", "Extracted ${Integer.toHexString(rgb)} from $uri")
        Color(rgb)
    }
}.onFailure {
    Log.w("ArtworkAccent", "Failed to extract accent from $uri", it)
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
