const express = require("express");
const cors = require("cors");
const dotenv = require("dotenv");
const YTMusicImport = require("ytmusic-api");
const YTMusic = YTMusicImport?.default || YTMusicImport;

dotenv.config();

const app = express();
const port = Number(process.env.PORT || 3000);

app.use(cors());
app.use(express.json());

// ytmusic-api for search
const ytmusic = new YTMusic();
let ytmusicInitialized = false;

async function ensureYTMusicInitialized() {
  if (!ytmusicInitialized) {
    await ytmusic.initialize();
    ytmusicInitialized = true;
  }
}

// Pre-warm on startup
ensureYTMusicInitialized().catch(console.error);

app.get("/api/health", (_req, res) => {
  res.json({ status: "ok", ytmusic: ytmusicInitialized });
});

app.get("/api/search", async (req, res) => {
  try {
    await ensureYTMusicInitialized();
    const query = (req.query.q || "").toString().trim();
    if (!query) {
      return res.status(400).json({ error: "query parameter q is required" });
    }

    const [songs, playlists, artists] = await Promise.all([
      ytmusic.searchSongs(query),
      ytmusic.searchPlaylists(query),
      ytmusic.searchArtists(query),
    ]);

    return res.json({
      songs: songs.map((song) => ({
        id: song.videoId,
        title: song.name || song.title || "",
        artist: song.artist?.name || "",
        durationMs: Number(song.duration || 0) * 1000,
        artworkUrl: song.thumbnails?.[0]?.url || null,
        album: song.album?.name || null,
      })),
      playlists: playlists.map((playlist) => ({
        id: playlist.playlistId,
        title: playlist.name || "",
        artworkUrl: playlist.thumbnails?.[0]?.url || null,
        songs: [],
      })),
      artists: artists.map((artist) => ({
        id: artist.artistId,
        name: artist.name || "",
        artworkUrl: artist.thumbnails?.[0]?.url || null,
      })),
    });
  } catch (error) {
    console.error("/api/search error:", error.message);
    return res.status(500).json({ error: error.message });
  }
});

// ─── Stream endpoint ────────────────────────────────────────────────────────
// Returns a proxy URL pointing back to this server's /api/song/:id/proxy.
// This avoids YouTube 403 errors: YouTube signs stream URLs to the requesting
// IP. If we returned the raw URL, the Android device (different IP) would get
// a 403. Proxying through this server solves it.
//
// IMPORTANT for physical devices:
//   Set BACKEND_HOST in .env to your PC's local WiFi IP, e.g.:
//     BACKEND_HOST=192.168.1.5
//   Then update Urls.java: YOUTUBE_BACKEND_BASE_URL = "http://192.168.1.5:3000/"
// ────────────────────────────────────────────────────────────────────────────
app.get("/api/song/:id/stream", async (req, res) => {
  try {
    const id = req.params.id;
    const ytdl = require("@distube/ytdl-core");

    const info = await ytdl.getInfo(`https://www.youtube.com/watch?v=${id}`);
    const format = ytdl.chooseFormat(info.formats, {
      quality: "highestaudio",
      filter: "audioonly",
    });

    if (!format) {
      return res.status(404).json({ error: "No playable audio format found." });
    }

    // Build the proxy URL using BACKEND_HOST env var (for physical devices)
    // or fall back to the request's Host header (works for emulator).
    const host = process.env.BACKEND_HOST
      ? `${process.env.BACKEND_HOST}:${port}`
      : req.get("host");
    const scheme = process.env.BACKEND_HOST ? "http" : req.protocol;
    const proxyUrl = `${scheme}://${host}/api/song/${id}/proxy`;

    console.log(`[stream] ${id} -> proxy ${proxyUrl} (format: ${format.mimeType})`);

    return res.json({
      url: proxyUrl,
      mimeType: format.mimeType || "audio/webm",
      bitrate: format.audioBitrate || 0,
      // 6-hour TTL; ExoPlayer will just reconnect if it expires
      expiresAt: Date.now() + 6 * 60 * 60 * 1000,
    });
  } catch (error) {
    console.error("/api/song/:id/stream error:", error.message);
    return res.status(500).json({ error: error.message });
  }
});

// ─── Proxy endpoint ─────────────────────────────────────────────────────────
// Streams audio bytes from YouTube through this server to the Android device.
// Supports Range requests so ExoPlayer can seek efficiently.
// ────────────────────────────────────────────────────────────────────────────
app.get("/api/song/:id/proxy", async (req, res) => {
  try {
    const id = req.params.id;
    const ytdl = require("@distube/ytdl-core");

    const info = await ytdl.getInfo(`https://www.youtube.com/watch?v=${id}`);
    const format = ytdl.chooseFormat(info.formats, {
      quality: "highestaudio",
      filter: "audioonly",
    });

    if (!format) {
      return res.status(404).end();
    }

    const mimeType = format.mimeType
      ? format.mimeType.split(";")[0]  // strip codec params for Content-Type header
      : "audio/webm";

    // Handle Range header from ExoPlayer (needed for seeking)
    const rangeHeader = req.headers["range"];
    const ytdlOptions = {
      quality: "highestaudio",
      filter: "audioonly",
    };
    if (rangeHeader) {
      ytdlOptions.range = parseRange(rangeHeader);
    }

    res.setHeader("Content-Type", mimeType);
    res.setHeader("Accept-Ranges", "bytes");
    // Allow ExoPlayer to cache the stream
    res.setHeader("Cache-Control", "no-transform");

    const stream = ytdl.downloadFromInfo(info, ytdlOptions);

    stream.on("error", (err) => {
      console.error(`/api/song/${id}/proxy stream error:`, err.message);
      if (!res.headersSent) res.status(500).end();
    });

    if (rangeHeader) {
      res.status(206);
    }
    stream.pipe(res);
  } catch (error) {
    console.error("/api/song/:id/proxy error:", error.message);
    if (!res.headersSent) res.status(500).json({ error: error.message });
  }
});

// Parse a Range header like "bytes=0-1023" into { start, end }
function parseRange(rangeHeader) {
  const match = rangeHeader && rangeHeader.match(/bytes=(\d*)-(\d*)/);
  if (!match) return undefined;
  const start = match[1] ? parseInt(match[1], 10) : undefined;
  const end = match[2] ? parseInt(match[2], 10) : undefined;
  if (start === undefined && end === undefined) return undefined;
  return { start, end };
}

app.get("/api/playlists", (_req, res) => {
  return res.json([]);
});

app.get("/api/playlist/:id", (req, res) => {
  return res.status(501).json({
    error: `Playlist endpoint not implemented for ${req.params.id}.`,
  });
});

app.listen(port, "0.0.0.0", () => {
  console.log(`Shuffler backend listening on http://0.0.0.0:${port}`);
  console.log(
    process.env.BACKEND_HOST
      ? `  Physical device URL: http://${process.env.BACKEND_HOST}:${port}/`
      : `  Set BACKEND_HOST=<your-PC-WiFi-IP> in backend/.env for physical device support`
  );
});
