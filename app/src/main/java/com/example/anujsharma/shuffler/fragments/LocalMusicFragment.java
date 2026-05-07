package com.example.anujsharma.shuffler.fragments;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.anujsharma.shuffler.R;
import com.example.anujsharma.shuffler.activities.MainActivity;
import com.example.anujsharma.shuffler.adapters.LocalMusicAdapter;
import com.example.anujsharma.shuffler.models.Playlist;
import com.example.anujsharma.shuffler.models.Song;
import com.example.anujsharma.shuffler.utilities.Constants;

import java.io.File;
import java.util.ArrayList;

/**
 * Fragment that lists all local audio files on the device and allows
 * playback via the existing ExoPlayerService using file:// URIs.
 */
public class LocalMusicFragment extends Fragment {

    private static final String TAG = "LocalMusicFragment";
    private static final String PLAYLIST_NAME = "local_device";

    private Context context;
    private RecyclerView recyclerView;
    private LocalMusicAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    private final ArrayList<Song> localSongs = new ArrayList<>();

    public LocalMusicFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_music, container, false);
        initialise(view);
        loadLocalSongs();
        return view;
    }

    private void initialise(View view) {
        recyclerView = view.findViewById(R.id.rvLocalMusic);
        progressBar = view.findViewById(R.id.pbLocalMusic);
        tvEmpty    = view.findViewById(R.id.tvLocalMusicEmpty);

        adapter = new LocalMusicAdapter(context, localSongs, (position) -> {
            if (localSongs.isEmpty()) return;
            Playlist playlist = buildPlaylist();
            ((MainActivity) getActivity()).playSongInMainActivityLocal(position, playlist);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
    }

    /** Query MediaStore for all audio files on the device. Runs on a background thread. */
    private void loadLocalSongs() {
        showProgress();
        new Thread(() -> {
            ArrayList<Song> songs = queryMediaStore();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                localSongs.clear();
                localSongs.addAll(songs);
                adapter.notifyDataSetChanged();
                if (localSongs.isEmpty()) {
                    showEmpty();
                } else {
                    showList();
                }
            });
        }).start();
    }

    private ArrayList<Song> queryMediaStore() {
        ArrayList<Song> result = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA          // absolute file path
        };

        // Only music (is_music flag) and duration > 30 seconds to skip short clips
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " > 30000";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, selection, null, sortOrder)) {

            if (cursor == null) return result;

            int colId       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int colTitle    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int colArtist   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int colAlbum    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int colGenre    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE);
            int colDuration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int colData     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id         = cursor.getLong(colId);
                String path     = cursor.getString(colData);
                String title    = cursor.getString(colTitle);
                String artist   = cursor.getString(colArtist);
                String album    = cursor.getString(colAlbum);
                String genre    = cursor.getString(colGenre);
                long durationMs = cursor.getLong(colDuration);

                if (title == null || title.isEmpty()) continue;
                if (artist == null) artist = "Unknown Artist";
                if (album  == null) album  = "Unknown Album";
                if (genre  == null) genre  = "";

                File file = new File(path);
                if (!file.exists()) continue;

                // duration stored in seconds in Song's local constructor
                int durationSec = (int) (durationMs / 1000);
                Song song = new Song(title, artist, genre, album, durationSec, file);
                // Temporarily store the _ID in the videoId field so buildPlaylist can construct the content:// URI
                song.setVideoId(String.valueOf(id));
                result.add(song);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "MediaStore query failed", e);
        }
        return result;
    }

    /** Build a Playlist where each Song has its streamUrl set to the file:// URI. */
    private Playlist buildPlaylist() {
        ArrayList<Song> playable = new ArrayList<>();
        for (Song s : localSongs) {
            // Reuse the existing Song model; set streamUrl to the local file URI
            // so ExoPlayerService can load it directly via ExoPlayer.
            Song copy = new Song(
                    s.getTitle(), s.getArtist(), s.getGenre(),
                    s.getAlbum(), (int) s.getDuration(), s.getSongFile()
            );
            // Construct the proper content:// URI instead of file:// to bypass Scoped Storage limits
            try {
                long id = Long.parseLong(s.getVideoId());
                Uri contentUri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                copy.setStreamUrl(contentUri.toString());
            } catch (Exception e) {
                copy.setStreamUrl(Uri.fromFile(s.getSongFile()).toString());
            }
            playable.add(copy);
        }
        return new Playlist(playable, PLAYLIST_NAME);
    }

    // ── Visibility helpers ──────────────────────────────────────────────────

    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void showList() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void showEmpty() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
    }
}
