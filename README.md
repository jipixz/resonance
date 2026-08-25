<div align="center">

# Resonance

**Reproductor de música local para Android.**
Las funciones de BlackPlayer EX, la piel de una app de Google, y la batería como primera restricción.

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

</div>

---

## Capturas

> Reemplaza estos huecos con tus propias capturas: déjalas en `docs/screenshots/` con
> estos nombres y se muestran solas.

| Biblioteca | Reproductor | Cola |
|:---:|:---:|:---:|
| ![Biblioteca](docs/screenshots/library.png) | ![Reproductor](docs/screenshots/player.png) | ![Cola](docs/screenshots/queue.png) |

| Menú lateral | Búsqueda | Horizontal |
|:---:|:---:|:---:|
| ![Menú](docs/screenshots/drawer.png) | ![Búsqueda](docs/screenshots/search.png) | ![Horizontal](docs/screenshots/landscape.png) |

---

## Por qué existe

BlackPlayer EX hace todo lo que quiero de un reproductor local, pero **se come la batería
escuchando de noche con la pantalla apagada**. Las razones son estructurales y ya están
obsoletas, así que valía más rehacerlo que aguantarlo.

Dos restricciones mandan sobre todas las decisiones del proyecto:

**1. Tiene que verse como una app de Google.** Material 3 / Material You, en la línea de
Recorder, Gmail, Calculadora, Calendario, Clima, Drive y Fit — más la fluidez de Solid
Explorer. Y explícitamente **no** como YouTube Music.

**2. El coste en batería gana sobre el número de funciones.** Cuando una función y la
autonomía chocan, gana la autonomía, y la función se envía opcional y apagada.

---

## La arquitectura de batería

Esto es el corazón del proyecto, no un detalle de optimización.

| Mecanismo | Qué hace | Dónde |
|---|---|---|
| **Audio offload** | El DSP del chip decodifica y el CPU se duerme entre lotes | `PlaybackService.enableAudioOffload()` |
| `WAKE_MODE_LOCAL` | ExoPlayer sostiene el wakelock solo mientras renderiza | `PlaybackService.onCreate()` |
| Sondeo de 500 ms | Y solo mientras suena *y* la UI está recolectando | `PlayerConnection.startProgressTicker()` |
| Parada limpia | El servicio se detiene si no queda nada en cola | `PlaybackService.onTaskRemoved()` |

### Qué es el offload

Un teléfono trae un **DSP de audio**: un procesador pequeño y especializado, aparte del
CPU. Con offload le entregas el archivo comprimido y él lo decodifica. Sin offload, el CPU
despierta cientos de veces por segundo a rellenar el búfer; con offload recibe varios
segundos de golpe y se duerme hasta el siguiente lote.

Se pide con `setIsGaplessSupportRequired(true)`: si un dispositivo no puede hacer ambas
cosas, que **rechace** el offload antes que meter un silencio entre pistas.

### El compromiso del crossfade

El DSP renderiza **un** stream, no dos. En cuanto quieres dos pistas sonando a la vez, el
trabajo vuelve al CPU. Por eso el crossfade es opcional y viene apagado, y
`PlaybackService` conmuta el offload automáticamente al encenderlo.

En absoluto es poco (decodificar ronda el 1-3 % de un núcleo), pero con la pantalla
apagada eso pasa a ser lo único despierto en el teléfono.

---

## Qué hace

- **Biblioteca** — escaneo desde MediaStore, caché en Room, rescan incremental
- **Navegación** — canciones, álbumes, artistas y listas, con pestañas deslizables
- **Detalle** de álbum, artista y lista, en una sola pantalla compartida
- **Reproducción** — sesión de medios, notificación, pantalla de bloqueo, Bluetooth
- **Gapless** siempre, **crossfade** real opcional (3/6/10 s)
- **Cola** — reordenar arrastrando, ir a la pista actual, aleatorizar lo que falta,
  quitar duplicados, guardarla como lista, tiempo restante y total
- **Listas** — crear, añadir, quitar, y portada en mosaico 2×2 o elegida
- **Búsqueda** con debounce sobre la caché
- **Lista negra de carpetas**, aplicada al consultar y no al escanear
- **Material You** — color del fondo de pantalla, modo AMOLED real, teñido con la carátula
- **Estadísticas** de reproducciones y saltos

---

## Decisiones de diseño que vale la pena conocer

**Un solo `ExoPlayer`.** `PlaybackService` es su único dueño. La UI, la notificación,
Bluetooth y la pantalla de bloqueo llegan a él por un `MediaController`, así que hay
exactamente una fuente de verdad sobre qué suena.

