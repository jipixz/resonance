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
import kotlin.math.sqrt
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
        // 1/4, not 1/8: the coarser decode blended small vivid regions into
        // whatever surrounded them, which is exactly the detail that decides
        // what colour a cover "is".
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeStream(it, null, options)
        if (bitmap == null) {
            Log.w("ArtworkAccent", "decodeStream returned null for $uri")
            return@use null
        }
        val palette = Palette.from(bitmap).clearFilters().maximumColorCount(32).generate()
        bitmap.recycle()

        // Every swatch competes on merit rather than working down a fixed list
        // of Palette's named ones. The ordered-list version put dominantSwatch
        // second, so a cover whose largest region is a muddy blend — a dark
        // figure over a gradient, say — handed back that blend and stopped
        // looking, even with vivid greens and purples elsewhere in the frame.
        // Area alone is a bad proxy for "the colour of this record".
        //
        // Saturation is squared and population only rooted, so vividness
        // dominates the score while presence still breaks ties. A brilliant
        // colour occupying a corner should beat a large wash of nearly-grey,
        // but a single vivid pixel should not beat a whole vivid background.
        fun Palette.Swatch.score(): Float {
            val saturation = hsl[1]
            return saturation * saturation * sqrt(population.toFloat())
        }

        val swatch = palette.swatches
            .filter { it.hsl[1] >= STRONG_SATURATION }
            .maxByOrNull { it.score() }
            ?: palette.swatches
                .filter { it.hsl[1] >= MIN_SATURATION }
                .maxByOrNull { it.score() }

        val rgb = swatch?.rgb
        if (rgb == null) {
            Log.d("ArtworkAccent", "No colourful swatch for $uri; leaving untinted")
            return@use null
        }
        // Even the best swatch on a busy cover can come back half-washed,
        // and every downstream use darkens it further — by the time it is a
        // background it would be indistinguishable from the theme. Lifting
        // saturation to a floor keeps the hue the artwork actually chose while
        // making sure it survives being darkened three times over.
        val boosted = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb, it) }
        boosted[1] = boosted[1].coerceAtLeast(TINT_SATURATION_FLOOR)
        val finalRgb = android.graphics.Color.HSVToColor(boosted)

        Log.d("ArtworkAccent", "Extracted ${Integer.toHexString(finalRgb)} from $uri")
        Color(finalRgb)
    }
}.onFailure {
    Log.w("ArtworkAccent", "Failed to extract accent from $uri", it)
}.getOrNull()

/**
 * How saturated a swatch has to be before it counts as a colour rather than a
 * shade of grey. Deliberately low: a lot of real album art is muted without
 * being monochrome, and a strict threshold would send all of it untinted.
 */
private const val MIN_SATURATION = 0.12f

/** What counts as unambiguously a colour, tried before settling for less. */
private const val STRONG_SATURATION = 0.35f

/** Floor applied to the chosen colour so it survives being darkened. */
private const val TINT_SATURATION_FLOOR = 0.45f

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
