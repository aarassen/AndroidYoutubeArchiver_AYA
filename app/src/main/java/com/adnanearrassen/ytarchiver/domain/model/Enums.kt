package com.adnanearrassen.ytarchiver.domain.model

/**
 * Central catalogue of the download/quality option enums. Each carries the
 * metadata the Python (yt-dlp) layer needs to build a format selector, plus a
 * human label for the UI. Keeping these in one file makes the relationship
 * between UI choices and yt-dlp behaviour easy to audit.
 */

/** What the user ultimately wants out of a link. */
enum class DownloadType(val label: String) {
    VIDEO("Video"),
    MUSIC("Music"),
}

/** Lifecycle of a single download item. */
enum class DownloadStatus {
    QUEUED,
    ANALYZING,
    DOWNLOADING,
    PAUSED,
    PROCESSING,   // remux / transcode / embed metadata via ffmpeg
    COMPLETED,
    FAILED,
    CANCELED;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELED
    val isActive: Boolean get() = this == DOWNLOADING || this == ANALYZING || this == PROCESSING
}

/** Classifies a piece of archived media for library grouping. */
enum class MediaKind {
    VIDEO,
    MUSIC,
    SHORT,
    LIVE,
    PLAYLIST,
}

// ---------------------------------------------------------------------------
// Video option enums
// ---------------------------------------------------------------------------

enum class Resolution(val label: String, val height: Int) {
    BEST("Best available", Int.MAX_VALUE),
    P2160("2160p (4K)", 2160),
    P1440("1440p (2K)", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    P240("240p", 240),
    P144("144p", 144),
}

enum class FrameRate(val label: String, val fps: Int?) {
    ANY("Any", null),
    FPS30("30 FPS", 30),
    FPS60("60 FPS", 60),
}

/** yt-dlp uses codec prefixes in the -f selector (e.g. vcodec^=avc1). */
enum class VideoCodec(val label: String, val ytdlpPrefix: String?) {
    ANY("Any", null),
    H264("H.264 (AVC)", "avc"),
    VP9("VP9", "vp9"),
    AV1("AV1", "av01"),
}

enum class Container(val label: String, val ext: String) {
    MP4("MP4", "mp4"),
    MKV("MKV", "mkv"),
    WEBM("WEBM", "webm"),
}

// ---------------------------------------------------------------------------
// Audio option enums
// ---------------------------------------------------------------------------

/** Final audio container/codec for music extraction (passed to ffmpeg postproc). */
enum class AudioFormat(val label: String, val ext: String) {
    MP3("MP3", "mp3"),
    M4A("M4A", "m4a"),
    AAC("AAC", "aac"),
    OPUS("Opus", "opus"),
    FLAC("FLAC (lossless)", "flac"),
}

enum class AudioBitrate(val label: String, val kbps: Int?) {
    BEST("Best available", null),
    K320("320 kbps", 320),
    K256("256 kbps", 256),
    K192("192 kbps", 192),
    K128("128 kbps", 128),
}

enum class SampleRate(val label: String, val hz: Int?) {
    SOURCE("Source", null),
    HZ48000("48 kHz", 48000),
    HZ44100("44.1 kHz", 44100),
}

/** For embedding audio track codec inside a video container. */
enum class VideoAudioCodec(val label: String, val ytdlpPrefix: String?) {
    ANY("Any", null),
    AAC("AAC", "mp4a"),
    OPUS("Opus", "opus"),
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemeColor { RED, BLUE, PURPLE, GREEN, ORANGE, PINK }
