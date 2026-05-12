package com.miku.player.ui.playlists;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.miku.player.R;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.databinding.ActivityPlaylistsBinding;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.utils.PlayerManager;
import java.util.ArrayList;

public class PlaylistsActivity extends AppCompatActivity
        implements PlaylistsAdapter.OnPlaylistClickListener {

    private ActivityPlaylistsBinding binding;
    private PlaylistsAdapter adapter;
    private PlaylistsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewModel = new ViewModelProvider(this).get(PlaylistsViewModel.class);
        setupAdapter();
        observeViewModel();
        observeMiniPlayer();

        binding.fabCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
    }

    private void setupAdapter() {
        adapter = new PlaylistsAdapter(this);
        binding.rvPlaylists.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        binding.rvPlaylists.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getPlaylists().observe(this, playlists -> {
            adapter.submitList(playlists != null ? playlists : new ArrayList<>());
            binding.rvPlaylists.setVisibility(View.VISIBLE);
        });
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
            Object src = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(song);
            com.bumptech.glide.Glide.with(this).load(src)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .error(R.drawable.ic_default_album_art)
                    .into(binding.ivMiniCover);
        });
        pm.getIsPlaying().observe(this, playing ->
                binding.btnMiniPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));
        binding.miniPlayer.setOnClickListener(v -> {
            if (pm.getCurrentSongValue() != null) startActivity(new Intent(this, PlayerActivity.class));
        });
        binding.btnMiniPlayPause.setOnClickListener(v -> pm.togglePlayPause());
        binding.btnMiniNext.setOnClickListener(v -> pm.playNext());
        binding.btnMiniPrevious.setOnClickListener(v -> pm.playPrevious());
    }

    private void showCreatePlaylistDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(getString(R.string.new_playlist_hint));
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        EditText editText = new TextInputEditText(inputLayout.getContext());
        inputLayout.addView(editText);
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(padding, padding / 2, padding, 0);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.create_playlist))
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    String name = editText.getText() != null ? editText.getText().toString().trim() : "";
                    if (!name.isEmpty()) viewModel.createPlaylist(name);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public void onPlaylistClick(PlaylistWithSongs playlistWithSongs) {
        Intent intent = new Intent(this, PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlistWithSongs.playlist.getId());
        startActivity(intent);
    }

    @Override
    public void onMoreClick(PlaylistWithSongs playlistWithSongs) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(playlistWithSongs.playlist.getName())
                .setItems(new String[]{getString(R.string.delete_playlist)}, (dialog, which) -> {
                    if (which == 0) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("¿Eliminar lista?")
                                .setMessage("Se eliminará \"" + playlistWithSongs.playlist.getName() + "\"")
                                .setPositiveButton("Eliminar", (d, w) -> viewModel.deletePlaylist(playlistWithSongs.playlist))
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                }).show();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() { super.onDestroy(); binding = null; }
}
