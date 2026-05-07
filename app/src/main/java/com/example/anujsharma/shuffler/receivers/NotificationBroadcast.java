package com.example.anujsharma.shuffler.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.anujsharma.shuffler.services.ExoPlayerService;
import com.example.anujsharma.shuffler.utilities.Constants;

import java.util.Objects;

/**
 * Created by anuj5 on 20-01-2018.
 *
 * FIX (Bug 1.3): Re-wired to target ExoPlayerService directly via startService().
 * Previously routed to MainActivity.musicSrv (MusicService), which is not the active
 * audio engine, so notification buttons had no effect on the audio playing in ExoPlayerService.
 */

public class NotificationBroadcast extends BroadcastReceiver {

    private static final String TAG = "NotificationBroadcast";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Constants.CLICK_NEXT)) {
            // FIX: send ACTION_NEXT directly to ExoPlayerService
            Intent serviceIntent = new Intent(context, ExoPlayerService.class);
            serviceIntent.setAction(ExoPlayerService.ACTION_NEXT);
            context.startService(serviceIntent);
        } else if (Objects.equals(intent.getAction(), Constants.CLICK_PLAY_PAUSE)) {
            // FIX: send ACTION_PLAY_PAUSE directly to ExoPlayerService
            Intent serviceIntent = new Intent(context, ExoPlayerService.class);
            serviceIntent.setAction(ExoPlayerService.ACTION_PLAY_PAUSE);
            context.startService(serviceIntent);
        } else if (Objects.equals(intent.getAction(), Constants.CLICK_PREVIOUS)) {
            // FIX: send ACTION_PREVIOUS directly to ExoPlayerService
            Intent serviceIntent = new Intent(context, ExoPlayerService.class);
            serviceIntent.setAction(ExoPlayerService.ACTION_PREVIOUS);
            context.startService(serviceIntent);
        }
    }
}
