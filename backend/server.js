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

// youtubei.js (Innertube) for streaming URLs
let innertube = null;

async function ensureYTMusicInitialized() {
  if (!ytmusicInitialized) {
    await ytmusic.initialize();
    ytmusicInitialized = true;
  }
}

async function ensureInnertube() {
  if (!innertube) {
    const { Innertube } = await import("youtubei.js");
    innertube = await Innertube.create({
      // Use ANDROID client — most reliable for getting stream URLs without sign-in
      client_type: "ANDROID",
    });
  }
  return innertube;
}

// Pre-warm on startup
ensureYTMusicInitialized().catch(console.error);
ensureInnertube().catch(console.error);

app.get("/api/health", (_req, res) => {
  res.json({ status: "ok", innertube: !!innertube, ytmusic: ytmusicInitialized });
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

app.get("/api/song/:id/stream", async (req, res) => {
  try {
    const id = req.params.id;

    // Use @distube/ytdl-core to proxy the audio stream directly.
    // Returning a raw YouTube URL causes 403 because YouTube signs URLs
    // to the server IP — playing them from a different IP (the device) fails.
    // Proxying through the backend avoids this entirely.
    const ytdl = require("@distube/ytdl-core");

    const info = await ytdl.getInfo(`https://www.youtube.com/watch?v=${id}`);
    const format = ytdl.chooseFormat(info.formats, {
      quality: "highestaudio",
      filter: "audioonly",
    });

    if (!format) {
      return res.status(404).json({ error: "No playable audio format found." });
    }

    // Return the proxied stream URL pointing back to this server
    const proxyUrl = `${req.protocol}://${req.get("host")}/api/song/${id}/proxy`;
    return res.json({
      url: proxyUrl,
      mimeType: format.mimeType || "audio/webm",
      bitrate: format.audioBitrate || 0,
      expiresAt: Date.now() + 6 * 60 * 60 * 1000,
    });
  } catch (error) {
    console.error("/api/song/:id/stream error:", error.message);
    return res.status(500).json({ error: error.message });
  }
});

// Proxy endpoint — streams audio bytes from YouTube through this server.
// ExoPlayer hits this URL; the server fetches from YouTube and pipes it back.
app.get("/api/song/:id/proxy", async (req, res) => {
  try {
    const id = req.params.id;
    const ytdl = require("@distube/ytdl-core");

    const stream = ytdl(`https://www.youtube.com/watch?v=${id}`, {
      quality: "highestaudio",
      filter: "audioonly",
    });

    stream.on("error", (err) => {
      console.error("/api/song/:id/proxy stream error:", err.message);
      if (!res.headersSent) res.status(500).end();
    });

    res.setHeader("Content-Type", "audio/webm");
    stream.pipe(res);
  } catch (error) {
    console.error("/api/song/:id/proxy error:", error.message);
    if (!res.headersSent) res.status(500).json({ error: error.message });
  }
});

app.get("/api/playlists", (_req, res) => {
  return res.json([]);
});

app.get("/api/playlist/:id", (req, res) => {
  return res.status(501).json({
    error: `Playlist endpoint not implemented for ${req.params.id}.`,
  });
});

app.listen(port, () => {
  console.log(`Shuffler YouTube backend listening on http://localhost:${port}`);
});
