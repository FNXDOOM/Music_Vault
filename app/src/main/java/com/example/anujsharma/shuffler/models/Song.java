package com.example.anujsharma.shuffler.models;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaDescriptionCompat;

import com.example.anujsharma.shuffler.utilities.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class Song implements Parcelable {
    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }

        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };

    private long id, duration, user_id;
    private String title, artist, genre, album;
    private File songFile;
    private String permalink;
    private String songArtwork, streamUrl;
    private int playbackCount, likesCount, favoritngsCount;
    private User user;

    // Full SoundCloud constructor
    public Song(long id, long duration, String title, String artist, String genre, String permalink,
                String songArtwork, String streamUrl, int playbackCount, int likesCount, User user) {
        this.id = id;
        this.duration = duration;
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.permalink = permalink;
        this.songArtwork = songArtwork;
        this.streamUrl = streamUrl;
        this.playbackCount = playbackCount;
        this.likesCount = likesCount;
        this.user = user;
    }

    // Local file constructor
    public Song(String title, String artist, String genre, String album, int duration, File songFile) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.album = album;
        this.duration = duration;
        this.songFile = songFile;
    }

    // Secondary SoundCloud constructor
    public Song(long song_id, long user_id, long duration, String title, String artistName, String genre,
                String artworkUrl, String streamUrl, int playbackCount, int likesCount) {
        this.id = song_id;
        this.user_id = user_id;
        this.duration = duration;
        this.title = title;
        this.artist = artistName;
        this.genre = genre;
        this.songArtwork = artworkUrl;
        this.streamUrl = streamUrl;
        this.playbackCount = playbackCount;
        this.likesCount = likesCount;
    }

    // JSON constructor (handles both SoundCloud and YouTube backend format)
    public Song(JSONObject song) {
        try {
            if (song.has("title") && song.has("artist") && song.has("durationMs")) {
                // YouTube backend format
                String idStr = song.has("id") ? song.getString("id") : "0";
                try {
                    this.id = Long.parseLong(idStr);
                } catch (NumberFormatException e) {
                    this.id = idStr.hashCode();
                }
                this.permalink = idStr; // store videoId in permalink
                this.user_id = 0;
                // FIX: store duration as-is in milliseconds; Utilities.formatTime() divides by 1000
                this.duration = song.has("durationMs") ? song.getLong("durationMs") : 0;
                this.title = song.getString("title").trim();
                this.artist = song.has("artist") ? song.getString("artist").trim() : "Unknown Artist";
                this.genre = "";
                this.album = song.has("album") ? song.getString("album").trim() : "";
                this.songArtwork = song.has("artworkUrl") && !song.isNull("artworkUrl") ? song.getString("artworkUrl") : "";
                this.streamUrl = "";
                this.playbackCount = 0;
                this.likesCount = 0;
                this.favoritngsCount = 0;
            } else {
                // SoundCloud format
                this.id = song.getLong("id");
                this.user_id = song.getLong("user_id");
                this.duration = song.getLong("duration");
                this.title = song.getString("title").trim();
                this.genre = song.getString("genre").trim();
                this.permalink = song.getString("permalink").trim();
                this.songArtwork = song.getString("artwork_url");
                this.streamUrl = song.has("stream_url") ? song.getString("stream_url") : "";
                this.playbackCount = song.has("playback_count") ? song.getInt("playback_count") : 0;
                this.likesCount = song.has("likes_count") ? song.getInt("likes_count") : 0;
                this.favoritngsCount = song.has("favoritings_count") ? song.getInt("favoritings_count") : 0;
                this.user = new User(song.getJSONObject("user"));
                this.artist = this.user.getUsername();
                if (this.likesCount == 0) this.likesCount = this.favoritngsCount;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Parcel constructor
    protected Song(Parcel in) {
        id = in.readLong();
        user_id = in.readLong();
        duration = in.readLong();
        title = in.readString();
        artist = in.readString();
        genre = in.readString();
        album = in.readString();
        permalink = in.readString();
        songArtwork = in.readString();
        streamUrl = in.readString();
        playbackCount = in.readInt();
        likesCount = in.readInt();
        favoritngsCount = in.readInt();
    }

    /** Constructor for YouTube Music songs from the backend */
    public Song(String videoId, String title, String artist, long durationMs, String artworkUrl) {
        try {
            this.id = Long.parseLong(videoId);
        } catch (NumberFormatException e) {
            this.id = videoId.hashCode();
        }
        this.permalink = videoId; // store videoId in permalink for stream URL lookup
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        // FIX: keep duration in milliseconds — formatTime() divides by 1000 itself
        this.duration = durationMs;
        this.songArtwork = artworkUrl != null ? artworkUrl : "";
        this.streamUrl = "";
        this.genre = "";
        this.album = "";
        this.user = null;
    }

    // ─── Getters & setters ───────────────────────────────────────────────────

    public String getVideoId() {
        // permalink always holds the real YouTube video ID (e.g. "dQw4w9WgXcQ").
        // Never fall back to the numeric id — it's either a hash or a SoundCloud long,
        // neither of which is a valid YouTube video ID.
        return (permalink != null && !permalink.isEmpty()) ? permalink : "";
    }

    public void setVideoId(String videoId) {
        this.permalink = videoId;
    }

    public void setStreamUrl(String url) {
        this.streamUrl = url;
    }

    public long getUser_id() { return user_id; }
    public User getUser() { return user; }
    public long getId() { return id; }
    public String getPermalink() { return permalink; }
    public String getSongArtwork() { return songArtwork; }
    public String getStreamUrl() { return streamUrl; }
    public int getPlaybackCount() { return playbackCount; }
    public int getLikesCount() { return likesCount; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getGenre() { return genre; }
    public String getAlbum() { return album; }
    public long getDuration() { return duration; }
    public File getSongFile() { return songFile; }
    public int getFavoritngsCount() { return favoritngsCount; }

    public MediaDescriptionCompat getMediaDescription(Context context) {
        return new MediaDescriptionCompat.Builder()
                .setTitle(getTitle())
                .setMediaId(getId() + "")
                .setDescription(getArtist())
                .setIconUri(Uri.parse(Utilities.getLargeArtworkUrl(getSongArtwork())))
                .build();
    }

    // ─── Parcelable ──────────────────────────────────────────────────────────

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(user_id);
        dest.writeLong(duration);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(genre);
        dest.writeString(album);
        dest.writeString(permalink);
        dest.writeString(songArtwork);
        dest.writeString(streamUrl);
        dest.writeInt(playbackCount);
        dest.writeInt(likesCount);
        dest.writeInt(favoritngsCount);
    }
}
