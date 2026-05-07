package com.example.anujsharma.shuffler.backgroundTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.example.anujsharma.shuffler.models.Song;
import com.example.anujsharma.shuffler.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class GetMergedBitmap extends AsyncTask<List<Song>, Void, Bitmap> {

    private final Context context;
    private final MergedBitmapCallback mergedBitmapCallback;

    public GetMergedBitmap(Context context, MergedBitmapCallback mergedBitmapCallback) {
        this.context = context;
        this.mergedBitmapCallback = mergedBitmapCallback;
    }

    @Override
    protected Bitmap doInBackground(List<Song>[] lists) {
        List<Song> songs = lists[0];
        List<Bitmap> bitmaps = new ArrayList<>();

        int loopCount = (songs.size() == 1) ? 1 : 4;

        for (int i = 0; i < loopCount; i++) {
            try {
                Bitmap bitmap = Glide.with(context)
                        .asBitmap()
                        .load(Utilities.getMediumArtworkUrl(songs.get(i).getSongArtwork()))
                        .submit()
                        .get();
                bitmaps.add(bitmap);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("TAG", e.getMessage() != null ? e.getMessage() : "error");
                bitmaps.add(null);
            }
        }

        if (loopCount == 4) return Utilities.mergeThemAll(bitmaps);
        else return bitmaps.get(0);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        mergedBitmapCallback.onBitmapReady(bitmap);
    }

    public interface MergedBitmapCallback {
        void onBitmapReady(Bitmap bitmap);
    }
}