**El escaneo lee el índice de MediaStore, no los archivos.** Una pasada de cursor en vez
de miles de aperturas. `MusicRepository.sync()` compara `dateModified`, así que un
arranque donde nada cambió cuesta una consulta y cero escrituras.

**Las exclusiones de carpeta se aplican al consultar.** Si se filtraran al escanear, una
carpeta excluida desaparecería de su propia pantalla de ajustes y jamás podrías volver a
activarla.

**El crossfade invierte el diseño obvio.** Media3 ata una sesión a un `Player` de por
vida, así que en vez de alternar dos reproductores, el de la sesión siempre lleva la pista
*entrante* y un segundo reproductor desechable desvanece la *saliente*. La costura cae en
la pista que ya va camino al silencio.

---

## Stack

| Capa | Qué se usa |
|---|---|
| Lenguaje | Kotlin 2.4.10 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2026.08.00) |
| Reproducción | AndroidX Media3 / ExoPlayer 1.11.0 |
| Persistencia | Room 2.8.4 (KSP), DataStore Preferences |
| Imágenes | Coil 3.5.0 |
| Color | AndroidX Palette (extracción desde la carátula) |
| Inyección | `AppContainer` hecho a mano — sin framework |
| Build | Gradle 9.7.1, AGP 9.3.2, JDK 25 |

Sin Hilt, sin Retrofit, sin Navigation-compose real: el proyecto es lo bastante chico como
para que cada framework cueste más complejidad de build de la que quita.

---

## Primeros pasos

### Requisitos

- **Android Studio** (versión estable reciente) — trae su propio JDK y el SDK
- **JDK 25** — el que viene con Android Studio (`Settings → Build Tools → Gradle → Gradle JDK`)
- Un dispositivo o emulador con **Android 11 o superior** (`minSdk 30`)

> El JDK del sistema suele ser más viejo y Gradle lo rechaza. Si ves un error que es solo
> una cadena de versión (`25.0.2`) sin más explicación, eso es Gradle o AGP demasiado
> viejos para el JDK — no es un fallo de AGP.

### Compilar

```bash
git clone git@github.com:jipixz/resonance.git
cd resonance
```

Desde Android Studio: abre la carpeta, deja que sincronice, y dale a Run.

Desde la terminal (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

El APK sale en `app/build/outputs/apk/debug/app-debug.apk`.

### Instalar en un dispositivo

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La primera vez pide permiso de acceso a la música; la biblioteca se construye sola desde
MediaStore.

---

## Estructura

```
core/           AppContainer (DI a mano), SettingsStore (DataStore)
data/db/        Entidades de Room, DAO, base de datos, migraciones
data/media/     MediaStoreScanner — lo único que toca MediaStore
data/           MusicRepository — reconcilia la caché, expone flows
playback/       PlaybackService (dueño del único ExoPlayer)
                PlayerConnection (puente a la UI)
                CrossfadeEngine (el segundo reproductor, desechable)
ui/theme/       Esquema Material You, override AMOLED, escala tipográfica
ui/library/     Listas, rejilla de álbumes, detalle, búsqueda, carpetas
ui/player/      MiniPlayer, PlayerScreen, QueueSheet, extracción de color
```

---

## Trampas ya pisadas

- `MediaStore.Audio.ALBUM_ARTIST` solo existe desde **API 30**. Por eso `minSdk` es 30 y
  no 29: consultarlo por debajo revienta el cursor.
- El parser SQL de **Room**, no SQLite, es la restricción real en `@Query`. Los `UPSERT`
  crudos se cambiaron por métodos `@Transaction`.
- MediaStore empaqueta los discos múltiples dentro de `TRACK` como `disco * 1000 + pista`.
  Se desempaqueta en `MediaStoreScanner` — nunca leas `TRACK` en crudo.
- **AGP 9 trae soporte de Kotlin integrado**: aplicar `org.jetbrains.kotlin.android` es un
  error duro, y `kotlinOptions {}` se fue con él.
- **Coil 3** movió todos sus paquetes de `coil.*` a `coil3.*`.
- Girar el teléfono recreaba la Activity y repetía la animación de arranque; se resuelve
  con `configChanges` en el manifiesto, no con más estado guardado.

---

## Pendiente

- Edición de etiquetas (necesita librería de tagging + `MediaStore.createWriteRequest`)
- Ecualizador — ojo: cualquier `AudioEffect` rompe el offload
- El APK de debug pesa ~72 MB, casi todo `material-icons-extended`. R8 lo limpia en
  release, pero salir de esa dependencia sería más barato.

---

## Créditos

- Tipografía del logotipo: [Pacifico](https://fonts.google.com/specimen/Pacifico), bajo
  SIL Open Font License 1.1.
- Inspiración funcional: **BlackPlayer EX**. Inspiración visual: las apps propias de
  Google y **Solid Explorer**.
