// Run this from the backend folder: node test_backend.js
const http = require("http");

function get(path) {
  return new Promise((resolve, reject) => {
    const req = http.get(`http://localhost:3000${path}`, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });
    req.on("error", reject);
    req.setTimeout(15000, () => { req.destroy(); reject(new Error("TIMEOUT")); });
  });
}

async function run() {
  console.log("=".repeat(60));
  console.log("SHUFFLER BACKEND TEST");
  console.log("=".repeat(60));

  // 1. Health check
  console.log("\n[1] Health check...");
  try {
    const r = await get("/api/health");
    console.log("  ✅ OK:", JSON.stringify(r.body));
  } catch (e) {
    console.log("  ❌ FAILED:", e.message);
    console.log("  → Is the backend running? Run: npm start");
    process.exit(1);
  }

  // 2. Search songs
  console.log("\n[2] Search songs for 'shape of you'...");
  try {
    const r = await get("/api/search?q=shape+of+you");
    if (r.status !== 200) {
      console.log("  ❌ HTTP", r.status, JSON.stringify(r.body));
    } else {
      const songs = r.body.songs || [];
      console.log(`  ✅ Got ${songs.length} songs, ${r.body.artists?.length} artists, ${r.body.playlists?.length} playlists`);
      if (songs.length > 0) {
        const s = songs[0];
        console.log("  First song:", JSON.stringify({ id: s.id, title: s.title, artist: s.artist, durationMs: s.durationMs }));

        // 3. Stream URL for first song
        if (s.id) {
          console.log(`\n[3] Fetching stream URL for song id: ${s.id}...`);
          try {
            const sr = await get(`/api/song/${s.id}/stream`);
            if (sr.status !== 200) {
              console.log("  ❌ HTTP", sr.status, JSON.stringify(sr.body));
            } else {
              const url = sr.body.url;
              console.log("  ✅ Stream URL obtained:", url ? url.substring(0, 80) + "..." : "null");
              if (!url) console.log("  ⚠️  URL is null — ytmusic-api getSong() returned no audio formats");
            }
          } catch (e) {
            console.log("  ❌ Stream fetch failed:", e.message);
          }
        }
      } else {
        console.log("  ⚠️  Search returned 0 songs — ytmusic-api may be broken or blocked");
      }
    }
  } catch (e) {
    console.log("  ❌ Search FAILED:", e.message);
  }

  console.log("\n" + "=".repeat(60));
  console.log("DONE");
  console.log("=".repeat(60));
}

run();
