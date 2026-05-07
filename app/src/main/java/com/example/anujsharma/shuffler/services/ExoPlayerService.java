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
    public static final String EXTRA_STREAM_URL = "stream_url";

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
            // Stream URL is ready — load and play via ExoPlayer
            String streamUrl = intent.getStringExtra(EXTRA_STREAM_URL);
            Playlist playlist = intent.getParcelableExtra(Constants.PLAYLIST_MODEL_KEY);
            int position = intent.getIntExtra(Constants.CURRENT_PLAYING_SONG_POSITION, 0);
            if (streamUrl != null && !streamUrl.isEmpty()) {
                currentPlaylist = playlist;
                currentPosition = position;
                pref.setCurrentPlaylist(playlist);
                // Load all songs that have a stream URL so next/prev work across the playlist
                player.clearMediaItems();
                if (playlist != null && playlist.getSongs() != null) {
                    for (com.example.anujsharma.shuffler.models.Song s : playlist.getSongs()) {
                        String url = s.getStreamUrl();
                        if (url != null && !url.isEmpty()) {
                            // Use explicit MIME type so ExoPlayer handles audio/webm proxy streams correctly
                            player.addMediaItem(MediaItem.fromUri(Uri.parse(url)));
                        }
                    }
                }
                // If no other songs had stream URLs yet, just add the current one
                if (player.getMediaItemCount() == 0) {
                    player.addMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)));
                }
                player.prepare();
                int targetIndex = position;
                int itemCount = player.getMediaItemCount();
                if (itemCount <= 0) {
                    targetIndex = 0;
                } else {
                    if (targetIndex < 0) targetIndex = 0;
                    if (targetIndex >= itemCount) targetIndex = itemCount - 1;
                }
                currentPosition = targetIndex;
                pref.setCurrentPlayingSongPosition(targetIndex);
                player.seekTo(targetIndex, 0);
                player.setPlayWhenReady(true);
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
