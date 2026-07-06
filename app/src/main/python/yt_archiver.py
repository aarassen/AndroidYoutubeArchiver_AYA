"""
yt_archiver — the Python side of the download engine.

Kotlin talks to this module through Chaquopy. Every public function takes and
returns JSON strings so the bridge stays language-agnostic and easy to evolve.

Public API:
    configure(engine_override_dir)   -> None   # allow a self-updated yt-dlp to win
    analyze(url)                     -> json    # extract metadata + formats
    build_format(options_json)       -> str     # (exposed for debugging/tests)
    download(url, options_json, out_dir, callback) -> json
"""

import json
import os
import shutil
import sys
import traceback

# Optional path to a bundled ffmpeg binary (set via configure()). yt-dlp needs
# ffmpeg to MERGE separate video+audio streams and to EXTRACT/convert audio.
# Chaquopy does not ship ffmpeg, so unless one is provided we fall back to
# stream selections that don't require it.
_FFMPEG_LOCATION = None


def _has_ffmpeg():
    if _FFMPEG_LOCATION and os.path.exists(_FFMPEG_LOCATION):
        return True
    return shutil.which("ffmpeg") is not None


def set_ffmpeg_location(path):
    """Kotlin can point us at a bundled ffmpeg binary/dir once one is shipped."""
    global _FFMPEG_LOCATION
    _FFMPEG_LOCATION = path


# ---------------------------------------------------------------------------
# Engine override: a newer yt-dlp downloaded at runtime (see EngineUpdater).
# If present, prepend it to sys.path so `import yt_dlp` picks up the new code
# instead of the version bundled at build time by Chaquopy/pip.
# ---------------------------------------------------------------------------
def configure(engine_override_dir):
    try:
        if engine_override_dir and os.path.isdir(engine_override_dir):
            zip_path = os.path.join(engine_override_dir, "yt-dlp")
            zipapp = zip_path if os.path.exists(zip_path) else os.path.join(
                engine_override_dir, "yt-dlp.zip"
            )
            if os.path.exists(zipapp) and zipapp not in sys.path:
                sys.path.insert(0, zipapp)
    except Exception:
        # Never let an override break the bundled engine.
        traceback.print_exc()


def engine_version():
    try:
        import yt_dlp
        return yt_dlp.version.__version__
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Analysis
# ---------------------------------------------------------------------------
def analyze(url):
    try:
        import yt_dlp

        opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "noplaylist": False,
            # Flatten playlists so we don't resolve every entry (fast).
            "extract_flat": "in_playlist",
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

        if info.get("_type") == "playlist" or "entries" in info:
            return json.dumps(_playlist_to_dict(info, url))
        return json.dumps(_media_to_dict(info, url))
    except Exception as e:
        return json.dumps({"error": _describe_error(e)})


def _playlist_to_dict(info, url):
    entries = []
    for e in (info.get("entries") or []):
        if not e:
            continue
        entries.append({
            "id": e.get("id") or "",
            "url": e.get("url") or e.get("webpage_url") or "",
            "title": e.get("title") or "Untitled",
            "durationSeconds": _as_long(e.get("duration")),
            "thumbnailUrl": _best_thumb(e),
        })
    return {
        "id": info.get("id") or "",
        "url": url,
        "title": info.get("title") or "Playlist",
        "uploader": info.get("uploader") or info.get("channel"),
        "channelUrl": info.get("channel_url"),
        "durationSeconds": None,
        "thumbnailUrl": entries[0]["thumbnailUrl"] if entries else None,
        "description": info.get("description"),
        "viewCount": _as_long(info.get("view_count")),
        "uploadDate": info.get("upload_date"),
        "isLive": False,
        "kind": "PLAYLIST",
        "videoFormats": [],
        "audioFormats": [],
        "subtitles": [],
        "playlist": {
            "id": info.get("id") or "",
            "title": info.get("title") or "Playlist",
            "uploader": info.get("uploader") or info.get("channel"),
            "itemCount": len(entries),
            "entries": entries,
        },
    }


def _media_to_dict(info, url):
    video_formats, audio_formats = _split_formats(info.get("formats") or [])
    return {
        "id": info.get("id") or "",
        "url": info.get("webpage_url") or url,
        "title": info.get("title") or "Untitled",
        "uploader": info.get("uploader") or info.get("channel"),
        "channelUrl": info.get("channel_url"),
        "durationSeconds": _as_long(info.get("duration")),
        "thumbnailUrl": _best_thumb(info),
        "description": info.get("description"),
        "viewCount": _as_long(info.get("view_count")),
        "uploadDate": info.get("upload_date"),
        "isLive": bool(info.get("is_live")),
        "kind": _classify(info),
        "videoFormats": video_formats,
        "audioFormats": audio_formats,
        "subtitles": _subtitles(info),
        "playlist": None,
    }


