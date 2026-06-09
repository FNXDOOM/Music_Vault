package com.example.anujsharma.shuffler.volley;

/**
 * Created by anuj5 on 02-01-2018.
 */

public class Urls {

    public static final String CLIENT_ID = "L769F50feEmlOI4zzWlSaA4DWuQfPmc8";
    public static final String TRACKS = "https://api.soundcloud.com/tracks";
    public static final String USERS = "https://api.soundcloud.com/users";
    public static final String PLAYLISTS = "https://api.soundcloud.com/playlists";

    // ─── YouTube backend URL ──────────────────────────────────────────────────
    // • Emulator:       http://10.0.2.2:3000/   (Android emulator routes 10.0.2.2 → PC localhost)
    // • Physical device: http://<PC-WiFi-IP>:3000/
    //     1. Run `ipconfig` on Windows → find "IPv4 Address" under WiFi adapter (e.g. 192.168.1.5)
    //     2. Set BACKEND_HOST=192.168.1.5 in backend/.env
    //     3. Change the line below to:  "http://192.168.1.5:3000/"
    //
    // The backend is OPTIONAL — the app falls back to YouTubeInAppClient (no backend needed).
    // Set this URL only if you are running the backend server on your PC.
    // ─────────────────────────────────────────────────────────────────────────
    public static final String YOUTUBE_BACKEND_BASE_URL = "http://10.0.2.2:3000/";

}
