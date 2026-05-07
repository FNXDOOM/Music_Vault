package com.example.anujsharma.shuffler.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerNotificationManager;

import com.example.anujsharma.shuffler.R;
import com.example.anujsharma.shuffler.activities.MainActivity;
import com.example.anujsharma.shuffler.models.Playlist;
import com.example.anujsharma.shuffler.utilities.Constants;
import com.example.anujsharma.shuffler.utilities.SharedPreference;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerService extends Service {

    private static final String TAG = "ExoPlayerService";
    public static final String ACTION_PLAY = "com.example.anujsharma.shuffler.ACTION_PLAY";
    public static final String ACTION_UPDATE_STREAM = "com.example.anujsharma.shuffler.ACTION_UPDATE_STREAM";
    public static final String ACTION_NEXT = "com.example.anujsharma.shuffler.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "com.example.anujsharma.shuffler.ACTION_PREVIOUS";
    public static final String ACTION_PLAY_PAUSE = "com.example.anujsharma.shuffler.ACTION_PLAY_PAUSE";
    public static final String ACTION_STREAM_ERROR = "com.example.anujsharma.shuffler.ACTION_STREAM_ERROR";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_ERROR_POSITION = "error_position";

    // FIX: expose a Binder so MainActivity can call play/pause/isPlaying directly on ExoPlayer
    private final IBinder binder = new ExoBinder();

    public class ExoBinder extends Binder {
        public ExoPlayerService getService() {
            return ExoPlayerService.this;
        }
    }

    private ExoPlayer player;
    private PlayerNotificationManager playerNotificationManager;
    private MediaSession mediaSession;
    private SharedPreference pref;
    private Playlist currentPlaylist;
    private int currentPosition;

    // FIX: expose the underlying ExoPlayer instance so ViewSongActivity can call seekTo() and addListener()
    public ExoPlayer getPlayer() {
        return player;
    }

    // FIX: control methods called directly by MainActivity's play/pause/next buttons
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void pausePlayer() {
        if (player != null) player.pause();
    }

    public void resumePlayer() {
        if (player != null) player.play();
    }

    public void playNext() {
        if (player != null) player.seekToNextMediaItem();
    }

    public void playPrev() {
        if (player != null) player.seekToPreviousMediaItem();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        pref = new SharedPreference(this);

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (player == null) return;
                int idx = player.getCurrentMediaItemIndex();
                currentPosition = idx;
                pref.setCurrentPlayingSongPosition(idx);
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (player == null) return;
                int idx = player.getCurrentMediaItemIndex();
                Intent i = new Intent(ACTION_STREAM_ERROR);
                i.putExtra(EXTRA_ERROR_POSITION, idx);
                sendBroadcast(i);
            }
        });

        mediaSession = new MediaSession.Builder(this, player).build();

        playerNotificationManager = new PlayerNotificationManager.Builder(
                this,
                Constants.NOTIFICATION_SONG_ID,
                Constants.NOTIFICATION_SONG_CHANNEL_ID
        )
                .setChannelNameResourceId(R.string.playback_channel_name)
                .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @Override
                    public CharSequence getCurrentContentTitle(androidx.media3.common.Player p) {
                        if (currentPlaylist != null && currentPlaylist.getSongs() != null) {
                            int idx = p.getCurrentMediaItemIndex();
                            if (idx < currentPlaylist.getSongs().size())
                                return currentPlaylist.getSongs().get(idx).getTitle();
                        }
                        return "Shuffler";
                    }

                    @Nullable
                    @Override
                    public PendingIntent createCurrentContentIntent(androidx.media3.common.Player p) {
                        Intent intent = new Intent(ExoPlayerService.this, MainActivity.class);
                        return PendingIntent.getActivity(ExoPlayerService.this, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    }

                    @Nullable
                    @Override
                    public CharSequence getCurrentContentText(androidx.media3.common.Player p) {
                        if (currentPlaylist != null && currentPlaylist.getSongs() != null) {
                            int idx = p.getCurrentMediaItemIndex();
                            if (idx < currentPlaylist.getSongs().size())
                                return currentPlaylist.getSongs().get(idx).getArtist();
                        }
                        return "";
                    }

                    @Nullable
                    @Override
                    public Bitmap getCurrentLargeIcon(androidx.media3.common.Player p,
                                                      PlayerNotificationManager.BitmapCallback callback) {
                        return BitmapFactory.decodeResource(getResources(), R.drawable.ic_headphones);
                    }
                })
                .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                        startForeground(notificationId, notification);
                    }

                    @Override
                    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                        stopSelf();
                    }
                })
                .build();

        playerNotificationManager.setPlayer(player);
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionCompatToken());

        startForeground(Constants.NOTIFICATION_SONG_ID, buildPlaceholderNotification());
    }

    private Notification buildPlaceholderNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    Constants.NOTIFICATION_SONG_CHANNEL_ID,
                    getString(R.string.playback_channel_name),
                    android.app.NotificationManager.IMPORTANCE_LOW);
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
        return new androidx.core.app.NotificationCompat.Builder(this, Constants.NOTIFICATION_SONG_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_headphones)
                .setContentTitle("Shuffler")
                .setContentText("Loading…")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand intent=" + (intent != null));
        if (intent == null) return START_STICKY;

        String action = intent.getAction();

        if (ACTION_UPDATE_STREAM.equals(action)) {
            String streamUrl = intent.getStringExtra(EXTRA_STREAM_URL);
            Playlist playlist = intent.getParcelableExtra(Constants.PLAYLIST_MODEL_KEY);
            int position = intent.getIntExtra(Constants.CURRENT_PLAYING_SONG_POSITION, 0);
            Log.d(TAG, "ACTION_UPDATE_STREAM url=" + (streamUrl != null ? streamUrl.substring(0, Math.min(80, streamUrl.length())) : "null"));
            if (streamUrl != null && !streamUrl.isEmpty()) {
                currentPlaylist = playlist;
                currentPosition = position;
                if (playlist != null) pref.setCurrentPlaylist(playlist);

                // Build MediaItem with explicit MIME type for audio/webm opus streams
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(streamUrl))
                        .setMimeType("audio/webm")
                        .build();

                player.stop();
                player.clearMediaItems();
                player.addMediaItem(mediaItem);
                player.prepare();
                player.setPlayWhenReady(true);
                Log.d(TAG, "ExoPlayer prepared and set to play");
            }
        } else if (ACTION_NEXT.equals(action)) {
            // Notification "next" button — advance to next track
            if (player != null) player.seekToNextMediaItem();
        } else if (ACTION_PREVIOUS.equals(action)) {
            // Notification "previous" button — go to previous track
            if (player != null) player.seekToPreviousMediaItem();
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            // Notification play/pause button — toggle playback
            if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
        } else {
            // ACTION_PLAY or initial start — store playlist, stream URL arrives via UPDATE_STREAM
            Playlist playlist = intent.getParcelableExtra(Constants.PLAYLIST_MODEL_KEY);
            if (playlist != null) {
                currentPlaylist = playlist;
                currentPosition = intent.getIntExtra(Constants.CURRENT_PLAYING_SONG_POSITION, 0);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (playerNotificationManager != null) playerNotificationManager.setPlayer(null);
        if (mediaSession != null) mediaSession.release();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    // FIX: return binder — was returning null, making bind useless
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
