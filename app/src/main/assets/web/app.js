"use strict";

function fmtDur(s) {
    s = Math.max(0, Math.floor(s || 0));
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    const pad = n => String(n).padStart(2, "0");
    return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
}

function el(html) {
    const t = document.createElement("template");
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
}

// ---- Tabs ----
document.querySelectorAll(".tab").forEach(btn => {
    btn.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach(b => b.classList.remove("active"));
        document.querySelectorAll(".page").forEach(p => p.classList.remove("active"));
        btn.classList.add("active");
        const id = btn.dataset.tab;
        document.getElementById(id).classList.add("active");
        if (id === "library") loadLibrary();
        if (id === "playlists") loadPlaylists();
        if (id === "download") pollQueue();
    });
});

function cardHtml(item) {
    const thumb = item.hasThumb
        ? `<img src="/thumb/${item.id}" loading="lazy">`
        : `<div class="ph">${item.kind === "MUSIC" ? "♪" : "▶"}</div>`;
    const dur = item.durationSeconds ? `<span class="dur">${fmtDur(item.durationSeconds)}</span>` : "";
    return `<div class="card">
        <div class="thumb">${thumb}${dur}</div>
        <div class="meta"><div class="t">${escapeHtml(item.title)}</div>
        <div class="s">${escapeHtml(item.uploader || "")}</div></div></div>`;
}