def _split_formats(formats):
    videos, audios = [], []
    for f in formats:
        vcodec = f.get("vcodec")
        acodec = f.get("acodec")
        has_video = vcodec and vcodec != "none"
        has_audio = acodec and acodec != "none"
        if has_video:
            videos.append({
                "formatId": str(f.get("format_id") or ""),
                "ext": f.get("ext") or "",
                "height": f.get("height"),
                "width": f.get("width"),
                "fps": int(f["fps"]) if f.get("fps") else None,
                "vcodec": vcodec,
                "acodec": acodec if has_audio else None,
                "fileSizeBytes": _as_long(f.get("filesize") or f.get("filesize_approx")),
                "tbrKbps": f.get("tbr"),
                "isHdr": "hdr" in (f.get("dynamic_range") or "").lower(),
            })
        elif has_audio:
            audios.append({
                "formatId": str(f.get("format_id") or ""),
                "ext": f.get("ext") or "",
                "acodec": acodec,
                "abrKbps": f.get("abr"),
                "asrHz": f.get("asr"),
                "fileSizeBytes": _as_long(f.get("filesize") or f.get("filesize_approx")),
            })
    # De-dupe video formats by height, keeping the highest bitrate.
    return videos, audios


def _subtitles(info):
    out = []
    for lang, tracks in (info.get("subtitles") or {}).items():
        out.append({"languageCode": lang, "languageName": lang, "isAutoGenerated": False})
    for lang, tracks in (info.get("automatic_captions") or {}).items():
        out.append({"languageCode": lang, "languageName": lang, "isAutoGenerated": True})
    return out


# ---------------------------------------------------------------------------
# Download
# ---------------------------------------------------------------------------
def download(url, options_json, out_dir, callback):
    """Download `url` using the merged options. `callback` is a Java object with
    an `onProgress(json)` method (a Kotlin ProgressCallback)."""
    try:
        import yt_dlp
        opts = json.loads(options_json)
        os.makedirs(out_dir, exist_ok=True)

        outtmpl = os.path.join(out_dir, (opts.get("outputFileName") or "%(title)s") + ".%(ext)s")

        def hook(d):
            try:
                status = d.get("status")
                if status == "downloading":
                    total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                    downloaded = d.get("downloaded_bytes") or 0
                    payload = {
                        "status": "DOWNLOADING",
                        "progress": (downloaded / total) if total else 0.0,
                        "speed": int(d.get("speed") or 0),
                        "downloaded": int(downloaded),
                        "total": int(total),
                        "eta": int(d.get("eta") or 0),
                        "filename": d.get("filename"),
                    }
                elif status == "finished":
                    payload = {"status": "PROCESSING", "progress": 1.0, "speed": 0,
                               "downloaded": 0, "total": 0, "eta": 0,
                               "filename": d.get("filename")}
                else:
                    return
                callback.onProgress(json.dumps(payload))
            except Exception:
                traceback.print_exc()

        ydl_opts = _build_ydl_opts(opts, outtmpl)
        ydl_opts["progress_hooks"] = [hook]

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            final_path = ydl.prepare_filename(info)
            # Postprocessors may change the extension (e.g. mp3 extraction).
            final_path = _resolve_final_path(final_path, opts)

        size = os.path.getsize(final_path) if os.path.exists(final_path) else 0
        return json.dumps({
            "filePath": final_path,
            "fileSizeBytes": size,
            "title": info.get("title"),
            "uploader": info.get("uploader") or info.get("channel"),
            "durationSeconds": _as_long(info.get("duration")) or 0,
            "id": info.get("id"),
        })
    except Exception as e:
        return json.dumps({"error": _describe_error(e)})


