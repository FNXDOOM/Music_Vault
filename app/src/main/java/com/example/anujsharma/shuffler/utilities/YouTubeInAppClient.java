package com.example.anujsharma.shuffler.utilities;

import android.net.Uri;

import com.example.anujsharma.shuffler.models.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
 import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * In-app YouTube client — no local backend dependency.
 * Stream resolution priority:
 *   1. YouTube Innertube /v1/player (ANDROID client — most reliable, no sign-in)
 *   2. Piped public API  (fallback)
 *   3. Invidious public API (final fallback)
 */
public class YouTubeInAppClient {

    private static final long STREAM_CACHE_TTL_MS = 20L * 60L * 1000L; // 20 min

    // ── Innertube (YouTube internal API) ──────────────────────────────────────
    private static final String INNERTUBE_SEARCH_URL =
            "https://www.youtube.com/youtubei/v1/search";
    private static final String INNERTUBE_PLAYER_URL =
            "https://www.youtube.com/youtubei/v1/player";
    // ANDROID client key — public, widely used, returns direct stream URLs without cipher
    private static final String INNERTUBE_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
    private static final String ANDROID_CLIENT_VERSION = "18.11.34";
    private static final int ANDROID_SDK_VERSION = 30;

    // ── Piped fallback ────────────────────────────────────────────────────────
    private static final String[] PIPED_BASES = {
            "https://pipedapi.adminforge.de",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://api.piped.yt",
    };

    // ── Invidious fallback ────────────────────────────────────────────────────
    private static final String[] INVIDIOUS_BASES = {
            "https://invidious.fdn.fr",
            "https://inv.thepixora.com",
            "https://invidious.io.lol",
    };

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    // Static so all instances (MainActivity, ViewSongActivity, fragments) share one cache.
    private static final Map<String, CachedStream> streamCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static String cachedVisitorData = null;

    private synchronized String getVisitorData() {
        if (cachedVisitorData != null && !cachedVisitorData.isEmpty()) return cachedVisitorData;
        try {
            JSONObject payload = buildInnertubeContext("WEB", "2.20240709.01.00");
            payload.put("query", "a");
            String json = postJson(INNERTUBE_SEARCH_URL + "?key=" + INNERTUBE_KEY, payload.toString());
            JSONObject root = new JSONObject(json);
            JSONObject responseContext = root.optJSONObject("responseContext");
            if (responseContext != null) {
                cachedVisitorData = responseContext.optString("visitorData", "");
            }
        } catch (Exception ignored) {}
        return cachedVisitorData != null ? cachedVisitorData : "";
    }

    // ═════════════════════════════ PUBLIC API ════════════════════════════════