function escapeHtml(s) {
    return (s || "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---- Library ----
async function loadLibrary() {
    const grid = document.getElementById("library-grid");
    const empty = document.getElementById("library-empty");
    const items = await fetch("/api/library").then(r => r.json()).catch(() => []);
    grid.innerHTML = "";
    empty.hidden = items.length > 0;
    items.forEach(item => {
        const c = el(cardHtml(item));
        c.addEventListener("click", () => playQueue(items, items.indexOf(item)));
        grid.appendChild(c);
    });
}

// ---- Playlists ----
let playlistsCache = [];

function playlistCardHtml(pl) {
    const thumb = pl.coverHasThumb
        ? `<img src="/thumb/${pl.coverId}" loading="lazy">`
        : `<div class="ph">☰</div>`;
    return `<div class="card">
        <div class="thumb">${thumb}<span class="badge">☰ ${pl.itemCount}</span></div>
        <div class="meta"><div class="t">${escapeHtml(pl.name)}</div>
        <div class="s">Playlist · ${pl.itemCount} videos</div></div></div>`;
}

async function loadPlaylists() {
    const wrap = document.getElementById("playlists-list");
    const empty = document.getElementById("playlists-empty");
    playlistsCache = await fetch("/api/playlists").then(r => r.json()).catch(() => []);
    wrap.innerHTML = "";
    empty.hidden = playlistsCache.length > 0;
    const grid = el(`<div class="grid"></div>`);
    playlistsCache.forEach(pl => {
        const c = el(playlistCardHtml(pl));
        c.addEventListener("click", () => openPlaylistDetail(pl));
        grid.appendChild(c);
    });
    wrap.appendChild(grid);
}

function openPlaylistDetail(pl) {
    const wrap = document.getElementById("playlists-list");
    document.getElementById("playlists-empty").hidden = true;
    const cover = pl.coverHasThumb ? `<img src="/thumb/${pl.coverId}">` : `<div class="ph">☰</div>`;
    const view = el(`<div class="pl-detail">
        <button class="back">← Back to playlists</button>
        <div class="pl-cover">${cover}</div>
        <h2>${escapeHtml(pl.name)}</h2>
        <div class="s">${pl.itemCount} videos · ${fmtDur(pl.durationSeconds)}</div>
        <button class="btn primary play-all">▶ Play all</button>
        <div class="pl-items"></div></div>`);
    view.querySelector(".back").addEventListener("click", loadPlaylists);
    view.querySelector(".play-all").addEventListener("click", () => playQueue(pl.items, 0));
    const list = view.querySelector(".pl-items");
    pl.items.forEach((item, i) => {
        const thumb = item.hasThumb
            ? `<img src="/thumb/${item.id}" loading="lazy">`
            : `<div class="ph">${item.kind === "MUSIC" ? "♪" : "▶"}</div>`;
        const row = el(`<div class="pl-item">
            <div class="idx">${i + 1}</div>
            <div class="rowthumb">${thumb}</div>
            <div class="rowmeta"><div class="t">${escapeHtml(item.title)}</div>
            <div class="s">${escapeHtml(item.uploader || "")}</div></div>
            <div class="idx">${fmtDur(item.durationSeconds)}</div></div>`);
        row.addEventListener("click", () => playQueue(pl.items, i));
        list.appendChild(row);
    });
    wrap.innerHTML = "";
    wrap.appendChild(view);
}

// ---- Player + queue ----
let queue = [], queueIndex = 0;

function playQueue(items, index) {
    queue = items;
    queueIndex = index;
    playCurrent();
    document.getElementById("overlay").classList.remove("hidden");
}

function playCurrent() {
    const item = queue[queueIndex];
    if (!item) return;
    const isAudio = item.kind === "MUSIC";
    const mediaBox = document.getElementById("player-media");
    const src = `/media/${item.id}`;
    if (isAudio) {
        const art = item.hasThumb ? `<img src="/thumb/${item.id}" style="width:100%;border-radius:12px">` : `<div class="art">♪</div>`;
        mediaBox.innerHTML = `${art}<audio id="media-el" controls autoplay src="${src}"></audio>`;
    } else {
        mediaBox.innerHTML = `<video id="media-el" controls autoplay playsinline src="${src}"></video>`;
    }
    document.getElementById("player-title").textContent = item.title;
    const mediaEl = document.getElementById("media-el");
    mediaEl.addEventListener("ended", playNext);
    renderUpNext();
}

function playNext() {
    if (queueIndex < queue.length - 1) { queueIndex++; playCurrent(); }
}

function renderUpNext() {
    const box = document.getElementById("player-up-next");
    box.innerHTML = queue.length > 1 ? "<h3>Up next</h3>" : "";
    queue.forEach((item, i) => {
        const row = el(`<div class="un ${i === queueIndex ? "playing" : ""}">
            <div class="idx">${i + 1}</div><div class="t">${escapeHtml(item.title)}</div>
            <div class="idx">${fmtDur(item.durationSeconds)}</div></div>`);
        row.addEventListener("click", () => { queueIndex = i; playCurrent(); });
        box.appendChild(row);
    });
}

function closePlayer() {
    const m = document.getElementById("media-el");
    if (m) { m.pause(); }
    document.getElementById("player-media").innerHTML = "";
    document.getElementById("overlay").classList.add("hidden");
}

// ---- Download control ----
async function submitDownload(type) {
    const url = document.getElementById("dl-url").value.trim();
    const status = document.getElementById("dl-status");
    if (!url) { status.textContent = "Paste a URL first."; return; }
    status.textContent = "Analyzing and queuing…";
    try {
        const body = new URLSearchParams({ url, type });
        const res = await fetch("/api/download", { method: "POST", body }).then(r => r.json());
        status.textContent = res.ok ? "Queued! Check the queue below." : ("Error: " + (res.error || "failed"));
        document.getElementById("dl-url").value = "";
        setTimeout(pollQueue, 1500);
    } catch (e) {
        status.textContent = "Request failed.";
    }
}

async function pollQueue() {
    const list = document.getElementById("queue-list");
    const items = await fetch("/api/queue").then(r => r.json()).catch(() => []);
    list.innerHTML = items.length ? "" : "<div class='qs'>Queue is empty.</div>";
    items.forEach(d => {
        list.appendChild(el(`<div class="q">
            <div class="qt">${escapeHtml(d.title)}</div>
            <div class="qs">${d.type} · ${d.status} · ${d.percent}%</div>
            <div class="bar"><div style="width:${d.percent}%"></div></div></div>`));
    });
}

// Refresh the queue periodically while on the Download tab.
setInterval(() => {
    if (document.getElementById("download").classList.contains("active")) pollQueue();
}, 3000);

// Initial load.
loadLibrary();