def _build_ydl_opts(opts, outtmpl):
    dtype = opts.get("type", "VIDEO")
    has_ffmpeg = _has_ffmpeg()
    common = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "outtmpl": outtmpl,
        "postprocessors": [],
        "retries": 5,
        "fragment_retries": 5,
        "continuedl": True,   # resume partial downloads
    }
    if _FFMPEG_LOCATION:
        common["ffmpeg_location"] = _FFMPEG_LOCATION

    if dtype == "MUSIC":
        common["format"] = "bestaudio/best"
        if has_ffmpeg:
            # Transcode/convert to the requested format and embed art/metadata.
            audio_format = (opts.get("format") or "MP3").lower()
            pp = {"key": "FFmpegExtractAudio", "preferredcodec": audio_format}
            bitrate = opts.get("bitrateKbps")
            if bitrate:
                pp["preferredquality"] = str(bitrate)
            common["postprocessors"].append(pp)
            if opts.get("embedThumbnail"):
                common["writethumbnail"] = True
                common["postprocessors"].append({"key": "EmbedThumbnail"})
            if opts.get("embedMetadata"):
                common["postprocessors"].append({"key": "FFmpegMetadata"})
        # else: no ffmpeg -> keep the native audio stream (e.g. .m4a/.webm)
        #       exactly as downloaded; still fully playable.
    else:
        if has_ffmpeg:
            # Best quality: allow adaptive video+audio streams that need merging.
            common["format"] = build_format(json.dumps(opts))
            common["merge_output_format"] = (opts.get("container") or "mp4").lower()
            if opts.get("embedMetadata"):
                common["postprocessors"].append({"key": "FFmpegMetadata"})
            if opts.get("embedThumbnail"):
                common["writethumbnail"] = True
                common["postprocessors"].append({"key": "EmbedThumbnail"})
            if opts.get("downloadSubtitles"):
                common["writesubtitles"] = True
                common["writeautomaticsub"] = bool(opts.get("autoSubtitles"))
                common["subtitleslangs"] = opts.get("subtitleLanguages") or ["en"]
                if opts.get("embedSubtitles"):
                    common["postprocessors"].append({"key": "FFmpegEmbedSubtitle"})
        else:
            # No ffmpeg -> pick a single pre-muxed (progressive) stream so no
            # merge is required. On YouTube this tops out around 720p, but it
            # works out of the box. Respect the requested max height if given.
            height = opts.get("maxHeight")
            if height:
                common["format"] = (
                    "best[ext=mp4][height<=?%d]/best[height<=?%d]/best" % (height, height)
                )
            else:
                common["format"] = "best[ext=mp4]/best"
            # Subtitles can still be written as separate .srt/.vtt sidecar files.
            if opts.get("downloadSubtitles"):
                common["writesubtitles"] = True
                common["writeautomaticsub"] = bool(opts.get("autoSubtitles"))
                common["subtitleslangs"] = opts.get("subtitleLanguages") or ["en"]
    return common


def build_format(options_json):
    """Translate video options into a yt-dlp -f selector string."""
    opts = json.loads(options_json)
    height = opts.get("maxHeight")
    fps = opts.get("fps")
    vcodec = opts.get("vcodecPrefix")   # e.g. "avc", "vp9", "av01"
    acodec = opts.get("acodecPrefix")   # e.g. "mp4a", "opus"

    v = "bestvideo"
    filters = []
    if height:
        filters.append(f"height<=?{height}")
    if fps:
        filters.append(f"fps<=?{fps}")
    if vcodec:
        filters.append(f"vcodec^=?{vcodec}")
    if filters:
        v += "[" + "][".join(filters) + "]"

    a = "bestaudio"
    if acodec:
        a += f"[acodec^=?{acodec}]"

    # Fall back to a progressive best if adaptive merge is unavailable.
    smart = opts.get("smartFallback", True)
    if smart:
        return f"{v}+{a}/best[height<=?{height}]/best" if height else f"{v}+{a}/best"
    return f"{v}+{a}"


def _resolve_final_path(path, opts):
    if opts.get("type") == "MUSIC":
        ext = (opts.get("format") or "mp3").lower()
        base, _ = os.path.splitext(path)
        candidate = base + "." + ext
        if os.path.exists(candidate):
            return candidate
    return path


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _best_thumb(info):
    if info.get("thumbnail"):
        return info["thumbnail"]
    thumbs = info.get("thumbnails") or []
    if thumbs:
        return thumbs[-1].get("url")
    return None


def _classify(info):
    if info.get("is_live"):
        return "LIVE"
    duration = info.get("duration") or 0
    if 0 < duration <= 60 and (info.get("webpage_url") or "").find("/shorts/") != -1:
        return "SHORT"
    return "VIDEO"


def _as_long(value):
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _describe_error(e):
    msg = str(e)
    low = msg.lower()
    if "private" in low:
        return "This video is private."
    if "copyright" in low or "removed" in low:
        return "This video is unavailable (removed or copyright)."
    if "not available in your country" in low or "geo" in low:
        return "This video is region-restricted."
    if "sign in" in low or "age" in low:
        return "This video requires sign-in / age verification."
    return msg or "Unknown error"
