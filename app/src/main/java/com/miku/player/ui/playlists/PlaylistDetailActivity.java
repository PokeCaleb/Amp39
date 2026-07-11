package com.miku.player.ui.playlists;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.miku.player.R;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.data.model.Song;
import com.miku.player.databinding.ActivityPlaylistDetailBinding;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.utils.PlayerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistDetailActivity extends AppCompatActivity
        implements PlaylistSongsAdapter.Listener {

    public static final String EXTRA_PLAYLIST_ID = "playlist_id";

    private ActivityPlaylistDetailBinding binding;
    private PlaylistDetailViewModel viewModel;
    private PlaylistSongsAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private long playlistId;
    private PlaylistWithSongs currentPlaylist;

    private boolean reorderMode = false;
    // True while ItemTouchHelper is actively dragging — blocks LiveData updates
    private boolean isDragging = false;
    private Song pendingCoverSong;

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && currentPlaylist != null)
                    viewModel.updatePlaylistCover(currentPlaylist.playlist, uri.toString());
            });

    /** Selector de carátula para una CANCIÓN puntual (distinto del de arriba, que es para la portada de la lista). */
    private final ActivityResultLauncher<String> songCoverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || pendingCoverSong == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                final Song songRef = pendingCoverSong;

                adapter.invalidateCover(this, songRef.getId());
                viewModel.updateSongCover(songRef, uri.toString(), (songId, localPath) -> {
                    adapter.invalidateCover(this, songId);
                    Song playing = PlayerManager.getInstance().getCurrentSongValue();
                    if (playing != null && playing.getId() == songId) {
                        songRef.setCustomCoverPath(localPath);
                        PlayerManager.getInstance().updateCurrentSongCover(songRef);
                    }
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playlistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1);
        if (playlistId == -1) { finish(); return; }

        // Do NOT call setSupportActionBar — it hijacks menu item clicks.
        // Handle navigation and menu directly on the toolbar object.
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this,
                new PlaylistDetailViewModelFactory(getApplication(), playlistId))
                .get(PlaylistDetailViewModel.class);

        setupAdapter();
        setupDragAndDrop();
        observeViewModel();
        setupButtons();
        setupMenu();
        observeCurrentSong();
        observeMiniPlayer();
        observeCoverInvalidation();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private void setupAdapter() {
        adapter = new PlaylistSongsAdapter(this);
        binding.rvPlaylistSongs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlaylistSongs.setAdapter(adapter);
        binding.rvPlaylistSongs.setHasFixedSize(false);

        Song playing = PlayerManager.getInstance().getCurrentSongValue();
        if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
    }

    // ── Drag & Drop ───────────────────────────────────────────────────────────

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos   = to.getBindingAdapterPosition();
                if (fromPos == RecyclerView.NO_ID || toPos == RecyclerView.NO_ID) return false;
                isDragging = true;
                adapter.moveItem(fromPos, toPos);   // mutates internal list + notifyItemMoved
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                isDragging = false;
                // Persist final order to DB
                List<Long> orderedIds = new ArrayList<>();
                for (Song s : adapter.getSongs()) orderedIds.add(s.getId());
                viewModel.reorderSongs(playlistId, orderedIds);
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.rvPlaylistSongs);
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getPlaylist().observe(this, pws -> {
            if (pws == null) return;
            currentPlaylist = pws;
            binding.tvPlaylistName.setText(pws.playlist.getName());

            List<Song> junctionSongs = pws.songs != null ? pws.songs : new ArrayList<>();
            Object coverSource = null;
            String sig = "default";
            if (pws.playlist.getCoverPath() != null) {
                coverSource = android.net.Uri.parse(pws.playlist.getCoverPath());
                sig = pws.playlist.getCoverPath();
            } else if (!junctionSongs.isEmpty()) {
                com.miku.player.data.model.Song first = junctionSongs.get(0);
                coverSource = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(first);
                sig = first.getId() + ":" + (first.getCustomCoverPath() != null
                        ? first.getCustomCoverPath() : "");
            }
            Glide.with(this).load(coverSource)
                    .signature(new com.bumptech.glide.signature.ObjectKey(sig))
                    .placeholder(R.drawable.ic_default_album_art)
                    .error(R.drawable.ic_default_album_art)
                    .centerCrop()
                    .into(binding.ivPlaylistHeader);
        });

        viewModel.getDirectSongs().observe(this, songs -> {
            // Never overwrite the adapter while the user is dragging
            if (isDragging) return;

            if (songs == null) songs = new ArrayList<>();
            int count = songs.size();
            binding.tvSongCount.setText(count == 1 ? "1 canción" : count + " canciones");

            adapter.setSongs(songs);   // replaces internal list and notifyDataSetChanged

            Song playing = PlayerManager.getInstance().getCurrentSongValue();
            if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());

            if (currentPlaylist != null) currentPlaylist.songs = songs;
            binding.btnPlayAll.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            binding.btnShuffleAll.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        });
    }

    // ── Buttons & menus ───────────────────────────────────────────────────────

    private void setupButtons() {
        binding.fabAddSongs.setOnClickListener(v ->
                AddSongsToPlaylistBottomSheet.newInstance(playlistId)
                        .show(getSupportFragmentManager(), "add_songs"));

        binding.btnPlayAll.setOnClickListener(v -> {
            List<Song> list = adapter.getSongs();
            if (!list.isEmpty()) {
                PlayerManager.getInstance().playSong(list.get(0), list);
                startActivity(new Intent(this, PlayerActivity.class));
            }
        });

        binding.btnShuffleAll.setOnClickListener(v -> {
            List<Song> shuffled = new ArrayList<>(adapter.getSongs());
            if (!shuffled.isEmpty()) {
                Collections.shuffle(shuffled);
                PlayerManager.getInstance().playSong(shuffled.get(0), shuffled);
                startActivity(new Intent(this, PlayerActivity.class));
            }
        });
    }

    private void setupMenu() {
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_rename)        { showRenameDialog();   return true; }
            if (id == R.id.action_delete)        { showDeleteDialog();   return true; }
            if (id == R.id.action_reorder)       { enterReorderMode();   return true; }
            if (id == R.id.action_change_cover)  { coverPickerLauncher.launch("image/*"); return true; }
            return false;
        });

        // Botón palomita para confirmar el reorden — visible solo en modo reorder
        binding.fabConfirmReorder.setOnClickListener(v -> exitReorderMode());
    }

    private void enterReorderMode() {
        reorderMode = true;
        adapter.setShowDragHandles(true);
        binding.fabAddSongs.setVisibility(View.GONE);
        binding.fabConfirmReorder.setVisibility(View.VISIBLE);
        com.google.android.material.snackbar.Snackbar.make(
                binding.getRoot(), "Arrastra ≡ para reordenar", 
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
    }

    private void exitReorderMode() {
        reorderMode = false;
        adapter.setShowDragHandles(false);
        binding.fabConfirmReorder.setVisibility(View.GONE);
        binding.fabAddSongs.setVisibility(View.VISIBLE);
        // Persistir el orden final
        List<Long> orderedIds = new ArrayList<>();
        for (Song s : adapter.getSongs()) orderedIds.add(s.getId());
        viewModel.reorderSongs(playlistId, orderedIds);
        com.google.android.material.snackbar.Snackbar.make(
                binding.getRoot(), "Orden guardado", 
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
    }

    private void showRenameDialog() {
        if (currentPlaylist == null) return;
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Nuevo nombre");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        EditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setText(currentPlaylist.playlist.getName());
        inputLayout.addView(editText);
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(p, p / 2, p, 0);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Renombrar lista")
                .setView(inputLayout)
                .setPositiveButton(getString(R.string.confirm), (d, w) -> {
                    String name = editText.getText() != null
                            ? editText.getText().toString().trim() : "";
                    if (!name.isEmpty()) viewModel.renamePlaylist(currentPlaylist.playlist, name);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showDeleteDialog() {
        if (currentPlaylist == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("¿Eliminar lista?")
                .setMessage("Se eliminará \"" + currentPlaylist.playlist.getName() + "\"")
                .setPositiveButton("Eliminar", (d, w) -> {
                    viewModel.deletePlaylist(currentPlaylist.playlist);
                    finish();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // ── PlaylistSongsAdapter.Listener ─────────────────────────────────────────

    @Override
    public void onSongClick(Song song, int position) {
        PlayerManager pm = PlayerManager.getInstance();
        Song currentlyPlaying = pm.getCurrentSongValue();
        if (currentlyPlaying != null && currentlyPlaying.getId() == song.getId()) {
            startActivity(new Intent(this, PlayerActivity.class));
            return;
        }
        List<Song> list = adapter.getSongs();
        if (!list.isEmpty()) {
            pm.playSong(song, new ArrayList<>(list));
            startActivity(new Intent(this, PlayerActivity.class));
        }
    }

    @Override
    public void onMoreClick(Song song, int position) {
        String[] options = {
            "Agregar a lista",
            "Cambiar carátula",
            "Cambiar nombre",
            "Eliminar canción",
            "Quitar de la lista"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(song.getDisplayTitle())
                .setItems(options, (d, w) -> {
                    if (w == 0) AddToPlaylistBottomSheet.newInstance(song)
                            .show(getSupportFragmentManager(), "add");
                    else if (w == 1) { pendingCoverSong = song; songCoverPickerLauncher.launch("image/*"); }
                    else if (w == 2) showSongRenameDialog(song);
                    else if (w == 3) showDeleteSongDialog(song);
                    else viewModel.removeSong(playlistId, song.getId());
                }).show();
    }

    private void showSongRenameDialog(Song song) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Nombre de la canción");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        EditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setText(song.getDisplayTitle());
        editText.selectAll();
        inputLayout.addView(editText);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(p, p / 2, p, 0);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Cambiar nombre")
                .setView(inputLayout)
                .setPositiveButton("Guardar", (d, w) -> {
                    String newName = editText.getText() != null
                            ? editText.getText().toString().trim() : "";
                    if (newName.isEmpty() || newName.equals(song.getDisplayTitle())) return;
                    viewModel.updateCustomTitle(song, newName, this);
                })
                .setNeutralButton("Restablecer original", (d, w) ->
                        viewModel.updateCustomTitle(song, null, this))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDeleteSongDialog(Song song) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar canción")
                .setMessage("¿Qué deseas hacer con \"" + song.getDisplayTitle() + "\"?")
                .setPositiveButton("Eliminar archivo permanentemente", (d, w) -> {
                    new MaterialAlertDialogBuilder(this)
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

    @Override
    public void onFavoriteClick(Song song, boolean newState) { viewModel.setFavorite(song, newState); }

    @Override
    public void onDragStart(RecyclerView.ViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }

    // ── Other observers ───────────────────────────────────────────────────────

    private void observeCurrentSong() {
        PlayerManager.getInstance().getCurrentSong().observe(this, song -> {
            long id = song != null ? song.getId() : -1;
            adapter.setCurrentlyPlayingId(id);
        });
    }

    /**
     * FIX: si la carátula de una canción cambia desde OTRA pantalla (por ejemplo
     * desde el reproductor, al estar escuchando una canción de esta misma lista),
     * antes esta lista nunca se enteraba — el cambio se veía en todas partes
     * menos aquí. CoverSignatureManager ahora emite un evento global cada vez
     * que se invalida una carátula, sin importar quién lo hizo.
     */
    private void observeCoverInvalidation() {
        com.miku.player.utils.CoverSignatureManager.getInstance(this).getInvalidationEvent()
                .observe(this, songId -> {
                    if (songId != null) adapter.notifyCoverChanged(songId);
                });
    }

    private void observeMiniPlayer() {
        PlayerManager pm = PlayerManager.getInstance();
        pm.getCurrentSong().observe(this, song -> {
            if (song == null) { binding.miniPlayer.setVisibility(View.GONE); return; }
            binding.miniPlayer.setVisibility(View.VISIBLE);
            binding.tvMiniTitle.setText(song.getTitle());
            binding.tvMiniArtist.setText(song.getArtist());
            Glide.with(this).clear(binding.ivMiniCover);
            binding.ivMiniCover.setImageResource(R.drawable.ic_default_album_art);
            Object src = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(song);
            Glide.with(this).load(src)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
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

    @Override
    protected void onDestroy() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        super.onDestroy();
        binding = null;
    }
}
