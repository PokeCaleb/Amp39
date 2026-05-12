package com.miku.player.ui.favorites;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.databinding.ActivityFavoritesBinding;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.ui.songs.SongsAdapter;
import com.miku.player.utils.CoverSignatureManager;
import com.miku.player.utils.PlayerManager;
import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends AppCompatActivity
        implements SongsAdapter.OnSongClickListener {

    private ActivityFavoritesBinding binding;
    private SongsAdapter adapter;
    private FavoritesViewModel viewModel;
    private List<Song> currentSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFavoritesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Favoritos");
        }

        viewModel = new ViewModelProvider(this).get(FavoritesViewModel.class);

        setupAdapter();
        observeViewModel();
        observeCurrentSong();
        observeMiniPlayer();
    }

    private void setupAdapter() {
        adapter = new SongsAdapter(this);

        // When the heart is tapped: remove from favorites.
        // Do NOT call toggleFavoriteOptimistic — the list is immutable and
        // mutation fails silently. Instead just write to DB; Room emits a new
        // list without this song which DiffUtil removes with animation.
        adapter.setFavoriteListener((song, newState) -> viewModel.setFavorite(song, newState));

        binding.rvFavorites.setAdapter(adapter);
        binding.rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFavorites.setHasFixedSize(true);

        Song playing = PlayerManager.getInstance().getCurrentSongValue();
        if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
    }

    private void observeViewModel() {
        viewModel.getFavorites().observe(this, songs -> {
            currentSongs = songs != null ? songs : new ArrayList<>();
            // Use submitList but with a short-circuit: if the size changed (song
            // added/removed) we call notifyDataSetChanged via a fresh list for
            // instant rendering instead of waiting for DiffUtil's async dispatch.
            adapter.submitList(new ArrayList<>(currentSongs));
            Song playing = PlayerManager.getInstance().getCurrentSongValue();
            if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
            boolean empty = currentSongs.isEmpty();
            binding.rvFavorites.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
    }

    private void observeCurrentSong() {
        PlayerManager.getInstance().getCurrentSong().observe(this, song -> {
            long id = song != null ? song.getId() : -1;
            adapter.setCurrentlyPlayingId(id);
        });
    }

    @Override
    public void onSongClick(Song song, int position) {
        PlayerManager pm = PlayerManager.getInstance();
        Song currentlyPlaying = pm.getCurrentSongValue();
        if (currentlyPlaying != null && currentlyPlaying.getId() == song.getId()) {
            startActivity(new Intent(this, PlayerActivity.class));
            return;
        }
        List<Song> adapterList = adapter.getCurrentList();
        List<Song> playQueue = (adapterList != null && !adapterList.isEmpty())
                ? new ArrayList<>(adapterList) : new ArrayList<>(currentSongs);
        pm.playSong(song, playQueue);
        startActivity(new Intent(this, PlayerActivity.class));
    }

    @Override
    public void onMoreClick(Song song, int position) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(song.getTitle())
                .setItems(new String[]{"Quitar de favoritos"}, (d, w) ->
                        viewModel.removeFavorite(song))
                .show();
    }

    private void observeMiniPlayer() {
        PlayerManager pm = PlayerManager.getInstance();
        pm.getCurrentSong().observe(this, song -> {
            if (song == null) { binding.miniPlayer.setVisibility(View.GONE); return; }
            binding.miniPlayer.setVisibility(View.VISIBLE);
            binding.tvMiniTitle.setText(song.getTitle());
            binding.tvMiniArtist.setText(song.getArtist());
            com.bumptech.glide.Glide.with(this).clear(binding.ivMiniCover);
            binding.ivMiniCover.setImageResource(R.drawable.ic_default_album_art);
            Object src = SongsAdapter.resolveCoverSource(song);
            com.bumptech.glide.Glide.with(this).load(src)
                    .signature(CoverSignatureManager.getInstance(this)
                            .signatureFor(song.getId()))
                    .error(R.drawable.ic_default_album_art)
                    .into(binding.ivMiniCover);
        });
        pm.getIsPlaying().observe(this, playing ->
                binding.btnMiniPlayPause.setImageResource(
                        playing ? R.drawable.ic_pause : R.drawable.ic_play));
        binding.miniPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerActivity.class)));
        binding.btnMiniPlayPause.setOnClickListener(v -> pm.togglePlayPause());
        binding.btnMiniNext.setOnClickListener(v -> pm.playNext());
        binding.btnMiniPrevious.setOnClickListener(v -> pm.playPrevious());
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() { super.onDestroy(); binding = null; }
}
