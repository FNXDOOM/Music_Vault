package com.example.anujsharma.shuffler.utilities;

import android.net.Uri;

import com.example.anujsharma.shuffler.models.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * In-app YouTube client with no local backend dependency.
 * Uses public Piped-compatible endpoints to fetch search and stream URLs.
 */
public class YouTubeInAppClient {
    private static final long STREAM_CACHE_TTL_MS = 20L * 60L * 1000L;

    // Public youtubei key commonly used by Android clients.
    private static final String YOUTUBEI_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String YOUTUBEI_SEARCH_URL =
            "https://www.youtube.com/youtubei/v1/search?key=" + YOUTUBEI_KEY;
    private static final String YOUTUBEI_PLAYER_URL =
            "https://youtubei.googleapis.com/youtubei/v1/player?key=" + YOUTUBEI_KEY;

    private static final String[] API_BASES = new String[]{
            "https://pipedapi.adminforge.de",
            "https://pipedapi.drgns.space",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.syncpundit.io",
            "https://api.piped.yt"
    };
    private static final String[] INVIDIOUS_BASES = new String[]{
            "https://invidious.fdn.fr",
            "https://invidious.privacyredirect.com",
            "https://vid.puffyan.us"
    };

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Map<String, CachedStream> streamCache = new HashMap<>();

