<div align="center">

# Resonance

**A local music player for Android.**
BlackPlayer EX's feature set, a first-party Google app's skin, and battery as the first constraint.

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.08-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-1.11.0-FF6F00?style=for-the-badge)](https://developer.android.com/media/media3)

[![minSdk](https://img.shields.io/badge/minSdk-30-blue?style=flat-square)](https://apilevels.com)
[![targetSdk](https://img.shields.io/badge/targetSdk-37-blue?style=flat-square)](https://apilevels.com)
[![Material You](https://img.shields.io/badge/Material%20You-dynamic%20color-6750A4?style=flat-square)](https://m3.material.io)
[![Room](https://img.shields.io/badge/Room-2.8.4-green?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Gradle](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=flat-square&logo=gradle)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.3.2-3DDC84?style=flat-square)](https://developer.android.com/build)

*[Léelo en español](README.es.md)*

</div>

---

## Screenshots

> Fill these gaps with your own screenshots: drop them in `docs/screenshots/` with
> these names and they show up on their own.

| Library | Player | Queue |
|:---:|:---:|:---:|
| ![Library](docs/screenshots/library.png) | ![Player](docs/screenshots/player.png) | ![Queue](docs/screenshots/queue.png) |

| Drawer | Search | Landscape |
|:---:|:---:|:---:|
| ![Drawer](docs/screenshots/drawer.png) | ![Search](docs/screenshots/search.png) | ![Landscape](docs/screenshots/landscape.png) |

---

## Why it exists

BlackPlayer EX does everything I want from a local player, but it **drains the battery
listening at night with the screen off**. The reasons are structural and already obsolete,
so rebuilding it beat living with it.

Two constraints drive every decision in this project:

**1. It has to look like a Google app.** Material 3 / Material You, in the spirit of
Recorder, Gmail, Calculator, Calendar, Weather, Drive and Fit — plus the fluidity of Solid
Explorer. And explicitly **not** like YouTube Music.

**2. Battery cost outranks feature count.** When a feature and battery life conflict,
battery wins, and the feature ships opt-in and disabled.

---

## The battery architecture

This is the heart of the project, not an optimization detail.

| Mechanism | What it does | Where |
|---|---|---|
| **Audio offload** | The chip's DSP decodes, the CPU sleeps between batches | `PlaybackService.enableAudioOffload()` |
| `WAKE_MODE_LOCAL` | ExoPlayer holds the wake lock only while rendering | `PlaybackService.onCreate()` |
| 500 ms poll | And only while playing *and* the UI is collecting | `PlayerConnection.startProgressTicker()` |
| Clean stop | The service stops when nothing is left queued | `PlaybackService.onTaskRemoved()` |

### What offload is

A phone ships with an **audio DSP**: a small, specialized processor separate from the CPU.
With offload, you hand it the compressed file and it decodes. Without offload, the CPU
wakes up hundreds of times a second to refill the buffer; with offload it receives several
seconds at once and sleeps until the next batch.

Requested with `setIsGaplessSupportRequired(true)`: if a device can't do both, it must
**refuse** offload rather than insert a gap between tracks.

### The crossfade trade-off

The DSP renders **one** stream, not two. The moment you want two tracks playing at once,
the work goes back to the CPU. That's why crossfade is opt-in and ships off, and
`PlaybackService` automatically switches offload off when it's turned on.

It's not much at all (decoding runs around 1–3% of one core), but with the screen off
that becomes the only thing awake on the phone.

---

## What it does

- **Library** — scanned from MediaStore, cached in Room, incremental rescan
- **Browsing** — songs, albums, artists, and playlists, with swipeable tabs
- **Detail** screens for album, artist, and playlist, sharing one layout
- **Playback** — media session, notification, lock screen, Bluetooth
- **Gapless** always, real **crossfade** opt-in (3/6/10 s)
- **Queue** — drag to reorder, jump to current, shuffle what's left, drop
  duplicates, save as a playlist, remaining/total time
- **Playlists** — create, add, remove, 2×2 mosaic cover or a chosen one
- **Search** with debounce over the cache
- **Folder blacklist**, applied at query time, not at scan time
- **Material You** — wallpaper-derived color, real AMOLED mode, artwork tinting
- **Stats** for plays and skips

---

## Design decisions worth knowing

**A single `ExoPlayer`.** `PlaybackService` is its only owner. The UI, the notification,
Bluetooth, and the lock screen all reach it through a `MediaController`, so there is
exactly one source of truth for what's playing.

**Scanning reads MediaStore's index, not the files.** One cursor pass instead of
thousands of file opens. `MusicRepository.sync()` diffs on `dateModified`, so a launch
where nothing changed costs one query and zero writes.

**Folder exclusions are applied at query time.** Filtering them at scan time would make
an excluded folder disappear from its own settings screen and you'd never be able to
turn it back on.

**Crossfade inverts the obvious design.** Media3 ties a session to one `Player` for its
whole life, so instead of alternating between two players, the session's own player
always carries the *incoming* track and a second, throwaway player fades out the
*outgoing* one. The seam lands on the track that's already headed to silence.

---

## Stack

| Layer | What's used |
|---|---|
| Language | Kotlin 2.4.10 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2026.08.00) |
| Playback | AndroidX Media3 / ExoPlayer 1.11.0 |
| Persistence | Room 2.8.4 (KSP), DataStore Preferences |
| Images | Coil 3.5.0 |
| Color | AndroidX Palette (extracted from artwork) |
| DI | Hand-rolled `AppContainer` — no framework |
| Build | Gradle 9.7.1, AGP 9.3.2, JDK 25 |

No Hilt, no Retrofit, no real Navigation-Compose: the project is small enough that each
framework would cost more build complexity than it removes.

---

## Getting started

### Requirements

- **Android Studio** (a recent stable release) — ships its own JDK and SDK
- **JDK 25** — the one bundled with Android Studio (`Settings → Build Tools → Gradle →
  Gradle JDK`)
- A device or emulator running **Android 11 or newer** (`minSdk 30`)

> The system JDK is usually older, and Gradle rejects it. If you see an error that's
> just a bare version string (`25.0.2`) with no explanation, that's Gradle or AGP too old
> for the JDK — not an AGP-specific bug.

### Build

```bash
git clone git@github.com:jipixz/resonance.git
cd resonance
```

From Android Studio: open the folder, let it sync, hit Run.

From the terminal (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first launch asks for music access; the library builds itself from MediaStore.

---

## Structure

```
core/           AppContainer (hand-rolled DI), SettingsStore (DataStore)
data/db/        Room entities, DAO, database, migrations
data/media/     MediaStoreScanner — the only thing that touches MediaStore
data/           MusicRepository — reconciles the cache, exposes flows
playback/       PlaybackService (owns the only ExoPlayer)
                PlayerConnection (bridge to the UI)
                CrossfadeEngine (the second, throwaway player)
ui/theme/       Material You scheme, AMOLED override, type scale
ui/library/     Lists, album grid, detail screens, search, folders
ui/player/      MiniPlayer, PlayerScreen, QueueSheet, color extraction
```

---

## Gotchas already hit

- `MediaStore.Audio.ALBUM_ARTIST` only exists from **API 30**. That's why `minSdk` is 30,
  not 29: querying it below that throws on the cursor.
- Room's own SQL parser, not SQLite, is the real constraint on `@Query`. Raw `UPSERT`s
  were replaced with `@Transaction` methods.
- MediaStore packs multi-disc releases into `TRACK` as `disc * 1000 + track`. Unpacked in
  `MediaStoreScanner` — never read `TRACK` raw.
- **AGP 9 has built-in Kotlin support**: applying `org.jetbrains.kotlin.android` is now a
  hard error, and `kotlinOptions {}` went with it.
- **Coil 3** moved every package from `coil.*` to `coil3.*`.
- Rotating the phone used to recreate the Activity and replay the launch animation; fixed
  with `configChanges` in the manifest, not more saved state.

---

## Not yet built

- Tag editing (needs a tagging library + `MediaStore.createWriteRequest`)
- Equalizer — heads up: any `AudioEffect` breaks offload
- The debug APK is ~72 MB, almost all `material-icons-extended`. R8 strips it in release,
  but dropping that dependency would be the cheaper fix.

---

## Credits

- Logo typeface: [Pacifico](https://fonts.google.com/specimen/Pacifico), under the
  SIL Open Font License 1.1.
- Functional inspiration: **BlackPlayer EX**. Visual inspiration: Google's own apps and
  **Solid Explorer**.
