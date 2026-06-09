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

import java.util.concurrent.atomic.AtomicBoolean;

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
    // Broadcast sent when the current song index changes (so MainActivity mini-player can sync)
    public static final String ACTION_SONG_CHANGED = "com.example.anujsharma.shuffler.ACTION_SONG_CHANGED";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_ERROR_POSITION = "error_position";
    public static final String EXTRA_SONG_POSITION = "song_position";

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
    // AtomicBoolean so rapid double-swipes don't race past the flag check.
    private final AtomicBoolean suppressNextTransitionBroadcast = new AtomicBoolean(false);
    // Ordered list of stream URLs matching currentPlaylist.getSongs() order.
    // Needed so seekToNextMediaItem / seekToPreviousMediaItem work across the full queue.
    private final java.util.List<String> queueUrls = new java.util.ArrayList<>();

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
        if (player == null) return;
        // If the queue has more than one item already loaded, use ExoPlayer's built-in next.
        if (player.getMediaItemCount() > 1 && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
            return;
        }
        // Otherwise ask MainActivity to resolve the next stream (single-item queue mode).
        int nextPos = currentPosition + 1;
        if (currentPlaylist != null && currentPlaylist.getSongs() != null
                && nextPos < currentPlaylist.getSongs().size()) {
            Intent i = new Intent(ACTION_SONG_CHANGED);
            i.putExtra(EXTRA_SONG_POSITION, nextPos);
            sendBroadcast(i);
        }
    }

    public void playPrev() {
        if (player == null) return;
        if (player.getMediaItemCount() > 1 && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
            return;
        }
        int prevPos = currentPosition - 1;
        if (currentPlaylist != null && currentPlaylist.getSongs() != null && prevPos >= 0) {
            Intent i = new Intent(ACTION_SONG_CHANGED);
            i.putExtra(EXTRA_SONG_POSITION, prevPos);
            sendBroadcast(i);
        }
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        pref = new SharedPreference(this);

        androidx.media3.common.AudioAttributes audioAttributes = new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .build();
        player.setVolume(1.0f);
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (player == null) return;
                // getAndSet(false) atomically reads-and-clears, preventing the
                // race where two rapid loads both arm the flag but only one clear fires.
                if (suppressNextTransitionBroadcast.getAndSet(false)) {
                    return;
                }
                int idx = player.getCurrentMediaItemIndex();
                currentPosition = idx;
                pref.setCurrentPlayingSongPosition(idx);
                // Broadcast so MainActivity mini-player updates its title
                Intent songChanged = new Intent(ACTION_SONG_CHANGED);
                songChanged.putExtra(EXTRA_SONG_POSITION, idx);
                sendBroadcast(songChanged);
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (player == null) return;
                Log.e(TAG, "ExoPlayer error: " + error.getMessage(), error);
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
            if (playlist == null) playlist = pref.getCurrentPlaylist();
            int position = intent.getIntExtra(Constants.CURRENT_PLAYING_SONG_POSITION, 0);
            Log.d(TAG, "ACTION_UPDATE_STREAM url=" + (streamUrl != null ? streamUrl.substring(0, Math.min(80, streamUrl.length())) : "null"));
            if (streamUrl != null && !streamUrl.isEmpty()) {
                currentPlaylist = playlist;
                currentPosition = position;
                if (playlist != null) pref.setCurrentPlaylist(playlist);

                // Build MediaItem without explicit MIME type so ExoPlayer can sniff the format
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(streamUrl))
                        .build();

                // Suppress the spurious onMediaItemTransition(index=0) that ExoPlayer
                // fires when we replace the media item.  We've already set currentPosition
                // above so the broadcast would carry the wrong index.
                suppressNextTransitionBroadcast.set(true);
                player.stop();
                player.clearMediaItems();
                player.setMediaItem(mediaItem);
                player.prepare();
                player.setPlayWhenReady(true);
                Log.d(TAG, "ExoPlayer prepared and set to play, position=" + position);
            }
        } else if (ACTION_NEXT.equals(action)) {
            // Notification "next" button
            playNext();
        } else if (ACTION_PREVIOUS.equals(action)) {
            // Notification "previous" button
            playPrev();
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            // Notification play/pause button — toggle playback
            if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
        } else {
            // ACTION_PLAY or initial start — store playlist, stream URL arrives via UPDATE_STREAM
            Playlist playlist = intent.getParcelableExtra(Constants.PLAYLIST_MODEL_KEY);
            if (playlist == null) playlist = pref.getCurrentPlaylist();
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
