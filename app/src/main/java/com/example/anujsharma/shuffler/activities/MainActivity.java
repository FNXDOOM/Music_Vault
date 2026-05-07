package com.example.anujsharma.shuffler.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.ServiceCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.anujsharma.shuffler.R;
import com.example.anujsharma.shuffler.dao.TracksDao;
import com.example.anujsharma.shuffler.fragments.HomeFragment;
import com.example.anujsharma.shuffler.fragments.SearchFragment;
import com.example.anujsharma.shuffler.fragments.YourLibraryFragment;
import com.example.anujsharma.shuffler.models.Playlist;
import com.example.anujsharma.shuffler.models.Song;
import com.example.anujsharma.shuffler.services.ExoPlayerService;
import com.example.anujsharma.shuffler.utilities.Constants;
import com.example.anujsharma.shuffler.utilities.FisherYatesShuffle;
import com.example.anujsharma.shuffler.utilities.SharedPreference;
import com.example.anujsharma.shuffler.utilities.YouTubeInAppClient;
import com.example.anujsharma.shuffler.volley.RequestCallback;
import com.example.anujsharma.shuffler.volley.Urls;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements RequestCallback {

    public static final String TAG = "TAG";
    public static final String HOME_FRAGMENT = "homeFragment";
    public static final String SEARCH_FRAGMENT = "searchFragment";
    public static final String YOUR_LIBRARY_FRAGMENT = "yourLibraryFragment";

    // FIX: ExoPlayerService is the single playback engine for the app now.
    private ExoPlayerService exoSrv;
    private boolean exoBound = false;
    private ServiceConnection exoConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ExoPlayerService.ExoBinder binder = (ExoPlayerService.ExoBinder) service;
            exoSrv = binder.getService();
            exoBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            exoBound = false;
            exoSrv = null;
        }
    };
    private final int REQUEST_PERMS_CODE = 1;
    SharedPreference pref;
    private boolean initHomeFragment, initSearchFragment, initYourLibraryFragment;
    private HomeFragment homeFragment;
    private SearchFragment searchFragment;
    private YourLibraryFragment yourLibraryFragment;
    //    private MediaPlayer mediaPlayer;
    private Context context;
    private ProgressBar mainSongLoader;
    private TextView tvHome, tvSearch, tvMyProfile, tvSongName;
    private ImageView ivPlay, ivNext, ivFullView;
    private TracksDao tracksDao;
    private YouTubeInAppClient youTubeInAppClient;
    private int currentSongPosition;
    private Playlist currentPlaylist;
    private Intent exoIntent;
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(2);
    private boolean streamErrorReceiverRegistered = false;
    private final android.content.BroadcastReceiver streamErrorReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ExoPlayerService.ACTION_STREAM_ERROR.equals(intent.getAction())) return;
            int failedPosition = intent.getIntExtra(ExoPlayerService.EXTRA_ERROR_POSITION, -1);
            retryPlaybackForPosition(failedPosition);
        }
    };
    //binding
    private static boolean crashLoggerInstalled = false;

    @Override
    protected void onStart() {
        super.onStart();
        // Bind ExoPlayerService so play/pause/next in mini-player work
        Intent eIntent = new Intent(this, ExoPlayerService.class);
        bindService(eIntent, exoConnection, Context.BIND_AUTO_CREATE);
        if (!streamErrorReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ExoPlayerService.ACTION_STREAM_ERROR);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(streamErrorReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(streamErrorReceiver, filter);
            }
            streamErrorReceiverRegistered = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashLogger();
        Log.d(TAG, "MainActivity.onCreate start");
        setContentView(R.layout.activity_main);

        initialise();
        Log.d(TAG, "MainActivity.initialise complete");
        initialiseListeners();
        Log.d(TAG, "MainActivity.initialiseListeners complete");
        if (hasPermissons()) {
            Log.d(TAG, "MainActivity.permissions granted");
            mainStuff();
        } else {
            Log.d(TAG, "MainActivity.permissions missing, requesting");
            requestPermissions();
        }

        Playlist savedPlaylist = pref.getCurrentPlaylist();
        Log.d(TAG, "MainActivity.savedPlaylist exists=" + (savedPlaylist != null));
        if (savedPlaylist != null && savedPlaylist.getSongs() != null && !savedPlaylist.getSongs().isEmpty()) {
            exoIntent = new Intent(this, ExoPlayerService.class);
            exoIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, pref.getCurrentPlayingSongPosition());
            Log.d(TAG, "MainActivity.starting ExoPlayerService songs=" + savedPlaylist.getSongs().size()
                    + " position=" + pref.getCurrentPlayingSongPosition());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(exoIntent);
            } else {
                startService(exoIntent);
            }
        }
        Log.d(TAG, "MainActivity.onCreate end");
    }

    private void installCrashLogger() {
        if (crashLoggerInstalled) return;
        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "FATAL app crash on thread=" + thread.getName(), throwable);
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
        crashLoggerInstalled = true;
    }

    public void playSongInMainActivity(int songPosition, Playlist playlist) {
        if (playlist.getSongs() == null || playlist.getSongs().size() == 0) {
            Toast.makeText(context, "Unable to play this Playlist.", Toast.LENGTH_SHORT).show();
            return;
        }
        Song song = playlist.getSongs().get(songPosition);
        currentSongPosition = songPosition;
        pref.setCurrentPlayingSong(song.getId());
        pref.setCurrentPlaylist(playlist);
        pref.setCurrentPlayingSongPosition(songPosition);
        tvSongName.setText(song.getTitle());
        this.currentPlaylist = playlist;
        ArrayList<Integer> shuffleList = new ArrayList<>();
        for (int i = 0; i < playlist.getSongs().size(); i++) shuffleList.add(i);
        pref.setCurrentPlaylistShuffleArray(shuffleList);
        pref.setCurrentShuffleSongPosition(0);
        FisherYatesShuffle.updateShuffleList(context, songPosition);

        // Step 1: Start the service immediately while app is in foreground
        exoIntent = new Intent(this, ExoPlayerService.class);
        exoIntent.setAction(ExoPlayerService.ACTION_PLAY);
        exoIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, songPosition);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(exoIntent);
        } else {
            startService(exoIntent);
        }

        String existingStreamUrl = song.getStreamUrl();
        // SoundCloud path: track already has stream_url, only normalize with client_id if needed.
        if (existingStreamUrl != null && !existingStreamUrl.isEmpty()) {
            String playableUrl = normalizeStreamUrl(existingStreamUrl);
            song.setStreamUrl(playableUrl);
            for (Song s : playlist.getSongs()) {
                if (s.getId() == song.getId()) s.setStreamUrl(playableUrl);
            }
            pref.setCurrentPlaylist(playlist);

            Intent updateIntent = new Intent(this, ExoPlayerService.class);
            updateIntent.setAction(ExoPlayerService.ACTION_UPDATE_STREAM);
            updateIntent.putExtra(ExoPlayerService.EXTRA_STREAM_URL, playableUrl);
            updateIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, songPosition);
            startService(updateIntent);
            ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            return;
        }

        // YouTube path: fetch stream URL from backend.
        mainSongLoader.setVisibility(android.view.View.VISIBLE);
        String videoId = song.getVideoId();
        final Playlist finalPlaylist = playlist;
        networkExecutor.execute(() -> fetchStreamAndPlay(videoId, song, finalPlaylist, songPosition));
    }

    private String normalizeStreamUrl(String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) return streamUrl;
        if (streamUrl.contains("soundcloud.com")
                && streamUrl.contains("/stream")
                && !streamUrl.contains("client_id=")) {
            return streamUrl + (streamUrl.contains("?") ? "&" : "?") + "client_id=" + Urls.CLIENT_ID;
        }
        return streamUrl;
    }

    /**
     * Play a local device song (file:// URI). No network fetch needed —
     * the stream URLs are already set to file:// URIs by LocalMusicFragment.buildPlaylist().
     * We send ACTION_UPDATE_STREAM immediately with the first song's file URI so ExoPlayerService
     * loads the entire playlist at once.
     */
    public void playSongInMainActivityLocal(int songPosition, Playlist playlist) {
        if (playlist.getSongs() == null || playlist.getSongs().isEmpty()) {
            Toast.makeText(context, "No songs to play.", Toast.LENGTH_SHORT).show();
            return;
        }
        Song song = playlist.getSongs().get(songPosition);
        currentSongPosition = songPosition;
        pref.setCurrentPlaylist(playlist);
        pref.setCurrentPlayingSongPosition(songPosition);
        tvSongName.setText(song.getTitle());
        this.currentPlaylist = playlist;

        // Start ExoPlayerService as a foreground service
        exoIntent = new Intent(this, ExoPlayerService.class);
        exoIntent.setAction(ExoPlayerService.ACTION_PLAY);
        exoIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, songPosition);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(exoIntent);
        } else {
            startService(exoIntent);
        }

        // File URIs are already present — immediately deliver them to the service
        String fileUri = song.getStreamUrl(); // set to file:// URI by LocalMusicFragment
        Intent updateIntent = new Intent(this, ExoPlayerService.class);
        updateIntent.setAction(ExoPlayerService.ACTION_UPDATE_STREAM);
        updateIntent.putExtra(ExoPlayerService.EXTRA_STREAM_URL, fileUri);
        updateIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, songPosition);
        startService(updateIntent);

        // Update UI immediately (no loading spinner needed for local files)
        ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
    }

    private void fetchStreamAndPlay(String videoId, Song song, Playlist playlist, int songPosition) {
        try {
            String streamUrl = youTubeInAppClient.resolveBestAudioStreamUrl(videoId);
            song.setStreamUrl(streamUrl);
            for (Song s : playlist.getSongs()) {
                if (s.getVideoId().equals(videoId)) s.setStreamUrl(streamUrl);
            }
            pref.setCurrentPlaylist(playlist);

            Intent updateIntent = new Intent(this, ExoPlayerService.class);
            updateIntent.setAction(ExoPlayerService.ACTION_UPDATE_STREAM);
            updateIntent.putExtra(ExoPlayerService.EXTRA_STREAM_URL, streamUrl);
            updateIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, songPosition);
            startService(updateIntent);
            prefetchNextSongStream(playlist, songPosition);

            runOnUiThread(() -> mainSongLoader.setVisibility(android.view.View.GONE));
        } catch (Exception e) {
            Log.e(TAG, "fetchStreamAndPlay error", e);
            runOnUiThread(() -> {
                mainSongLoader.setVisibility(android.view.View.GONE);
                Toast.makeText(context, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void retryPlaybackForPosition(int failedPosition) {
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || currentPlaylist.getSongs().isEmpty()) return;
        if (failedPosition < 0 || failedPosition >= currentPlaylist.getSongs().size()) return;

        Song failedSong = currentPlaylist.getSongs().get(failedPosition);
        String videoId = failedSong.getVideoId();
        if (videoId == null || videoId.isEmpty()) return;

        networkExecutor.execute(() -> {
            try {
                youTubeInAppClient.invalidateStream(videoId);
                String refreshedUrl = youTubeInAppClient.resolveBestAudioStreamUrl(videoId);
                failedSong.setStreamUrl(refreshedUrl);
                pref.setCurrentPlaylist(currentPlaylist);

                Intent updateIntent = new Intent(this, ExoPlayerService.class);
                updateIntent.setAction(ExoPlayerService.ACTION_UPDATE_STREAM);
                updateIntent.putExtra(ExoPlayerService.EXTRA_STREAM_URL, refreshedUrl);
                updateIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, failedPosition);
                startService(updateIntent);
                prefetchNextSongStream(currentPlaylist, failedPosition);
            } catch (Exception e) {
                Log.e(TAG, "retryPlaybackForPosition failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Stream refresh failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void prefetchNextSongStream(Playlist playlist, int currentPosition) {
        if (playlist == null || playlist.getSongs() == null || playlist.getSongs().isEmpty()) return;
        int nextPosition = currentPosition + 1;
        if (nextPosition >= playlist.getSongs().size()) return;
        Song nextSong = playlist.getSongs().get(nextPosition);
        if (nextSong.getStreamUrl() != null && !nextSong.getStreamUrl().isEmpty()) return;
        String nextVideoId = nextSong.getVideoId();
        if (nextVideoId == null || nextVideoId.isEmpty()) return;

        networkExecutor.execute(() -> {
            try {
                String nextStream = youTubeInAppClient.resolveBestAudioStreamUrl(nextVideoId);
                nextSong.setStreamUrl(nextStream);
                pref.setCurrentPlaylist(playlist);
            } catch (Exception ignored) {
                // best-effort prefetch
            }
        });
    }

    public void updatePlaylistInMainActivity(Playlist playlist) {
        if (playlist.getPlaylistId() == currentPlaylist.getPlaylistId()) {
            currentPlaylist = playlist;
            pref.setCurrentPlaylist(playlist);
        }
    }

    private void initialiseListeners() {

        ivPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // FIX: control ExoPlayerService — that's where audio actually plays
                if (exoBound && exoSrv != null) {
                    if (exoSrv.isPlaying()) {
                        exoSrv.pausePlayer();
                        ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_play));
                    } else {
                        exoSrv.resumePlayer();
                        ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
                    }
                }
            }
        });

        ivNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // FIX: control ExoPlayerService for next track
                if (exoBound && exoSrv != null) {
                    exoSrv.playNext();
                }
            }
        });

        ivFullView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPlaylist != null
                        && currentPlaylist.getSongs() != null
                        && !currentPlaylist.getSongs().isEmpty()) {
                    int safePosition = currentSongPosition;
                    if (safePosition < 0 || safePosition >= currentPlaylist.getSongs().size()) {
                        safePosition = 0;
                    }
                    Intent intent = new Intent(MainActivity.this, ViewSongActivity.class);
                    intent.putExtra(Constants.PLAYLIST_MODEL_KEY, currentPlaylist);
                    intent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, safePosition);
                    // FIX: read play state from ExoPlayerService, not MusicService
                    intent.putExtra(Constants.IS_PLAYING, exoBound && exoSrv != null && exoSrv.isPlaying());
                    MainActivity.this.startActivity(intent);
                }
            }
        });

        tvSongName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPlaylist != null
                        && currentPlaylist.getSongs() != null
                        && !currentPlaylist.getSongs().isEmpty()) {
                    int safePosition = currentSongPosition;
                    if (safePosition < 0 || safePosition >= currentPlaylist.getSongs().size()) {
                        safePosition = 0;
                    }
                    Intent intent = new Intent(MainActivity.this, ViewSongActivity.class);
                    intent.putExtra(Constants.PLAYLIST_MODEL_KEY, currentPlaylist);
                    intent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, safePosition);
                    // FIX: read play state from ExoPlayerService
                    intent.putExtra(Constants.IS_PLAYING, exoBound && exoSrv != null && exoSrv.isPlaying());
                    MainActivity.this.startActivity(intent);
                }
            }
        });
    }

    public void initialise() {
        context = getApplicationContext();
        pref = new SharedPreference(context);
        tracksDao = new TracksDao(context, this);
        youTubeInAppClient = new YouTubeInAppClient();

        tvHome = findViewById(R.id.xtvHome);
        tvSearch = findViewById(R.id.xtvSearch);
        tvMyProfile = findViewById(R.id.xtvMyProfile);
        tvSongName = findViewById(R.id.tvSongName);
        mainSongLoader = findViewById(R.id.pbLoadSong);
        tvSongName.setSelected(true);
        ivFullView = findViewById(R.id.ivUpArrow);
        ivNext = findViewById(R.id.ivPlayNext);
        ivPlay = findViewById(R.id.ivPlaySong);
        currentPlaylist = pref.getCurrentPlaylist();
        currentSongPosition = pref.getCurrentPlayingSongPosition();
        if (currentPlaylist != null
                && currentPlaylist.getSongs() != null
                && !currentPlaylist.getSongs().isEmpty()) {
            if (currentSongPosition < 0 || currentSongPosition >= currentPlaylist.getSongs().size()) {
                currentSongPosition = 0;
            }
            Song currentSong = currentPlaylist.getSongs().get(currentSongPosition);
            tvSongName.setText(currentSong.getTitle());
        }
    }


    public void modifyBottomLayout(int position) {
        switch (position) {
            case 0:
                tvHome.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_home_selected, 0, 0);
                tvHome.setTextColor(ContextCompat.getColor(context, R.color.white));
                tvSearch.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_search, 0, 0);
                tvSearch.setTextColor(ContextCompat.getColor(context, R.color.color_unselected));
                tvMyProfile.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_library, 0, 0);
                tvMyProfile.setTextColor(ContextCompat.getColor(context, R.color.color_unselected));
                break;
            case 1:
                tvHome.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_home, 0, 0);
                tvHome.setTextColor(ContextCompat.getColor(context, R.color.color_unselected));
                tvSearch.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_search_selected, 0, 0);
                tvSearch.setTextColor(ContextCompat.getColor(context, R.color.white));
                tvMyProfile.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_library, 0, 0);
                tvMyProfile.setTextColor(ContextCompat.getColor(context, R.color.color_unselected));
                break;
            case 2:
                tvHome.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_home, 0, 0);
                tvHome.setTextColor(ContextCompat.getColor(context, R.color.color_unselected));
                tvSearch.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_search, 0, 0);
                tvSearch.setTextColor(ContextCompat.getColor(context, R.color.color_unselected));
                tvMyProfile.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_library_selected, 0, 0);
                tvMyProfile.setTextColor(ContextCompat.getColor(context, R.color.white));
                break;
        }
    }

    public void homeTabClicked(View view) {
        if (!initHomeFragment) {
            initHomeFragment = true;
            homeFragment = new HomeFragment();
        }
        addFragmentToMainFrameContainer(homeFragment, HOME_FRAGMENT);
    }

    public void searchTabClicked(View view) {
        if (!initSearchFragment) {
            initSearchFragment = true;
            searchFragment = new SearchFragment();
        }
        addFragmentToMainFrameContainer(searchFragment, SEARCH_FRAGMENT);
    }

    public void yourLibraryClicked(View view) {
        if (!initYourLibraryFragment) {
            initYourLibraryFragment = true;
            yourLibraryFragment = new YourLibraryFragment();
        }
        addFragmentToMainFrameContainer(yourLibraryFragment, YOUR_LIBRARY_FRAGMENT);
    }


    public void addFragmentToMainFrameContainer(Fragment fragment, String TAG) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.mainFrameContainer);
        if (currentFragment != null && currentFragment.getClass().equals(fragment.getClass())) {

        } else {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.mainFrameContainer, fragment).addToBackStack(null).commit();
        }
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    public void mainStuff() {
        HomeFragment fragment = new HomeFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.mainFrameContainer, fragment, HOME_FRAGMENT)
                .addToBackStack(null).commit();
    }

    @SuppressLint("WrongConstant")
    private boolean hasPermissons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_AUDIO instead
            return ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{android.Manifest.permission.READ_MEDIA_AUDIO,
                            android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_PERMS_CODE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMS_CODE);
        } else {
            // Below M permissions are granted at install time
            mainStuff();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMS_CODE) {
            // As long as audio permission is granted, proceed (notifications are optional)
            boolean audioGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if ((permissions[i].equals(android.Manifest.permission.READ_MEDIA_AUDIO) ||
                        permissions[i].equals(android.Manifest.permission.READ_EXTERNAL_STORAGE))
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    audioGranted = true;
                }
            }
            if (audioGranted) {
                mainStuff();
            } else {
                Toast.makeText(this, "Audio permission denied. Music won't be available.", Toast.LENGTH_SHORT).show();
                // Still load the app — search/streaming doesn't need storage
                mainStuff();
            }
        }
    }


    @Override
    public void onListRequestSuccessful(ArrayList list, int check, boolean status) {

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (currentPlaylist != null
                && currentPlaylist.getSongs() != null
                && !currentPlaylist.getSongs().isEmpty()
                && currentSongPosition >= 0
                && currentSongPosition < currentPlaylist.getSongs().size()) {
            pref.setCurrentPlayingSong(currentPlaylist.getSongs().get(currentSongPosition).getId());
        }
        // FIX: also unbind ExoPlayerService
        if (exoBound) {
            unbindService(exoConnection);
            exoBound = false;
        }
        if (streamErrorReceiverRegistered) {
            unregisterReceiver(streamErrorReceiver);
            streamErrorReceiverRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onObjectRequestSuccessful(Object object, int check, boolean status) {
    }
}
