package com.example.anujsharma.shuffler.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.media3.common.C;
import androidx.palette.graphics.Palette;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.anujsharma.shuffler.R;
import com.example.anujsharma.shuffler.adapters.SeeAllViewPagerAdapter;
import com.example.anujsharma.shuffler.backgroundTasks.GetColorPaletteFromImageUrl;
import com.example.anujsharma.shuffler.models.Playlist;
import com.example.anujsharma.shuffler.models.Song;
import com.example.anujsharma.shuffler.services.ExoPlayerService;
import com.example.anujsharma.shuffler.utilities.Constants;
import com.example.anujsharma.shuffler.utilities.DialogBoxes;
import com.example.anujsharma.shuffler.utilities.SharedPreference;
import com.example.anujsharma.shuffler.utilities.Utilities;
import com.example.anujsharma.shuffler.utilities.ZoomOutPageTransformer;

import java.util.List;

public class ViewSongActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "TAG";
    private Context context;
    private ImageView ivBackButton, ivShowPlaylist, ivAddToLibrary, ivMenu, ivShuffle, ivPrevious, ivPlay, ivNext, ivRepeat;
    private TextView tvPlaylistName, tvSongName, tvArtistName, tvCurrentTime, tvDuration;
    private SeekBar seekBar;
    private ViewPager viewPager;
    private RelativeLayout relativeLayout;
    private List<Song> songs;
    private Playlist currentPlaylist;
    private Song currentPlayingSong;
    private SeeAllViewPagerAdapter seeAllViewPagerAdapter;
    private int currentPlayingPosition;
    private GradientDrawable gd;
    // FIX: replaced MusicService with ExoPlayerService
    private ExoPlayerService exoService;
    private boolean musicBound;
    private Intent playIntent;
    private boolean isPlaying;
    private SharedPreference pref;

    private long getDurationMs(Song song) {
        if (song == null) return 0L;
        long d = song.getDuration();
        // Local songs are stored in seconds; online songs are typically in ms.
        return d < 1000 ? d * 1000L : d;
    }

    // FIX: Player.Listener registered on ExoPlayer to drive seekbar, replacing MusicService.ViewMusicInterface
    private final Player.Listener exoPlayerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlayingNow) {
            if (isPlayingNow) {
                ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            } else {
                ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_play));
            }
        }

        @Override
        public void onMediaItemTransition(androidx.media3.common.MediaItem mediaItem, int reason) {
            // When ExoPlayer moves to a new media item, sync the ViewPager position
            if (exoService != null) {
                int newIndex = exoService.getPlayer().getCurrentMediaItemIndex();
                if (newIndex != currentPlayingPosition) {
                    currentPlayingPosition = newIndex;
                    viewPager.setCurrentItem(newIndex, true);
                    if (songs != null && newIndex < songs.size()) {
                        Song song = songs.get(newIndex);
                        tvSongName.setText(song.getTitle());
                        tvArtistName.setText(song.getArtist());
                        long durationMs = getDurationMs(song);
                        tvDuration.setText(Utilities.formatTime(durationMs));
                        tvCurrentTime.setText(Utilities.formatTime(0));
                        seekBar.setProgress(0);
                        seekBar.setMax((int) (durationMs / 100));
                        changeBackground(song.getSongArtwork());
                    }
                }
            }
        }
    };

    // FIX: Runnable to poll ExoPlayer position and update seekbar
    private final Runnable seekBarUpdater = new Runnable() {
        @Override
        public void run() {
            if (exoService != null && exoService.getPlayer() != null) {
                long duration = exoService.getPlayer().getDuration();
                if (duration > 0) {
                    seekBar.setMax((int) (duration / 100));
                    tvDuration.setText(Utilities.formatTime(duration));
                }
                long pos = exoService.getPlayer().getCurrentPosition();
                if (pos < 0) pos = 0;
                seekBar.setProgress((int) (pos / 100));
                tvCurrentTime.setText(Utilities.formatTime(pos));
            }
            seekBar.postDelayed(this, 100);
        }
    };

    private ServiceConnection musicConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // FIX: cast to ExoBinder instead of MusicBinder
            ExoPlayerService.ExoBinder binder = (ExoPlayerService.ExoBinder) service;
            exoService = binder.getService();
            musicBound = true;

            // FIX: register Player.Listener on ExoPlayer to receive playback state updates
            if (exoService.getPlayer() != null) {
                exoService.getPlayer().addListener(exoPlayerListener);
            }

            // Sync initial play/pause icon
            if (exoService.isPlaying()) {
                ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            } else {
                ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_play));
            }

            // Sync repeat/shuffle behavior to player whenever service binds.
            exoService.getPlayer().setShuffleModeEnabled(pref.getIsShuffleOn());
            exoService.getPlayer().setRepeatMode(
                    pref.getIsRepeatOn() ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF
            );

            // If player has no media items (common after app/service recreation),
            // rehydrate from playlist so controls work immediately.
            if (exoService.getPlayer() != null
                    && exoService.getPlayer().getMediaItemCount() == 0
                    && currentPlaylist != null
                    && songs != null
                    && !songs.isEmpty()) {
                int safeIndex = currentPlayingPosition;
                if (safeIndex < 0 || safeIndex >= songs.size()) safeIndex = 0;
                String streamUrl = songs.get(safeIndex).getStreamUrl();
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    Intent updateIntent = new Intent(ViewSongActivity.this, ExoPlayerService.class);
                    updateIntent.setAction(ExoPlayerService.ACTION_UPDATE_STREAM);
                    updateIntent.putExtra(ExoPlayerService.EXTRA_STREAM_URL, streamUrl);
                    updateIntent.putExtra(Constants.PLAYLIST_MODEL_KEY, currentPlaylist);
                    updateIntent.putExtra(Constants.CURRENT_PLAYING_SONG_POSITION, safeIndex);
                    startService(updateIntent);
                }
            }

            // Start polling seekbar position
            seekBar.post(seekBarUpdater);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
            exoService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_song);

        Intent intent = getIntent();
        currentPlaylist = intent.getParcelableExtra(Constants.PLAYLIST_MODEL_KEY);
        currentPlayingPosition = intent.getIntExtra(Constants.CURRENT_PLAYING_SONG_POSITION, 0);
        isPlaying = intent.getBooleanExtra(Constants.IS_PLAYING, true);
        if (currentPlaylist == null || currentPlaylist.getSongs() == null || currentPlaylist.getSongs().isEmpty()) {
            Toast.makeText(this, "No song available to open player.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        songs = currentPlaylist.getSongs();
        if (currentPlayingPosition < 0 || currentPlayingPosition >= songs.size()) {
            currentPlayingPosition = 0;
        }
        currentPlayingSong = songs.get(currentPlayingPosition);

        if (playIntent == null) {
            // FIX: bind to ExoPlayerService instead of MusicService
            playIntent = new Intent(getBaseContext(), ExoPlayerService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }

        initialize();
        initializeListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop seekbar polling
        seekBar.removeCallbacks(seekBarUpdater);
        // Remove ExoPlayer listener
        if (exoService != null && exoService.getPlayer() != null) {
            exoService.getPlayer().removeListener(exoPlayerListener);
        }
        // Do NOT stopService here — ExoPlayerService must keep running for background playback
        // FIX: only unbind if actually bound
        if (musicBound) {
            musicBound = false;
            unbindService(musicConnection);
        }
        exoService = null;
    }

    public void changeBackground(String url) {
        GetColorPaletteFromImageUrl getColorPaletteFromImageUrl = new GetColorPaletteFromImageUrl(context, new GetColorPaletteFromImageUrl.PaletteCallback() {
            @Override
            public void onPostExecute(Palette palette) {
                changeBackground(Utilities.getBackgroundColorFromPalette(palette));
            }
        });
        getColorPaletteFromImageUrl.execute(url);
    }

    public void changeBackground(int color) {
        gd.setColors(new int[]{color, context.getResources().getColor(R.color.bottom_gradient)});
        relativeLayout.setBackground(gd);
    }

    private void initializeListeners() {
        ivBackButton.setOnClickListener(this);
        ivShowPlaylist.setOnClickListener(this);
        ivAddToLibrary.setOnClickListener(this);
        ivMenu.setOnClickListener(this);
        ivShuffle.setOnClickListener(this);
        ivPrevious.setOnClickListener(this);
        ivPlay.setOnClickListener(this);
        ivNext.setOnClickListener(this);
        ivRepeat.setOnClickListener(this);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                // FIX: guard null — service may not yet be bound during swipe
                if (exoService == null) return;
                if (position > currentPlayingPosition) {
                    // FIX: use exoService.playNext() instead of musicService.playNext()
                    exoService.playNext();
                } else if (position < currentPlayingPosition) {
                    // FIX: use exoService.playPrev() instead of musicService.playPrev()
                    exoService.playPrev();
                }
                currentPlayingPosition = position;
                changeBackground(songs.get(currentPlayingPosition).getSongArtwork());
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (exoService != null && b && exoService.getPlayer() != null) {
                    // FIX: use ExoPlayer.seekTo() instead of musicService.seek()
                    exoService.getPlayer().seekTo((long) i * 100);
                }
                tvCurrentTime.setText(Utilities.formatTime((long) i * 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // FIX: use exoService.pausePlayer() instead of musicService.pausePlayer()
                if (exoService != null) exoService.pausePlayer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // FIX: use exoService.resumePlayer() instead of musicService.go()
                if (exoService != null) exoService.resumePlayer();
            }
        });
    }

    private void initialize() {
        context = this;
        pref = new SharedPreference(context);
        ivBackButton = findViewById(R.id.ivBackButton);
        ivShowPlaylist = findViewById(R.id.ivShowPlaylist);
        ivAddToLibrary = findViewById(R.id.ivAddToLibrary);
        ivMenu = findViewById(R.id.ivShowSongMenu);
        ivShuffle = findViewById(R.id.ivShuffle);
        ivPrevious = findViewById(R.id.ivPrevious);
        ivPlay = findViewById(R.id.ivPlay);
        ivNext = findViewById(R.id.ivNext);
        ivRepeat = findViewById(R.id.ivRepeat);
        tvPlaylistName = findViewById(R.id.tvPlayingFromPlaylist);
        tvSongName = findViewById(R.id.tvSongName);
        tvArtistName = findViewById(R.id.tvArtistName);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDuration = findViewById(R.id.tvDuration);
        seekBar = findViewById(R.id.seekBar);
        viewPager = findViewById(R.id.viewSongViewPager);
        relativeLayout = findViewById(R.id.rlViewSongLayout);
        gd = new GradientDrawable();
        gd.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        relativeLayout.setBackground(gd);

        if (songs.get(currentPlayingPosition).getSongArtwork() != null)
            changeBackground(songs.get(currentPlayingPosition).getSongArtwork());
        else
            changeBackground(0xFF616261);

        tvSongName.setText(songs.get(currentPlayingPosition).getTitle());
        tvSongName.setSelected(true);
        tvArtistName.setText(songs.get(currentPlayingPosition).getArtist());
        long durationMs = getDurationMs(songs.get(currentPlayingPosition));
        tvDuration.setText(Utilities.formatTime(durationMs));
        tvPlaylistName.setText(currentPlaylist.getTitle());

        if (isPlaying) ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
        else ivPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_play));
        seekBar.setMax((int) (durationMs / 100));

        // Req 3.7: shuffle/repeat toggles continue to persist via SharedPreference
        if (pref.getIsRepeatOn())
            ivRepeat.setColorFilter(context.getResources().getColor(R.color.colorAccent));
        if (pref.getIsShuffleOn())
            ivShuffle.setColorFilter(context.getResources().getColor(R.color.colorAccent));

        viewPager.setPageTransformer(false, new ZoomOutPageTransformer());
        seeAllViewPagerAdapter = new SeeAllViewPagerAdapter(context, songs);
        viewPager.setAdapter(seeAllViewPagerAdapter);
        viewPager.setCurrentItem(currentPlayingPosition);
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.ivBackButton) {
            finish();
        } else if (viewId == R.id.ivShowPlaylist) {
        } else if (viewId == R.id.ivAddToLibrary) {
            View mView = getLayoutInflater().inflate(R.layout.dialog_add_song_to_playlist, null);
            View createPlaylistView = getLayoutInflater().inflate(R.layout.create_playlist_dialog_layout, null);
            DialogBoxes.showAddSongToPlaylistDialog(context, mView, createPlaylistView, songs.get(currentPlayingPosition));
        } else if (viewId == R.id.ivShowSongMenu) {
        } else if (viewId == R.id.ivShuffle) {
            // Req 3.7: shuffle toggle persists via SharedPreference
            if (pref.getIsShuffleOn()) {
                ivShuffle.setColorFilter(context.getResources().getColor(R.color.white));
                pref.setIsShuffleOn(false);
                if (exoService != null && exoService.getPlayer() != null) {
                    exoService.getPlayer().setShuffleModeEnabled(false);
                }
            } else {
                ivShuffle.setColorFilter(context.getResources().getColor(R.color.colorAccent));
                pref.setIsShuffleOn(true);
                if (exoService != null && exoService.getPlayer() != null) {
                    exoService.getPlayer().setShuffleModeEnabled(true);
                }
            }
        } else if (viewId == R.id.ivPrevious) {
            if (exoService == null) {
                Intent i = new Intent(this, ExoPlayerService.class);
                i.setAction(ExoPlayerService.ACTION_PREVIOUS);
                startService(i);
                return;
            }
            if (songs != null && currentPlayingPosition - 1 >= 0) {
                currentPlayingPosition--;
                viewPager.setCurrentItem(currentPlayingPosition, true);
            }
            // FIX: use exoService.playPrev() instead of musicService.playPrev()
            exoService.playPrev();
        } else if (viewId == R.id.ivPlay) {
            if (exoService == null) {
                Intent i = new Intent(this, ExoPlayerService.class);
                i.setAction(ExoPlayerService.ACTION_PLAY_PAUSE);
                startService(i);
                return;
            }
            // FIX: use exoService.resumePlayer() / exoService.pausePlayer() instead of musicService.go() / musicService.pausePlayer()
            if (exoService.isPlaying()) {
                exoService.pausePlayer();
            } else {
                exoService.resumePlayer();
            }
        } else if (viewId == R.id.ivNext) {
            if (exoService == null) {
                Intent i = new Intent(this, ExoPlayerService.class);
                i.setAction(ExoPlayerService.ACTION_NEXT);
                startService(i);
                return;
            }
            if (songs != null && currentPlayingPosition + 1 < songs.size()) {
                currentPlayingPosition++;
                viewPager.setCurrentItem(currentPlayingPosition, true);
            }
            // FIX: use exoService.playNext() instead of musicService.playNext()
            exoService.playNext();
        } else if (viewId == R.id.ivRepeat) {
            // Req 3.7: repeat toggle persists via SharedPreference
            if (pref.getIsRepeatOn()) {
                ivRepeat.setColorFilter(context.getResources().getColor(R.color.white));
                pref.setIsRepeatOn(false);
                if (exoService != null && exoService.getPlayer() != null) {
                    exoService.getPlayer().setRepeatMode(Player.REPEAT_MODE_OFF);
                }
            } else {
                ivRepeat.setColorFilter(context.getResources().getColor(R.color.colorAccent));
                pref.setIsRepeatOn(true);
                if (exoService != null && exoService.getPlayer() != null) {
                    exoService.getPlayer().setRepeatMode(Player.REPEAT_MODE_ONE);
                }
            }
        }
    }
}
