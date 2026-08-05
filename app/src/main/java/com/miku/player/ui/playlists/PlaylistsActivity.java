package com.miku.player.ui.playlists;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.miku.player.R;
import com.miku.player.data.model.Playlist;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.databinding.ActivityPlaylistsBinding;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.utils.ActivityTransitions;
import com.miku.player.utils.PlayerManager;
import com.miku.player.utils.ViewAnimUtils;
import java.util.ArrayList;
import java.util.List;

public class PlaylistsActivity extends AppCompatActivity
        implements PlaylistsAdapter.OnPlaylistClickListener {

    private ActivityPlaylistsBinding binding;
    private PlaylistsAdapter adapter;
    private PlaylistsViewModel viewModel;
    private Playlist pendingCoverPlaylist;
    // True mientras el usuario está arrastrando una playlist — bloquea que el
    // observer de LiveData pise el reordenamiento en curso (mismo patrón que
    // ya usa PlaylistDetailActivity para reordenar canciones dentro de una lista).
    private boolean isDragging = false;

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && pendingCoverPlaylist != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    viewModel.updatePlaylistCover(pendingCoverPlaylist, uri.toString());
                    pendingCoverPlaylist = null;
                }
            });

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
        setupDragAndDrop();
    }

    /**
     * Arrastrar y soltar para reordenar playlists: mantener presionada una
     * tarjeta la activa (isLongPressDragEnabled = true, sin necesidad de un
     * modo de reordenar separado ni un ícono de agarre), y se puede soltar en
     * cualquier posición de la grilla (arriba/abajo/izquierda/derecha).
     */
    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public boolean isLongPressDragEnabled() { return true; }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos   = to.getBindingAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false;
                isDragging = true;
                adapter.moveItem(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                isDragging = false;
                // Persistir el orden final
                List<Long> orderedIds = new ArrayList<>();
                for (PlaylistWithSongs pws : adapter.getItems()) orderedIds.add(pws.playlist.getId());
                viewModel.reorderPlaylists(orderedIds);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(binding.rvPlaylists);
    }

    private void observeViewModel() {
        viewModel.getPlaylists().observe(this, playlists -> {
            // No pisar la lista mientras el usuario está arrastrando una tarjeta
            if (isDragging) return;
            adapter.setItems(playlists != null ? playlists : new ArrayList<>());
            binding.rvPlaylists.setVisibility(View.VISIBLE);
        });
    }

    private void observeMiniPlayer() {
        PlayerManager pm = PlayerManager.getInstance();
        pm.getCurrentSong().observe(this, song -> {
            if (song == null) { com.miku.player.utils.ViewAnimUtils.animateMiniPlayerVisibility(binding.miniPlayer, false); return; }
            com.miku.player.utils.ViewAnimUtils.animateMiniPlayerVisibility(binding.miniPlayer, true);
            binding.tvMiniTitle.setText(song.getDisplayTitle()); // FIX: mostraba el título original, no el personalizado
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
            if (pm.getCurrentSongValue() != null) {
                startActivity(new Intent(this, PlayerActivity.class));
                ActivityTransitions.forward(this);
            }
        });
        binding.btnMiniPlayPause.setOnClickListener(v -> { ViewAnimUtils.pulse(v); pm.togglePlayPause(); });
        binding.btnMiniNext.setOnClickListener(v -> { ViewAnimUtils.pulse(v); pm.playNext(); });
        binding.btnMiniPrevious.setOnClickListener(v -> { ViewAnimUtils.pulse(v); pm.playPrevious(); });
        startMiniProgressUpdater();
    }

    private final android.os.Handler miniProgressHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable miniProgressRunnable = new Runnable() {
        @Override public void run() {
            if (binding != null && binding.miniPlayer.getVisibility() == View.VISIBLE) {
                long dur = PlayerManager.getInstance().getDuration();
                long pos = PlayerManager.getInstance().getPositionMs();
                if (dur > 0) binding.miniPlayerProgress.setProgress((int) ((pos * 1000L) / dur));
            }
            miniProgressHandler.postDelayed(this, 500);
        }
    };

    private void startMiniProgressUpdater() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        miniProgressHandler.post(miniProgressRunnable);
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
        ActivityTransitions.forward(this);
    }

    @Override
    public void onMoreClick(PlaylistWithSongs playlistWithSongs) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(playlistWithSongs.playlist.getName())
                .setItems(new String[]{"Cambiar portada", getString(R.string.delete_playlist)},
                        (dialog, which) -> {
                    if (which == 0) {
                        pendingCoverPlaylist = playlistWithSongs.playlist;
                        coverPickerLauncher.launch("image/*");
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("¿Eliminar lista?")
                                .setMessage("Se eliminará \"" + playlistWithSongs.playlist.getName() + "\"")
                                .setPositiveButton("Eliminar",
                                        (d, w) -> viewModel.deletePlaylist(playlistWithSongs.playlist))
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                }).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        ActivityTransitions.back(this);
        return true;
    }

    @Override
    protected void onDestroy() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        super.onDestroy();
        binding = null;
    }
}
