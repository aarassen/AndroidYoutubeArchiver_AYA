# YT Archiver

An offline YouTube archive app for Android: download videos, playlists, Shorts,
live VODs and YouTube Music for offline viewing/listening, browse them in a
polished YouTube-like library, and keep the download engine (yt-dlp) updated
without reinstalling the app.

> **First build?** Read [BUILD_NOTES.md](BUILD_NOTES.md) — the toolchain is
> bleeding-edge and a couple of things (Chaquopy/AGP compatibility, FFmpeg)
> need attention before everything runs on device.

## Tech stack

| Concern            | Choice                                             |
|--------------------|----------------------------------------------------|
| Language / UI      | Kotlin, Jetpack Compose, Material 3                |
| Architecture       | MVVM + Clean Architecture + Repository pattern     |
| DI                 | Hilt                                               |
| Persistence        | Room + DataStore Preferences                       |
| Background work    | WorkManager + Coroutines                           |
| Download engine    | Embedded Python (Chaquopy) running **yt-dlp**      |
| Media playback     | Media3 / ExoPlayer + MediaSession                  |
| Networking         | Retrofit + OkHttp (GitHub API for engine updates)  |
| Images             | Coil                                               |

## Module / package layout

Single Gradle module (`:app`) with layered packages — chosen over 13 Gradle
modules for build reliability on this toolchain; packages can be promoted to
modules later.

```
com.adnanearrassen.ytarchiver
├── core/common        formatters, URL utils, dispatchers, notifications
├── domain             models, enums, repository interfaces, use cases
├── data
│   ├── local          Room entities, DAOs, database, converters
│   ├── datastore      settings persistence
│   ├── repository     repository implementations
│   └── mapper         entity ⇆ domain
├── di                 Hilt modules
├── network            GitHub releases API
├── python             Chaquopy runtime, YtDlpService, analyzer, updater
├── download           WorkManager worker + scheduler + notifier
├── storage            file location resolver
├── player             Media3 playback service
└── ui                 theme, navigation, screens + ViewModels + components
        home · download · library · manager · settings · storage · history
        · update · player · components
app/src/main/python    yt_archiver.py  (the yt-dlp bridge)
```

## Key flows

- **Download**: paste/share a URL → *Analyze* (yt-dlp `extract_info`) → two-tap
  **Download Video** / **Download Music** sheet (uses saved defaults) with an
  **Advanced Options** expander to override per-download.
- **Queue**: WorkManager runs downloads honouring the max-simultaneous setting,
  Wi-Fi-only / battery constraints, auto-retry and resume; the Download Manager
  shows live progress, speed, ETA and pause/resume/cancel/retry/reorder.
- **Engine updates**: on launch (if enabled) and on demand, the app checks the
  latest yt-dlp GitHub release, downloads the zipapp and shadows the bundled
  engine — no app reinstall needed.
- **Library & playback**: archived media is stored in Room and played offline
  via ExoPlayer with resume-position support.

## Configuration

Everything in the spec's Settings is persisted via DataStore and consumed when
building download options — video/music defaults, download behaviour, network &
power, appearance (theme mode, accent, Material You) and update preferences.
