# Build Notes & Known Risks

This project is a **broad, coherent, compiling foundation** for the YouTube
Offline Archive app, wired end-to-end across a clean layered architecture.

## Toolchain (downgraded from the template for compatibility)

The Android Studio template generated a **bleeding-edge AGP 9.2.1 / Kotlin
2.2.10** setup. The Hilt and Chaquopy Gradle plugins **do not support AGP 9**
yet (Hilt fails with *"Android BaseExtension not found"*), so the project was
moved to a proven, mutually-compatible stable stack:

| Tool          | Version    |
|---------------|------------|
| Gradle        | 8.11.1     |
| AGP           | 8.7.2      |
| Kotlin        | 2.0.21     |
| KSP           | 2.0.21-1.0.28 |
| Compose BOM   | 2024.12.01 |
| Hilt          | 2.52       |
| Room          | 2.6.1      |
| Media3        | 1.4.1      |
| Chaquopy      | 16.1.0     |
| compileSdk / targetSdk | 35 |

If you later want to return to AGP 9, wait for Hilt + Chaquopy releases that
support it, then bump the versions in
[gradle/libs.versions.toml](gradle/libs.versions.toml) and restore the AGP-9
`android { compileSdk { … } }` / `optimization { }` DSL.

## Things to check on first sync

### 1. SDK Platform 35
`compileSdk`/`targetSdk` are 35. If only SDK 36 is installed, Android Studio
will normally auto-download platform 35; if not, install it via the SDK Manager.

### 2. Chaquopy
Now aligned with AGP 8.7.2, so the `chaquopy { }` block should apply. The Python
bridge is still isolated behind interfaces
([`MediaAnalyzer`](app/src/main/java/com/adnanearrassen/ytarchiver/domain/repository/Repositories.kt),
[`YtDlpService`](app/src/main/java/com/adnanearrassen/ytarchiver/python/YtDlpService.kt)),
so you can drop in a stub to build/run the UI without Python if needed.

### 3. FFmpeg (affects download quality, not whether downloads work)
yt-dlp needs FFmpeg to **merge** adaptive video+audio streams (anything above
~720p on YouTube), to **transcode** audio to MP3/FLAC/etc., and to **embed**
thumbnails/metadata/subtitles. Chaquopy does not bundle an FFmpeg binary.

`yt_archiver.py` now **detects FFmpeg** (`_has_ffmpeg()`) and degrades
gracefully so downloads succeed without it:

- **Video, no FFmpeg** → downloads a single pre-muxed (progressive) stream, so
  no merge is needed. On YouTube this tops out around **720p**. Subtitles are
  still saved as sidecar files.
- **Music, no FFmpeg** → keeps the native audio stream (usually `.m4a`), fully
  playable, but not re-encoded to your chosen format and without embedded art.
- **With FFmpeg** → full behaviour (4K merges, MP3/FLAC conversion, embedding).

To unlock full quality, ship an FFmpeg build and point the engine at it:
call `set_ffmpeg_location(path)` (already exposed in `yt_archiver.py`) from
`PythonRuntime` with the path to a bundled arm64 `ffmpeg` binary (e.g. from an
`ffmpeg-kit` / prebuilt static build extracted to app storage and marked
executable). `_has_ffmpeg()` will then return true and quality selection kicks
back in automatically.

## What is fully implemented

- Clean, layered architecture (domain / data / di / python / download / player / ui).
- Room database (media, downloads, playlists, join table) + DAOs + converters.
- DataStore-backed settings covering every option in the spec.
- Hilt DI graph across all layers.
- WorkManager download engine: queue, concurrency cap, pause/resume/cancel/retry,
  reorder, auto-retry, foreground progress notification.
- Python `yt_archiver.py`: analyze (video + playlist), format-selector builder,
  download with progress hooks, audio extraction, subtitle/metadata/thumbnail
  postprocessors.
- yt-dlp self-update system (GitHub latest release → download zipapp → shadow the
  bundled engine via `sys.path`), with version compare + changelog + progress.
- Compose UI: Home dashboard, Download (analyze + two-tap Video/Music sheet +
  Advanced Options), Library (filters + search), Download Manager, full Settings,
  Storage manager, Download history, Engine update screen.
- Share-to-App and youtube.com/youtu.be VIEW intents.
- Media3 player screen + background `PlaybackService` (MediaSession).

## What is stubbed / basic (good next steps)

- **FFmpeg runtime** (see risk #4) — required for most postprocessing.
- **Player depth**: PiP, gesture brightness/volume, seek-preview, auto-next,
  shuffle/repeat, mini-player, equalizer, lyrics — the screen plays files and
  remembers position; the rest are TODO.
- **Thumbnails for archived files**: the worker stores `thumbnailPath = null`;
  wire `writethumbnail` output into `StorageLocator.thumbnailDir()`.
- **Playlist detail / management UI** (create/rename/merge/pin exist in the
  repository; a dedicated screen is not built yet).
- **Duplicate detection & cleanup recommendations** in Storage (broken-file
  detection is implemented; dedupe is not).
- **Runtime permission prompts** (POST_NOTIFICATIONS) — declared in the manifest;
  add an Accompanist permission request on first launch.

See `// TODO` markers and the repository interfaces for extension points.
