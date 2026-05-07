package com.example.anujsharma.shuffler.volley;

/**
 * Created by anuj5 on 02-01-2018.
 */

public class Urls {

    public static final String CLIENT_ID = "L769F50feEmlOI4zzWlSaA4DWuQfPmc8";
    public static final String TRACKS = "https://api.soundcloud.com/tracks";
    public static final String USERS = "https://api.soundcloud.com/users";
    public static final String PLAYLISTS = "https://api.soundcloud.com/playlists";

    // 10.0.2.2 = Android emulator localhost only.
    // FIX: On a physical device, replace with your PC's WiFi IP.
    // Run `ipconfig` on Windows -> look for "IPv4 Address" under your WiFi adapter.
    // Example: "http://192.168.1.5:3000/"
    // To find it automatically: Settings -> WiFi -> your network -> IP address on PC
    public static final String YOUTUBE_BACKEND_BASE_URL = "http://10.0.2.2:3000/";

}
