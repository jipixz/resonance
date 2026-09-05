package com.jipix.resonance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import com.jipix.resonance.R

/**
 * Every icon the app draws, and nothing else.
 *
 * This replaces `material-icons-extended`, which ships one generated class per
 * icon — several thousand of them — and was the bulk of a 72 MB debug APK for
 * the sake of the 35 used here. R8 strips the rest in release builds, but debug
 * builds do not run R8, so every install carried the whole set.
 *
 * Each name maps to a standalone vector drawable in `res/drawable`. To swap one,
 * export it from fonts.google.com and paste over that single file; nothing here
 * needs to change.
 */
object ResonanceIcons {

    val Add: ImageVector @Composable get() = load(R.drawable.ic_add)
    val Album: ImageVector @Composable get() = load(R.drawable.ic_album)
    val ArrowBack: ImageVector @Composable get() = load(R.drawable.ic_arrow_back)
    val Bedtime: ImageVector @Composable get() = load(R.drawable.ic_bedtime)
    val Bluetooth: ImageVector @Composable get() = load(R.drawable.ic_bluetooth)
    val Close: ImageVector @Composable get() = load(R.drawable.ic_close)
    val ContentCopy: ImageVector @Composable get() = load(R.drawable.ic_content_copy)
    val Delete: ImageVector @Composable get() = load(R.drawable.ic_delete)
    val DeleteOutline: ImageVector @Composable get() = load(R.drawable.ic_delete_outline)
    val DeleteSweep: ImageVector @Composable get() = load(R.drawable.ic_delete_sweep)
    val DragHandle: ImageVector @Composable get() = load(R.drawable.ic_drag_handle)
    val FileOpen: ImageVector @Composable get() = load(R.drawable.ic_file_open)
    val Folder: ImageVector @Composable get() = load(R.drawable.ic_folder)
    val Headphones: ImageVector @Composable get() = load(R.drawable.ic_headphones)
    val Image: ImageVector @Composable get() = load(R.drawable.ic_image)
    val KeyboardArrowDown: ImageVector
        @Composable get() = load(R.drawable.ic_keyboard_arrow_down)
    val KeyboardArrowUp: ImageVector @Composable get() = load(R.drawable.ic_keyboard_arrow_up)
    val MoreVert: ImageVector @Composable get() = load(R.drawable.ic_more_vert)
    val MusicNote: ImageVector @Composable get() = load(R.drawable.ic_music_note)
    val MyLocation: ImageVector @Composable get() = load(R.drawable.ic_my_location)
    val Pause: ImageVector @Composable get() = load(R.drawable.ic_pause)
    val Person: ImageVector @Composable get() = load(R.drawable.ic_person)
    val PlayArrow: ImageVector @Composable get() = load(R.drawable.ic_play_arrow)
    val PlaylistAdd: ImageVector @Composable get() = load(R.drawable.ic_playlist_add)
    val QueueMusic: ImageVector @Composable get() = load(R.drawable.ic_queue_music)
    val Repeat: ImageVector @Composable get() = load(R.drawable.ic_repeat)
    val RepeatOne: ImageVector @Composable get() = load(R.drawable.ic_repeat_one)
    val Save: ImageVector @Composable get() = load(R.drawable.ic_save)
    val Search: ImageVector @Composable get() = load(R.drawable.ic_search)
    val Shuffle: ImageVector @Composable get() = load(R.drawable.ic_shuffle)
    val SkipNext: ImageVector @Composable get() = load(R.drawable.ic_skip_next)
    val SkipPrevious: ImageVector @Composable get() = load(R.drawable.ic_skip_previous)
    val Speaker: ImageVector @Composable get() = load(R.drawable.ic_speaker)
    val Usb: ImageVector @Composable get() = load(R.drawable.ic_usb)
    val VolumeUp: ImageVector @Composable get() = load(R.drawable.ic_volume_up)

    @Composable
    @ReadOnlyComposable
    private fun load(id: Int): ImageVector =
        ImageVector.vectorResource(LocalContext.current.theme, LocalContext.current.resources, id)
}
