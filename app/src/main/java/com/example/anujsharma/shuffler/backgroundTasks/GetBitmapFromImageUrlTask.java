package com.example.anujsharma.shuffler.backgroundTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.bumptech.glide.Glide;

import java.util.concurrent.ExecutionException;

public class GetBitmapFromImageUrlTask extends AsyncTask<String, Void, Bitmap> {

    private static final String TAG = "GetBitmapImageUrlTag";
    private final Context context;
    private final BitmapFromUrlCallback bitmapFromUrlCallback;

    public GetBitmapFromImageUrlTask(Context context, BitmapFromUrlCallback bitmapFromUrlCallback) {
        this.context = context;
        this.bitmapFromUrlCallback = bitmapFromUrlCallback;
    }

    @Override
    protected Bitmap doInBackground(String... strings) {
        try {
            return Glide.with(context)
                    .asBitmap()
                    .load(strings[0])
                    .submit()
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, e.getMessage() != null ? e.getMessage() : "error");
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        bitmapFromUrlCallback.onBitmapFound(bitmap);
    }

    public interface BitmapFromUrlCallback {
        void onBitmapFound(Bitmap bitmap);
    }
}