    public ArrayList<Song> searchSongs(String query, int limit) throws Exception {
        try {
            return searchSongsFromYoutubei(query, limit);
        } catch (Exception ignored) {
            // fallback below
        }

        String encoded = URLEncoder.encode(query, "UTF-8");
        Exception last = null;
        for (String base : API_BASES) {
            try {
                String url = base + "/search?q=" + encoded + "&filter=music_songs";
                Request request = new Request.Builder().url(url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;

                    String json = response.body().string();
                    JSONObject root = new JSONObject(json);
                    JSONArray items = root.optJSONArray("items");
                    if (items == null) continue;

                    ArrayList<Song> result = new ArrayList<>();
                    for (int i = 0; i < items.length() && result.size() < limit; i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;

                        String type = item.optString("type", "");
                        if (!"stream".equalsIgnoreCase(type)) continue;

                        String videoId = item.optString("id", "");
                        if (videoId.isEmpty()) {
                            String urlField = item.optString("url", "");
                            if (!urlField.isEmpty()) {
                                Uri uri = Uri.parse("https://youtube.com" + urlField);
                                videoId = uri.getQueryParameter("v");
                            }
                        }
                        if (videoId == null || videoId.isEmpty()) continue;

                        String title = item.optString("title", "Unknown");
                        String artist = item.optString("uploaderName", "");
                        long durationMs = item.optLong("duration", 0) * 1000L;
                        String artworkUrl = item.optString("thumbnail", "");
                        Song song = new Song(videoId, title, artist, durationMs, artworkUrl);
                        result.add(song);
                    }
                    if (!result.isEmpty()) return result;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return new ArrayList<>();
    }

    public String resolveBestAudioStreamUrl(String videoId) throws Exception {
        CachedStream cached = streamCache.get(videoId);
        if (cached != null && !cached.isExpired()) {
            return cached.url;
        }

        try {
            String resolved = resolveBestAudioStreamUrlFromYoutubei(videoId);
            putCached(videoId, resolved);
            return resolved;
        } catch (Exception ignored) {
            // fallback below
        }

        Exception last = null;
        Exception lastNonTlsError = null;
        for (String base : API_BASES) {
            try {
                String url = base + "/streams/" + videoId;
                Request request = new Request.Builder().url(url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;
                    String json = response.body().string();
                    JSONObject root = new JSONObject(json);
                    JSONArray audioStreams = root.optJSONArray("audioStreams");
                    if (audioStreams == null || audioStreams.length() == 0) continue;

                    int bestIndex = -1;
                    int bestBitrate = Integer.MIN_VALUE;
                    for (int i = 0; i < audioStreams.length(); i++) {
                        JSONObject a = audioStreams.optJSONObject(i);
                        if (a == null) continue;
                        int bitrate = a.optInt("bitrate", 0);
                        String streamUrl = a.optString("url", "");
                        if (streamUrl == null || streamUrl.isEmpty()) continue;
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate;
                            bestIndex = i;
                        }
                    }

                    if (bestIndex >= 0) {
                        String streamUrl = audioStreams.getJSONObject(bestIndex).optString("url", "");
                        if (streamUrl != null && !streamUrl.isEmpty()) {
                            putCached(videoId, streamUrl);
                            return streamUrl;
                        }
                    }
                }
            } catch (SSLHandshakeException ssl) {
                // Some public mirror cert chains expire occasionally; skip this host and try next.
                last = ssl;
            } catch (Exception e) {
                last = e;
                lastNonTlsError = e;
            }
        }
        if (lastNonTlsError != null) throw lastNonTlsError;
        if (last != null) {
            try {
                String inv = resolveBestAudioStreamUrlFromInvidious(videoId);
                putCached(videoId, inv);
                return inv;
            } catch (Exception ignored) {
                // fall through and throw last
            }
            throw last;
        }
        try {
            String inv = resolveBestAudioStreamUrlFromInvidious(videoId);
            putCached(videoId, inv);
            return inv;
        } catch (Exception ignored) {
            // fall through
        }
        throw new IllegalStateException(String.format(Locale.US, "Unable to resolve stream URL for %s", videoId));
    }

    private String resolveBestAudioStreamUrlFromInvidious(String videoId) throws Exception {
        Exception last = null;
        for (String base : INVIDIOUS_BASES) {
            try {
                String url = base + "/api/v1/videos/" + videoId;
                Request request = new Request.Builder().url(url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;
                    JSONObject root = new JSONObject(response.body().string());
                    JSONArray adaptive = root.optJSONArray("adaptiveFormats");
                    if (adaptive == null || adaptive.length() == 0) continue;
                    int bestIndex = -1;
                    int bestBitrate = Integer.MIN_VALUE;
                    for (int i = 0; i < adaptive.length(); i++) {
                        JSONObject f = adaptive.optJSONObject(i);
                        if (f == null) continue;
                        String type = f.optString("type", "");
                        if (!type.startsWith("audio/")) continue;
                        String streamUrl = f.optString("url", "");
                        if (streamUrl == null || streamUrl.isEmpty()) continue;
                        int bitrate = f.optInt("bitrate", 0);
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate;
                            bestIndex = i;
                        }
                    }
                    if (bestIndex >= 0) {
                        String streamUrl = adaptive.getJSONObject(bestIndex).optString("url", "");
                        if (streamUrl != null && !streamUrl.isEmpty()) return streamUrl;
                    }
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new IllegalStateException("Invidious fallback failed");
    }

    public synchronized void invalidateStream(String videoId) {
        streamCache.remove(videoId);
    }

    public synchronized void prefetchStream(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        CachedStream cached = streamCache.get(videoId);
        if (cached != null && !cached.isExpired()) return;
        try {
            resolveBestAudioStreamUrl(videoId);
        } catch (Exception ignored) {
            // best-effort prefetch
        }
    }

    private ArrayList<Song> searchSongsFromYoutubei(String query, int limit) throws Exception {
        JSONObject payload = new JSONObject();
        JSONObject context = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("hl", "en");
        client.put("gl", "US");
        client.put("clientName", "WEB");
        client.put("clientVersion", "2.20240709.01.00");
        context.put("client", client);
        payload.put("context", context);
        payload.put("query", query);

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                payload.toString(), okhttp3.MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(YOUTUBEI_SEARCH_URL)
                .post(body)
                .header("Content-Type", "application/json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("youtubei search failed: " + response.code());
            }
            String json = response.body().string();
            JSONObject root = new JSONObject(json);

            ArrayList<JSONObject> videoRenderers = new ArrayList<>();
            collectVideoRenderers(root, videoRenderers);

            ArrayList<Song> songs = new ArrayList<>();
            for (JSONObject vr : videoRenderers) {
                if (songs.size() >= limit) break;
                String videoId = vr.optString("videoId", "");
                if (videoId.isEmpty()) continue;

                String title = "";
                JSONObject titleObj = vr.optJSONObject("title");
                if (titleObj != null) {
                    JSONArray runs = titleObj.optJSONArray("runs");
                    if (runs != null && runs.length() > 0) {
                        title = runs.optJSONObject(0).optString("text", "");
                    } else {
                        title = titleObj.optString("simpleText", "");
                    }
                }

                String artist = "";
                JSONObject ownerObj = vr.optJSONObject("ownerText");
                if (ownerObj != null) {
                    JSONArray runs = ownerObj.optJSONArray("runs");
                    if (runs != null && runs.length() > 0) {
                        artist = runs.optJSONObject(0).optString("text", "");
                    } else {
                        artist = ownerObj.optString("simpleText", "");
                    }
                }

                long durationMs = 0L;
                JSONObject lengthObj = vr.optJSONObject("lengthText");
                String length = "";
                if (lengthObj != null) {
                    JSONArray runs = lengthObj.optJSONArray("runs");
                    if (runs != null && runs.length() > 0) {
                        length = runs.optJSONObject(0).optString("text", "");
                    } else {
                        length = lengthObj.optString("simpleText", "");
                    }
                }
                if (!length.isEmpty()) {
                    String[] parts = length.split(":");
                    if (parts.length == 2) {
                        durationMs = (Long.parseLong(parts[0]) * 60L + Long.parseLong(parts[1])) * 1000L;
                    } else if (parts.length == 3) {
                        durationMs = (Long.parseLong(parts[0]) * 3600L
                                + Long.parseLong(parts[1]) * 60L
                                + Long.parseLong(parts[2])) * 1000L;
                    }
                }

                String artwork = "";
                JSONObject thumbObj = vr.optJSONObject("thumbnail");
                if (thumbObj != null) {
                    JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
                    if (thumbs != null && thumbs.length() > 0) {
                        artwork = thumbs.optJSONObject(thumbs.length() - 1).optString("url", "");
                    }
                }

                songs.add(new Song(videoId, title, artist, durationMs, artwork));
            }
            return songs;
        }
    }

    private String resolveBestAudioStreamUrlFromYoutubei(String videoId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("videoId", videoId);
        JSONObject context = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("hl", "en");
        client.put("gl", "US");
        client.put("clientName", "ANDROID");
        client.put("clientVersion", "16.20");
        context.put("client", client);
        payload.put("context", context);

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                payload.toString(), okhttp3.MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(YOUTUBEI_PLAYER_URL)
                .post(body)
                .header("Content-Type", "application/json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("youtubei player failed: " + response.code());
            }
            String json = response.body().string();
            JSONObject root = new JSONObject(json);
            JSONObject streamingData = root.optJSONObject("streamingData");
            if (streamingData == null) throw new IllegalStateException("No streamingData in youtubei player response");
            JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
            if (adaptive == null || adaptive.length() == 0) {
                throw new IllegalStateException("No adaptiveFormats in youtubei player response");
            }

            int bestIndex = -1;
            int bestBitrate = Integer.MIN_VALUE;
            for (int i = 0; i < adaptive.length(); i++) {
                JSONObject f = adaptive.optJSONObject(i);
                if (f == null) continue;
                if (f.optInt("audioChannels", 0) <= 0) continue;
                String url = f.optString("url", "");
                if (url == null || url.isEmpty()) continue;
                int bitrate = f.optInt("bitrate", 0);
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0) throw new IllegalStateException("No direct audio url in adaptiveFormats");
            return adaptive.getJSONObject(bestIndex).optString("url");
        }
    }

    private void collectVideoRenderers(Object node, ArrayList<JSONObject> out) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            JSONObject videoRenderer = obj.optJSONObject("videoRenderer");
            if (videoRenderer != null) out.add(videoRenderer);

            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                collectVideoRenderers(obj.opt(key), out);
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                collectVideoRenderers(arr.opt(i), out);
            }
        }
    }

    private synchronized void putCached(String videoId, String url) {
        if (videoId == null || videoId.isEmpty() || url == null || url.isEmpty()) return;
        streamCache.put(videoId, new CachedStream(url, System.currentTimeMillis() + STREAM_CACHE_TTL_MS));
    }

    private static class CachedStream {
        private final String url;
        private final long expiresAtMs;

        private CachedStream(String url, long expiresAtMs) {
            this.url = url;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }
}
