# YTMusic API integration (how the app fetches YouTube Music)

This document explains how the `YTMusicAPI` class in this repo works and how to integrate the same approach into your app.

**Contents**
- Overview
- Requirements
- Configuration & discovery
- Search flow
- Stream URL resolution (detailed)
- Selecting the best audio format
- `itag` / local redirect behavior
- Caching, retries & error handling
- Integration examples
- Troubleshooting and tips

---

## Overview

`YTMusicAPI` is an internal client that connects to third-party YouTube Music API instances (self-hosted or public proxies) and performs two primary tasks:

- Search for videos (tracks) and normalize results to the app's `Track` shape.
- Fetch video metadata and compute a playable audio URL (preferring high-bitrate audio-only formats).

The implementation handles instance discovery, failover across instances, and small caches/TTL for robustness.

## Requirements

- A working HTTP API that exposes endpoints similar to the ones used here:
  - `/api/v1/search?q=...&type=video&fields=videoId,title,author,lengthSeconds,videoThumbnails`
  - `/api/v1/videos/<id>?fields=adaptiveFormats`
  - Optionally: `/latest_version?id=...&itag=...&local=true` for local redirection behavior on instances that support it.
- `fetch` available (React Native / Expo / Node with polyfill). `AbortController` is used for timeouts.

## Configuration & discovery

The class supports several ways to configure instances (ordered by preference):

- `EXPO_PUBLIC_YT_API_INSTANCES`: comma-separated list of instance base URLs (static list).
- `EXPO_PUBLIC_YT_API_DISCOVERY`: URL that returns a discovery JSON array; used to populate dynamic instances.
- A remote JSON config is fetched from a constant URL (`YTMUSIC_EXTRA_API_URL`) to add more instances.

The class caches discovered instances for a TTL and remembers the last-working instance to prefer it in subsequent calls.

Environment variables used in the repo:

- `EXPO_PUBLIC_YT_API_INSTANCES` — explicit instance list (comma-separated)
- `EXPO_PUBLIC_YT_API_DISCOVERY` — discovery JSON URL

If no instances are provided the code will still attempt discovery and extra-config.

## Search flow

1. `YTMusicAPI.search({ q, type })` is called.
2. Internally it calls `fetchFromAnyInstance('/api/v1/search?...')`.
3. `fetchFromAnyInstance` iterates ordered instances and returns the first successful JSON response, caching the last-working instance.
4. Results (YtApiSearchItem[]) are normalized to the app's `Track` shape via `normalizeTrack()`.

Key normalization fields:
- `id` = `videoId`
- `title`, `author` → `artist`
- thumbnails → `images`
- `lengthSeconds` → `duration` (ms)

## Stream URL resolution (detailed)

When you need a playable audio URL for a track id:

1. Call `YTMusicAPI.getStreamUrl(trackId)`.
2. That calls `fetchWithInstance('/api/v1/videos/<id>?fields=adaptiveFormats')`, trying ordered instances until one responds with JSON.
3. The response should include `adaptiveFormats` array describing media formats (each with `type`, optional `itag`, `bitrate`, and/or `url`).
4. `pickBestAudioFormat(adaptiveFormats, instance, trackId)`:
   - Filters formats whose `type` starts with `audio/`.
   - Sorts them preferring `audio/mp4` (M4A) over other containers, then by bitrate descending.
   - Chooses the top format as `best`.
   - If `best.itag` is present, it constructs a local-redirect URL using the selected instance: `<instance>/latest_version?id=<trackId>&itag=<itag>&local=true`.
     - This relies on the instance supporting `latest_version` redirect endpoint which will serve the audio content.
   - If no `itag`, but `best.url` exists, it returns that URL directly.
   - Otherwise it throws an error.

Important: the produced URL can be either a direct CDN link or a proxied instance URL that re-streams the audio.

## Selecting the best audio format (rules)

1. Keep only audio MIME types (type starting with `audio/`).
2. Prefer `audio/mp4` (M4A) when available since it is widely supported.
3. From formats with equal container preference, pick the one with highest `bitrate`.

Implementation detail: the code uses simple array filtering and sorting; you can extend it to prefer sample rate or channel count.

## `itag` and local redirect

- Many YouTube proxy implementations expose an endpoint that accepts an `itag` parameter and returns a stable / proxied playable URL. When `adaptiveFormats` include an `itag`, the implementation chooses to call that redirect endpoint rather than returning the raw signed CDN URL.
- Benefit: proxied redirect URLs often avoid expiring signatures and provide a consistent access method.
- If your instance doesn't support the `latest_version` redirect, `pickBestAudioFormat` will fall back to `format.url` if present.

## Caching, retries & error handling

- Instances are fetched and cached with TTLs; extra-config is fetched periodically.
- `fetchFromAnyInstance` tries instances in order and records the last-working one; on failure it clears the lastWorkingInstance cache for subsequent tries.
- Timeouts are applied using `AbortController` and `fetchWithTimeout`.
- If all instances fail the function throws `All instances failed`.

## Integration examples

1) Minimal usage (search + get stream):

```js
import { YTMusicAPI } from './ytmusic-api';

async function playExample(q) {
  const res = await YTMusicAPI.search({ q, type: 'track' });
  if (res.tracks.length === 0) return;
  const track = res.tracks[0];
  const streamUrl = await YTMusicAPI.getStreamUrl(track.id);
  // Use streamUrl with your player (TrackPlayer, expo-av, HTMLAudioElement, etc.)
}
```

2) Using with a queue (example for react-native-track-player):

```js
const item = {
  id: track.id.toString(),
  url: await YTMusicAPI.getStreamUrl(track.id),
  title: track.title,
  artist: track.artist,
  artwork: track.albumCover,
  duration: Math.floor(track.duration / 1000),
};
await TrackPlayer.reset();
await TrackPlayer.add([item]);
await TrackPlayer.play();
```

3) Environment variables (example .env):

```
EXPO_PUBLIC_YT_API_INSTANCES=https://yt1.example.com,https://yt2.example.com
EXPO_PUBLIC_YT_API_DISCOVERY=https://example.com/yt-discovery.json
```

## Troubleshooting & tips

- If search returns empty, check discovery and that instances return the expected `/api/v1/search` shape.
- If stream URL fails (403/expired), prefer `latest_version` redirect on the instance (use `itag`) or rotate instances.
- For web apps, CORS can block direct CDN URLs; either use a proxy instance that re-streams audio or host a server-side proxy.
- Respect Terms of Service: re-streaming or downloading YouTube content may violate platform policies — verify legal constraints for your use case.

## Extending this implementation

- Add richer format scoring (sample rate, channels, explicit container support).
- Add persistent instance list caching (local storage) and health checks.
- Provide server-side token signing if you control instances to avoid exposing raw CDN signatures to clients.

---

If you'd like, I can:

- Generate a pared-down `YTMusicAPI` copy you can drop into your project.
- Create a minimal server proxy that exposes the required `/api/v1` endpoints for local testing.

File in this repo: [openspot-mobile/lib/ytmusic-api.ts](../lib/ytmusic-api.ts)
