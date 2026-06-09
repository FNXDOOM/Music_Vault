package com.example.anujsharma.shuffler.fragments;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.example.anujsharma.shuffler.R;
import com.example.anujsharma.shuffler.activities.MainActivity;
import com.example.anujsharma.shuffler.adapters.SearchSongRecyclerViewAdapter;
import com.example.anujsharma.shuffler.models.Playlist;
import com.example.anujsharma.shuffler.models.Song;
import com.example.anujsharma.shuffler.utilities.Constants;
import com.example.anujsharma.shuffler.utilities.YouTubeInAppClient;

import java.util.ArrayList;

public class HomeFragment extends Fragment {

    private Context context;
    private RecyclerView rvHomeTrending;
    private SearchSongRecyclerViewAdapter trendingAdapter;
    private ProgressBar progressBar;
    private RelativeLayout rlHomeError;
    private ArrayList<Song> trendingSongs = new ArrayList<>();
    private YouTubeInAppClient youTubeInAppClient;

    public HomeFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getActivity();
        youTubeInAppClient = new YouTubeInAppClient();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((MainActivity) getActivity()).modifyBottomLayout(0);
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initialise(view);
        fetchTrendingSongs();
        return view;
    }

    private void initialise(View view) {
        rvHomeTrending = view.findViewById(R.id.rvHomeTrending);
        progressBar = view.findViewById(R.id.home_progressbar);
        rlHomeError = view.findViewById(R.id.rlHomeError);

        trendingAdapter = new SearchSongRecyclerViewAdapter(context,
                new SearchSongRecyclerViewAdapter.ItemClickListener() {
                    @Override
                    public void onItemClick(View v, int position, int check) {
                        if (check == Constants.EACH_SONG_LAYOUT_CLICKED) {
                            Playlist playlist = new Playlist(trendingSongs, "trending");
                            ((MainActivity) getActivity()).playSongInMainActivity(position, playlist);
                        }
                    }
                });

        rvHomeTrending.setLayoutManager(new LinearLayoutManager(context));
        rvHomeTrending.setAdapter(trendingAdapter);
    }

    private void fetchTrendingSongs() {
        // Don't re-fetch if we already have results (tab switches re-create the view)
        if (!trendingSongs.isEmpty()) {
            showSongs(trendingSongs);
            return;
        }
        showProgressBar();
        new Thread(() -> {
            try {
                ArrayList<Song> fetched = youTubeInAppClient.searchSongs("trending music", 20);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (fetched.isEmpty()) showError();
                    else showSongs(fetched);
                });
            } catch (Exception e) {
                android.util.Log.e("HomeFragment", "In-app YouTube trending error", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::showError);
                }
            }
        }).start();
    }

    private void showSongs(ArrayList<Song> list) {
        trendingSongs.clear();
        trendingSongs.addAll(list);
        trendingAdapter.changeSongData(trendingSongs);
        trendingAdapter.changeUserData(new ArrayList<>());
        trendingAdapter.changePlaylistData(new ArrayList<>());
        showRecyclerView();
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        rvHomeTrending.setVisibility(View.GONE);
        rlHomeError.setVisibility(View.GONE);
    }

    private void showRecyclerView() {
        progressBar.setVisibility(View.GONE);
        rvHomeTrending.setVisibility(View.VISIBLE);
        rlHomeError.setVisibility(View.GONE);
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        rvHomeTrending.setVisibility(View.GONE);
        rlHomeError.setVisibility(View.VISIBLE);
    }
}