    /** Search YouTube for songs matching {@code query}. */
    public ArrayList<Song> searchSongs(String query, int limit) throws Exception {
        // Try Innertube first (most reliable)
        try {
            return searchViaInnertube(query, limit);
        } catch (Exception e) {
            System.out.println( "Innertube search failed, trying Piped: " + e.getMessage());
        }
        // Piped fallback
        String encoded = URLEncoder.encode(query, "UTF-8");
        for (String base : PIPED_BASES) {
            try {
                ArrayList<Song> result = searchViaPiped(base, encoded, limit);
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {}
        }
        return new ArrayList<>();
    }

    /** Resolve the best audio stream URL for {@code videoId}. Uses a 20-min cache. */
    public String resolveBestAudioStreamUrl(String videoId) throws Exception {
        CachedStream cached = streamCache.get(videoId);
        if (cached != null && !cached.isExpired()) return cached.url;

        // 1. Try Innertube client — gives direct, non-ciphered URLs
        try {
            String url = resolveViaInnertube(videoId);
            if (url != null && !url.isEmpty()) {
                putCached(videoId, url);
                return url;
            }
        } catch (Exception e) {
            System.out.println( "Innertube stream resolve failed: " + e.getMessage());
        }

        // 2. Try Piped
        for (String base : PIPED_BASES) {
            try {
                String url = resolveViaPiped(base, videoId);
                if (url != null && !url.isEmpty()) {
                    putCached(videoId, url);
                    return url;
                }
            } catch (Exception ignored) {}
        }

        // 3. Try Invidious (proxy URL)
        for (String base : INVIDIOUS_BASES) {
            try {
                String url = resolveViaInvidious(base, videoId);
                if (url != null && !url.isEmpty()) {
                    putCached(videoId, url);
                    return url;
                }
            } catch (Exception ignored) {}
        }

        throw new IllegalStateException("All stream resolution methods failed for " + videoId);
    }

    public void invalidateStream(String videoId) {
        streamCache.remove(videoId);
    }

    /** Best-effort background prefetch — never throws. */
    public void prefetchStream(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        CachedStream cached = streamCache.get(videoId);
        if (cached != null && !cached.isExpired()) return;
        try { resolveBestAudioStreamUrl(videoId); } catch (Exception ignored) {}
    }

    // ═════════════════════════ SEARCH IMPLEMENTATIONS ═══════════════════════

    private ArrayList<Song> searchViaInnertube(String query, int limit) throws Exception {
        JSONObject payload = buildInnertubeContext("WEB", "2.20240709.01.00");
        payload.put("query", query);

        String json = postJson(INNERTUBE_SEARCH_URL + "?key=" + INNERTUBE_KEY, payload.toString());
        JSONObject root = new JSONObject(json);

        ArrayList<JSONObject> renderers = new ArrayList<>();
        collectVideoRenderers(root, renderers);

        ArrayList<Song> songs = new ArrayList<>();
        for (JSONObject vr : renderers) {
            if (songs.size() >= limit) break;
            String videoId = vr.optString("videoId", "");
            if (videoId.isEmpty()) continue;

            String title = extractText(vr.optJSONObject("title"));
            String artist = extractText(vr.optJSONObject("ownerText"));
            long durationMs = parseDurationMs(extractText(vr.optJSONObject("lengthText")));
            String artwork = extractLastThumbnailUrl(vr.optJSONObject("thumbnail"));
            songs.add(new Song(videoId, title, artist, durationMs, artwork));
        }
        return songs;
    }

    private ArrayList<Song> searchViaPiped(String base, String encodedQuery, int limit) throws Exception {
        String url = base + "/search?q=" + encodedQuery + "&filter=music_songs";
        Request req = new Request.Builder().url(url).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return new ArrayList<>();
            JSONObject root = new JSONObject(resp.body().string());
            JSONArray items = root.optJSONArray("items");
            if (items == null) return new ArrayList<>();

            ArrayList<Song> result = new ArrayList<>();
            for (int i = 0; i < items.length() && result.size() < limit; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !"stream".equalsIgnoreCase(item.optString("type"))) continue;
                String videoId = item.optString("id", "");
                if (videoId.isEmpty()) {
                    String urlField = item.optString("url", "");
                    if (!urlField.isEmpty()) {
                        Uri uri = Uri.parse("https://youtube.com" + urlField);
                        videoId = uri.getQueryParameter("v");
                    }
                }
                if (videoId == null || videoId.isEmpty()) continue;
                result.add(new Song(
                        videoId,
                        item.optString("title", "Unknown"),
                        item.optString("uploaderName", ""),
                        item.optLong("duration", 0) * 1000L,
                        item.optString("thumbnail", "")
                ));
            }
            return result;
        }
    }

    // ════════════════════════ STREAM IMPLEMENTATIONS ════════════════════════

    private String resolveViaInnertube(String videoId) throws Exception {
        // Using ANDROID_VR client as it currently bypasses PoToken requirements
        JSONObject payload = buildInnertubeContext("ANDROID_VR", "1.48.27");
        // Add specific fields
        JSONObject clientNode = payload.getJSONObject("context").getJSONObject("client");
        // Remove osName and osVersion if they were added for IOS
        clientNode.put("userAgent", "Mozilla/5.0");
        String visitorData = getVisitorData();
        if (visitorData != null && !visitorData.isEmpty()) {
            clientNode.put("visitorData", visitorData);
        }
        payload.put("videoId", videoId);
        payload.put("racyCheckOk", true);
        payload.put("contentCheckOk", true);

        String json = postJson(INNERTUBE_PLAYER_URL + "?key=" + INNERTUBE_KEY, payload.toString());
        JSONObject root = new JSONObject(json);

        // Check playability status
        JSONObject status = root.optJSONObject("playabilityStatus");
        if (status != null) {
            String statusStr = status.optString("status", "");
            if ("ERROR".equals(statusStr) || "LOGIN_REQUIRED".equals(statusStr)
                    || "UNPLAYABLE".equals(statusStr)) {
                throw new IllegalStateException("Innertube: " + statusStr
                        + " - " + status.optString("reason", ""));
            }
        }

        JSONObject streamingData = root.optJSONObject("streamingData");
        if (streamingData == null) throw new IllegalStateException("No streamingData");

        JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
        if (adaptive == null || adaptive.length() == 0)
            throw new IllegalStateException("No adaptiveFormats");

        // Pick highest-bitrate audio-only format
        int bestIndex = -1;
        int bestBitrate = -1;
        for (int i = 0; i < adaptive.length(); i++) {
            JSONObject f = adaptive.optJSONObject(i);
            if (f == null) continue;
            String mimeType = f.optString("mimeType", "");
            if (!mimeType.startsWith("audio/")) continue;
            String directUrl = f.optString("url", "");
            if (directUrl.isEmpty()) continue; // skip if ciphered
            int bitrate = f.optInt("bitrate", 0);
            if (bitrate > bestBitrate) {
                bestBitrate = bitrate;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) throw new IllegalStateException("No direct audio URL in adaptiveFormats");
        return adaptive.getJSONObject(bestIndex).getString("url");
    }

    private String resolveViaPiped(String base, String videoId) throws Exception {
        String url = base + "/streams/" + videoId;
        Request req = new Request.Builder().url(url).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            JSONObject root = new JSONObject(resp.body().string());
            JSONArray streams = root.optJSONArray("audioStreams");
            if (streams == null || streams.length() == 0) return null;

            String best = null;
            int bestBitrate = -1;
            for (int i = 0; i < streams.length(); i++) {
                JSONObject s = streams.optJSONObject(i);
                if (s == null) continue;
                int bitrate = s.optInt("bitrate", 0);
                String streamUrl = s.optString("url", "");
                if (!streamUrl.isEmpty() && bitrate > bestBitrate) {
                    bestBitrate = bitrate;
                    best = streamUrl;
                }
            }
            return best;
        }
    }

    private String resolveViaInvidious(String base, String videoId) throws Exception {
        String url = base + "/api/v1/videos/" + videoId;
        Request req = new Request.Builder().url(url).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            JSONObject root = new JSONObject(resp.body().string());
            JSONArray adaptive = root.optJSONArray("adaptiveFormats");
            if (adaptive == null || adaptive.length() == 0) return null;

            // Try to find a format with a direct URL first; fallback to proxy
            int bestIndex = -1;
            int bestBitrate = -1;
            int bestItag = -1;
            for (int i = 0; i < adaptive.length(); i++) {
                JSONObject f = adaptive.optJSONObject(i);
                if (f == null) continue;
                String type = f.optString("type", "");
                if (!type.startsWith("audio/")) continue;
                int bitrate = 0;
                try { bitrate = Integer.parseInt(f.optString("bitrate", "0")); } catch (Exception ignored) {}
                if (bitrate > bestBitrate) {
                    // Prefer format with direct URL
                    String directUrl = f.optString("url", "");
                    if (!directUrl.isEmpty()) {
                        bestBitrate = bitrate;
                        bestIndex = i;
                        bestItag = -1; // signal: use directUrl
                    } else {
                        // Note itag for proxy URL fallback
                        int itag = 0;
                        try { itag = Integer.parseInt(f.optString("itag", "0")); } catch (Exception ignored) {}
                        if (itag > 0) {
                            bestBitrate = bitrate;
                            bestIndex = i;
                            bestItag = itag;
                        }
                    }
                }
            }
            if (bestIndex < 0) return null;

            // ALWAYS use Invidious proxy URL with local=true so it streams through their server.
            // Direct URLs are IP-bound to the server that requested them and will 403 on the client.
            int finalItag = bestItag > 0 ? bestItag : adaptive.getJSONObject(bestIndex).optInt("itag", 251);
            return base + "/latest_version?id=" + videoId + "&itag=" + finalItag + "&local=true";
        }
    }

    // ═════════════════════════════ HELPERS ══════════════════════════════════

    private String postJson(String url, String body) throws Exception {
        RequestBody rb = RequestBody.create(body, MediaType.parse("application/json"));
        Request req = new Request.Builder()
                .url(url)
                .post(rb)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .header("Origin", "https://www.youtube.com")
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new IllegalStateException("HTTP " + resp.code() + " for " + url);
            return resp.body().string();
        }
    }

    private JSONObject buildInnertubeContext(String clientName, String clientVersion) throws Exception {
        JSONObject client = new JSONObject();
        client.put("hl", "en");
        client.put("gl", "US");
        client.put("clientName", clientName);
        client.put("clientVersion", clientVersion);
        JSONObject context = new JSONObject();
        context.put("client", client);
        JSONObject payload = new JSONObject();
        payload.put("context", context);
        return payload;
    }

    private void collectVideoRenderers(Object node, ArrayList<JSONObject> out) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (obj.has("videoRenderer")) out.add(obj.optJSONObject("videoRenderer"));
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) collectVideoRenderers(obj.opt(keys.next()), out);
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) collectVideoRenderers(arr.opt(i), out);
        }
    }

    private String extractText(JSONObject obj) {
        if (obj == null) return "";
        JSONArray runs = obj.optJSONArray("runs");
        if (runs != null && runs.length() > 0) {
            JSONObject run = runs.optJSONObject(0);
            return run != null ? run.optString("text", "") : "";
        }
        return obj.optString("simpleText", "");
    }

    private long parseDurationMs(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            String[] parts = text.split(":");
            if (parts.length == 2) return (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000;
            if (parts.length == 3) return (Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2])) * 1000;
        } catch (Exception ignored) {}
        return 0;
    }

    private String extractLastThumbnailUrl(JSONObject thumbObj) {
        if (thumbObj == null) return "";
        JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
        if (thumbs != null && thumbs.length() > 0) {
            JSONObject last = thumbs.optJSONObject(thumbs.length() - 1);
            return last != null ? last.optString("url", "") : "";
        }
        return "";
    }

    private void putCached(String videoId, String url) {
        if (videoId == null || videoId.isEmpty() || url == null || url.isEmpty()) return;
        streamCache.put(videoId, new CachedStream(url, System.currentTimeMillis() + STREAM_CACHE_TTL_MS));
    }

    private static class CachedStream {
        final String url;
        final long expiresAtMs;
        CachedStream(String url, long expiresAtMs) { this.url = url; this.expiresAtMs = expiresAtMs; }
        boolean isExpired() { return System.currentTimeMillis() >= expiresAtMs; }
    }
}
