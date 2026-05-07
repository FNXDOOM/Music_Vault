package com.example.anujsharma.shuffler.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.anujsharma.shuffler.R;
import com.example.anujsharma.shuffler.models.Song;
import com.example.anujsharma.shuffler.utilities.Utilities;

import java.util.ArrayList;

/**
 * RecyclerView adapter for displaying local device songs in LocalMusicFragment.
 */
public class LocalMusicAdapter extends RecyclerView.Adapter<LocalMusicAdapter.ViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    private final Context context;
    private final ArrayList<Song> songs;
    private final OnSongClickListener listener;

    public LocalMusicAdapter(Context context, ArrayList<Song> songs, OnSongClickListener listener) {
        this.context  = context;
        this.songs    = songs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_local_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.tvTitle.setText(song.getTitle());

        String subtitle = song.getArtist();
        if (song.getAlbum() != null && !song.getAlbum().isEmpty()
                && !song.getAlbum().equals("Unknown Album")) {
            subtitle += "  •  " + song.getAlbum();
        }
        holder.tvSubtitle.setText(subtitle);

        // Duration: stored in seconds in the local-file constructor
        long durationMs = song.getDuration() * 1000L;
        holder.tvDuration.setText(Utilities.formatTime(durationMs));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSongClick(holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubtitle, tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle    = itemView.findViewById(R.id.tvLocalSongTitle);
            tvSubtitle = itemView.findViewById(R.id.tvLocalSongSubtitle);
            tvDuration = itemView.findViewById(R.id.tvLocalSongDuration);
        }
    }
}
