package com.example.anujsharma.shuffler.backgroundTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;

import java.util.concurrent.ExecutionException;

public class GetColorPaletteFromImageUrl extends AsyncTask<String, Void, Palette> {

    private final String TAG = "TAG";
    private final Context context;
    private final PaletteCallback paletteCallback;

    public GetColorPaletteFromImageUrl(Context context, PaletteCallback paletteCallback) {
        this.context = context;
        this.paletteCallback = paletteCallback;
    }

    @Override
    protected Palette doInBackground(String... strings) {
        String url = strings[0];
        Bitmap theBitmap = null;
        try {
            theBitmap = Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, e.getMessage() != null ? e.getMessage() : "error");
        }

        if (theBitmap != null) return Palette.from(theBitmap).generate();
        return null;
    }

    @Override
    protected void onPostExecute(Palette palette) {
        super.onPostExecute(palette);
        paletteCallback.onPostExecute(palette);
    }

    public interface PaletteCallback {
        void onPostExecute(Palette palette);
    }
}
