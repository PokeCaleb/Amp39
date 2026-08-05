package com.miku.player.ui.favorites;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.snackbar.Snackbar;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.databinding.ActivityFavoritesBinding;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.ui.playlists.AddToPlaylistBottomSheet;
import com.miku.player.ui.songs.SongsAdapter;
import com.miku.player.utils.ActivityTransitions;
import com.miku.player.utils.CoverSignatureManager;
import com.miku.player.utils.PlayerManager;
import com.miku.player.utils.ViewAnimUtils;
import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends AppCompatActivity
        implements SongsAdapter.OnSongClickListener {

    private ActivityFavoritesBinding binding;
    private SongsAdapter adapter;
    private FavoritesViewModel viewModel;
    private List<Song> currentSongs = new ArrayList<>();
    private Song pendingCoverSong;

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || pendingCoverSong == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                final Song songRef = pendingCoverSong;

                adapter.invalidateCover(this, songRef.getId());
                com.miku.player.utils.CoverWriteConfirm.ask(this, songRef.getPath(), writeToFile -> {
                    viewModel.updateSongCover(songRef, uri.toString(), writeToFile, (songId, localPath) -> {
                        adapter.invalidateCover(this, songId);
                        Song playing = PlayerManager.getInstance().getCurrentSongValue();
                        if (playing != null && playing.getId() == songId) {
                            songRef.setCustomCoverPath(localPath);
                            PlayerManager.getInstance().updateCurrentSongCover(songRef);
                        }
                    });
                });
            });

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
        observeCoverInvalidation();
    }

    /** FIX: refresca la carátula de cualquier canción de esta lista cambiada desde otra pantalla. */
    private void observeCoverInvalidation() {
        com.miku.player.utils.CoverSignatureManager.getInstance(this).getInvalidationEvent()
                .observe(this, songId -> {
                    if (songId != null) adapter.notifyCoverChanged(songId);
                });
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
            ActivityTransitions.forward(this);
            return;
        }
        List<Song> adapterList = adapter.getCurrentList();
        List<Song> playQueue = (adapterList != null && !adapterList.isEmpty())
                ? new ArrayList<>(adapterList) : new ArrayList<>(currentSongs);
        pm.playSong(song, playQueue);
        startActivity(new Intent(this, PlayerActivity.class));
        ActivityTransitions.forward(this);
    }

    @Override
    public void onMoreClick(Song song, int position) {
        String[] options = {
            getString(R.string.add_to_playlist),
            getString(R.string.change_cover),
            "Cambiar nombre",
            "Eliminar canción"
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(song.getDisplayTitle())
                .setItems(options, (d, w) -> {
                    if (w == 0) AddToPlaylistBottomSheet.newInstance(song)
                            .show(getSupportFragmentManager(), "add");
                    else if (w == 1) { pendingCoverSong = song; coverPickerLauncher.launch("image/*"); }
                    else if (w == 2) showRenameDialog(song);
                    else showDeleteSongDialog(song);
                }).show();
    }

    private void showRenameDialog(Song song) {
        com.google.android.material.textfield.TextInputLayout inputLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Nombre de la canción");
        inputLayout.setBoxBackgroundMode(
                com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        android.widget.EditText editText =
                new com.google.android.material.textfield.TextInputEditText(inputLayout.getContext());
        editText.setText(song.getDisplayTitle());
        editText.selectAll();
        inputLayout.addView(editText);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(p, p / 2, p, 0);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Cambiar nombre")
                .setView(inputLayout)
                .setPositiveButton("Guardar", (d, w) -> {
                    String newName = editText.getText() != null
                            ? editText.getText().toString().trim() : "";
                    if (newName.isEmpty() || newName.equals(song.getDisplayTitle())) return;
                    viewModel.updateCustomTitle(song, newName, this);
                    syncPlayerManagerTitle(song, newName);
                })
                .setNeutralButton("Restablecer original", (d, w) -> {
                    viewModel.updateCustomTitle(song, null, this);
                    syncPlayerManagerTitle(song, null);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Ver nota equivalente en SongsActivity. */
    private void syncPlayerManagerTitle(Song song, String newTitle) {
        PlayerManager pm = PlayerManager.getInstance();
        Song playing = pm.getCurrentSongValue();
        if (playing != null && playing.getId() == song.getId()) {
            song.setCustomTitle(newTitle);
            pm.updateCurrentSongMetadata(song);
        }
    }

    private void showDeleteSongDialog(Song song) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar canción")
                .setMessage("¿Qué deseas hacer con \"" + song.getDisplayTitle() + "\"?")
                .setPositiveButton("Eliminar archivo permanentemente", (d, w) -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("⚠ Eliminar archivo")
                            .setMessage("Esto borrará el archivo de audio del dispositivo. Esta acción no se puede deshacer.")
                            .setPositiveButton("Eliminar permanentemente", (d2, w2) ->
                                    performDeleteSong(song, true))
                            .setNegativeButton("Cancelar", null)
                            .show();
                })
                .setNeutralButton("Quitar de la app", (d, w) -> performDeleteSong(song, false))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void performDeleteSong(Song song, boolean deleteFile) {
        Song playing = PlayerManager.getInstance().getCurrentSongValue();
        if (playing != null && playing.getId() == song.getId()) {
            PlayerManager.getInstance().playNext();
        }
        viewModel.deleteSong(song, deleteFile, success -> {
            String msg = deleteFile
                    ? (success ? "Archivo eliminado" : "Eliminado de la app (no se pudo borrar el archivo)")
                    : "Canción quitada de la app";
            Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
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
        binding.miniPlayer.setOnClickListener(v -> {
            startActivity(new Intent(this, PlayerActivity.class));
            ActivityTransitions.forward(this);
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
            if (binding != null
                    && binding.miniPlayer.getVisibility() == android.view.View.VISIBLE) {
                long dur = PlayerManager.getInstance().getDuration();
                long pos = PlayerManager.getInstance().getPositionMs();
                if (dur > 0) binding.miniPlayerProgress.setProgress((int)((pos * 1000L) / dur));
            }
            miniProgressHandler.postDelayed(this, 500);
        }
    };

    private void startMiniProgressUpdater() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        miniProgressHandler.post(miniProgressRunnable);
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
