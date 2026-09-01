package com.jipix.resonance.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jipix.resonance.R
import com.jipix.resonance.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The home-screen widget.
 *
 * ## One widget, three shapes — not three widgets
 *
 * BlackPlayer ships a separate widget per size, so choosing one is a decision
 * made at placement time with no preview and no way back except deleting and
 * starting over. Since Android 12 a widget can simply answer for several sizes,
 * so this is one entry in the picker that re-lays itself out as it is resized:
 *
 * - **narrow** — art, title, play. What fits beside other widgets in a row.
 * - **wide** — art, title, artist, previous/play/next. The everyday shape.
 * - **tall** — large art above the metadata and transport. The one you place
 *   because you want to see the record, not just control it.
 *
 * ## What it deliberately does not do
 *
 * No progress bar and no elapsed time. Both mean redrawing every second, on the
 * home screen, forever, whether or not anyone is looking — and this project
 * exists because a player was doing exactly that kind of thing in the
 * background. The notification already shows position for anyone who wants it.
 *
 * Colour comes from [GlanceTheme], which on Android 12+ is the same wallpaper
 * palette the app itself uses. A widget sits on the user's wallpaper rather than
 * on our surface, so matching the system is the only choice that works against
 * every background — including a photo of a dog in glasses.
 */
class ResonanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(NARROW, WIDE, TALL)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetBody()
            }
        }
    }

    companion object {
        val NARROW = DpSize(140.dp, 60.dp)
        val WIDE = DpSize(250.dp, 60.dp)
        val TALL = DpSize(250.dp, 180.dp)
    }
}

@Composable
private fun WidgetBody() {
    val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
    val size = LocalSize.current

    val title = prefs[WidgetState.TITLE].orEmpty()
    val artist = prefs[WidgetState.ARTIST].orEmpty()
    val artworkUri = prefs[WidgetState.ARTWORK_URI].orEmpty().takeIf { it.isNotBlank() }
    val isPlaying = prefs[WidgetState.IS_PLAYING] ?: false
    val hasQueue = prefs[WidgetState.HAS_QUEUE] ?: false

    val tall = size.height >= ResonanceWidget.TALL.height
    val wide = size.width >= ResonanceWidget.WIDE.width

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        when {
            !hasQueue -> EmptyState()
            tall -> TallLayout(title, artist, artworkUri, isPlaying)
            wide -> WideLayout(title, artist, artworkUri, isPlaying)
            else -> NarrowLayout(title, artworkUri, isPlaying)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Resonance",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
            ),
        )
    }
}

@Composable
private fun NarrowLayout(title: String, artworkUri: String?, isPlaying: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artworkUri, 40.dp)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = title,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        PlayPause(isPlaying)
    }
}

@Composable
private fun WideLayout(title: String, artist: String, artworkUri: String?, isPlaying: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artworkUri, 48.dp)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = artist,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        TransportRow(isPlaying)
    }
}

@Composable
private fun TallLayout(title: String, artist: String, artworkUri: String?, isPlaying: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(artworkUri, 96.dp)
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = artist,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(8.dp))
        TransportRow(isPlaying)
    }
}

@Composable
private fun TransportRow(isPlaying: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetButton(R.drawable.ic_skip_previous, "Anterior", WidgetAction.PREVIOUS)
        Spacer(GlanceModifier.width(4.dp))
        PlayPause(isPlaying)
        Spacer(GlanceModifier.width(4.dp))
        WidgetButton(R.drawable.ic_skip_next, "Siguiente", WidgetAction.NEXT)
    }
}

@Composable
private fun PlayPause(isPlaying: Boolean) {
    // The one filled control, so the primary action is findable without reading
    // — which is the whole point of a widget you tap while doing something else.
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .clickable(actionRunCallback<WidgetActionCallback>(WidgetAction.params(WidgetAction.PLAY_PAUSE))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            ),
            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

@Composable
private fun WidgetButton(iconRes: Int, description: String, action: String) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .clickable(actionRunCallback<WidgetActionCallback>(WidgetAction.params(action))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

/**
 * Album art, decoded at roughly the size it will be drawn.
 *
 * A widget's bitmaps cross a Binder transaction to the launcher, and that
 * transaction has a hard size limit — handing over a full-resolution cover is
 * how a widget silently fails to appear at all.
 */
@Composable
private fun Artwork(uri: String?, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap = remember(uri, size) { loadArtwork(context, uri, size.value.toInt() * 3) }

    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_album),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(size / 2),
            )
        }
    }
}

private fun loadArtwork(context: Context, uri: String?, targetPx: Int): Bitmap? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            val bytes = stream.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sample = 1
            while (bounds.outWidth / sample > targetPx * 2) sample *= 2

            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }.getOrNull()
}
