package com.miku.player.ui.songs;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.databinding.ActivitySongsBinding;
import com.miku.player.ui.main.MainActivity;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.ui.playlists.AddToPlaylistBottomSheet;
import com.miku.player.utils.ActivityTransitions;
import com.miku.player.utils.PlayerManager;
import com.miku.player.utils.ViewAnimUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SongsActivity extends AppCompatActivity
        implements SongsAdapter.OnSongClickListener {
    private final com.miku.player.ui.common.SongActions songActions =
            new com.miku.player.ui.common.SongActions(this, new com.miku.player.ui.common.SongActions.Host() {
                @Override public boolean isAlive() { return binding != null; }
                @Override public android.view.View snackbarAnchor() { return binding.getRoot(); }
                @Override public void deleteSong(com.miku.player.data.model.Song song, boolean deleteFile,
                        com.miku.player.data.repository.MusicRepository.Callback<Boolean> cb) {
                    viewModel.deleteSong(song, deleteFile, cb);
                }
                @Override public void updateCustomTitle(com.miku.player.data.model.Song song,
                        String newTitle, boolean writeToFile) {
                    viewModel.updateCustomTitle(song, newTitle, SongsActivity.this, writeToFile);
                }
                @Override public void updateSongCover(com.miku.player.data.model.Song song,
                        String coverUri, boolean writeToFile,
                        com.miku.player.data.repository.MusicRepository.CoverUpdateCallback cb) {
                    viewModel.updateSongCover(song, coverUri, writeToFile, cb);
                }
                @Override public void invalidateCover(long songId) { adapter.invalidateCover(SongsActivity.this, songId); }
            });


    // Sort modes
    private static final int SORT_AZ            = 0;
    private static final int SORT_RECENT_ADDED   = 1;
    private static final int SORT_RECENT_MODIFIED = 2;
    private static final String KEY_SORT_MODE = "songs_sort_mode";

    private ActivitySongsBinding binding;
    private SongsAdapter adapter;
    private SongsViewModel viewModel;
    private List<Song> allSongs = new ArrayList<>();   // full unfiltered+sorted list
    private String currentQuery = "";
    private int currentSort = SORT_AZ;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) loadSongs(); else showPermissionDenied();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        songActions.restoreState(savedInstanceState);
        binding = ActivitySongsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewModel = new ViewModelProvider(this).get(SongsViewModel.class);

        setupAdapter();
        setupSearch();
        setupChips();
        observeViewModel();
        observeCurrentSong();
        observeCurrentSongCoverUpdates();
        observeCoverInvalidation();
        observeMiniPlayer();
        checkPermissionAndLoad();
    }

    /**
     * FIX: complementa a observeCurrentSongCoverUpdates() (que solo cubre la canción
     * que se está reproduciendo). Este cubre CUALQUIER canción de la lista cuya
     * carátula haya cambiado desde otra pantalla (reproductor, favoritos, playlist).
     */
    private void observeCoverInvalidation() {
        com.miku.player.utils.CoverSignatureManager.getInstance(this).getInvalidationEvent()
                .observe(this, songId -> {
                    if (songId != null) adapter.notifyCoverChanged(songId);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Song playing = PlayerManager.getInstance().getCurrentSongValue();
        if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
    }

    private void setupAdapter() {
        adapter = new SongsAdapter(this);
        adapter.setFavoriteListener((song, newState) -> viewModel.setFavorite(song, newState));
        binding.rvSongs.setAdapter(adapter);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(false);
        binding.rvSongs.setLayoutManager(lm);
        binding.rvSongs.setHasFixedSize(true);
        binding.rvSongs.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        Song playing = PlayerManager.getInstance().getCurrentSongValue();
        if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentQuery = s.toString();
                applyFilterAndSort(false);
            }
            public void afterTextChanged(Editable s) {}
        });

        // Botón "X": limpia el texto (vuelve al hint "Buscar canciones, artistas"),
        // quita el foco del campo y oculta el teclado.
        binding.tilSearch.setEndIconOnClickListener(v -> {
            binding.etSearch.setText("");
            binding.etSearch.clearFocus();
            hideKeyboard(binding.etSearch);
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void setupChips() {
        // FIX: restaurar el último orden elegido por el usuario (antes siempre volvía a A-Z)
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        currentSort = prefs.getInt(KEY_SORT_MODE, SORT_AZ);
        int checkedChipId = currentSort == SORT_RECENT_ADDED ? R.id.chipSortAdded
                : currentSort == SORT_RECENT_MODIFIED ? R.id.chipSortModified
                : R.id.chipSortAZ;
        binding.chipGroupFilter.check(checkedChipId);

        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipSortAZ)             currentSort = SORT_AZ;
            else if (id == R.id.chipSortAdded)     currentSort = SORT_RECENT_ADDED;
            else if (id == R.id.chipSortModified)  currentSort = SORT_RECENT_MODIFIED;
            prefs.edit().putInt(KEY_SORT_MODE, currentSort).apply();
            applyFilterAndSort(true);
        });
    }

    private void applyFilterAndSort(boolean scrollToTop) {
        // 1. Filter by search query
        List<Song> filtered;
        if (currentQuery == null || currentQuery.trim().isEmpty()) {
            filtered = new ArrayList<>(allSongs);
        } else {
            String lower = currentQuery.toLowerCase().trim();
            filtered = new ArrayList<>();
            for (Song s : allSongs) {
                // FIX: buscaba por el título ORIGINAL del archivo, así que una canción
                // renombrada dejaba de aparecer si buscabas por su nombre nuevo.
                if (s.getDisplayTitle().toLowerCase().contains(lower)
                        || s.getArtist().toLowerCase().contains(lower)) {
                    filtered.add(s);
                }
            }
        }

        // 2. Sort
        switch (currentSort) {
            case SORT_RECENT_ADDED:
                Collections.sort(filtered, (a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));
                break;
            case SORT_RECENT_MODIFIED:
                Collections.sort(filtered, (a, b) -> Long.compare(b.getDateModified(), a.getDateModified()));
                break;
            case SORT_AZ:
            default:
                // FIX: esta es la causa de "no se acomoda a la posición nueva al
                // renombrar" — ordenaba por el título ORIGINAL del archivo, así que
                // una canción renombrada se quedaba en su posición alfabética vieja
                // para siempre, sin importar cuántas veces se refrescara la lista.
                Collections.sort(filtered, Comparator.comparing(
                        s -> s.getDisplayTitle().toLowerCase()));
                break;
        }

        binding.tvSongCount.setText(getString(R.string.songs_count, filtered.size()));

        adapter.submitList(new ArrayList<>(filtered), () -> {
            Song playing = PlayerManager.getInstance().getCurrentSongValue();
            if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
            if (scrollToTop) {
                // post() garantiza que el RecyclerView ya procesó la nueva lista
                // antes de hacer scroll, evitando que aparezca en posición aleatoria.
                binding.rvSongs.post(() -> binding.rvSongs.scrollToPosition(0));
            }
        });

        binding.rvSongs.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void observeViewModel() {
        viewModel.getSongs().observe(this, songs -> {
            allSongs = songs != null ? songs : new ArrayList<>();
            applyFilterAndSort(false);
        });
    }

    /**
     * Cuando Room emite una actualización de la canción activa (por ejemplo, tras cambiar
     * su portada desde PlayerActivity), invalida el item correspondiente en el adapter
     * para que Glide lo recargue sin esperar a que el usuario vuelva a la lista.
     */
    private void observeCurrentSongCoverUpdates() {
        PlayerManager.getInstance().getCurrentSong().observe(this, playing -> {
            if (playing == null) return;
            // Observar la versión reactiva de esa canción en DB
            viewModel.getSongByIdLive(playing.getId()).observe(this, updated -> {
                if (updated == null) return;
                // Si cambió el customCoverPath, invalidar en el adapter
                adapter.invalidateCover(this, updated.getId());
                // Actualizar también la copia en allSongs para que la firma sea coherente
                for (int i = 0; i < allSongs.size(); i++) {
                    if (allSongs.get(i).getId() == updated.getId()) {
                        allSongs.get(i).setCustomCoverPath(updated.getCustomCoverPath());
                        break;
                    }
                }
            });
        });
    }

    private void observeCurrentSong() {
        PlayerManager.getInstance().getCurrentSong().observe(this, song -> {
            long id = song != null ? song.getId() : -1;
            adapter.setCurrentlyPlayingId(id);
        });
    }

    private void checkPermissionAndLoad() {
        // Con una carpeta elegida (SAF) el acceso lo da el selector de carpetas: no se
        // necesita el permiso de audio, así que no se bloquea la carga si fue denegado.
        String folderUri = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(MainActivity.KEY_FOLDER_URI, null);
        if (folderUri != null) {
            loadSongs();
            return;
        }
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            loadSongs();
        else permissionLauncher.launch(perm);
    }

    private void loadSongs() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String folderUri = prefs.getString(MainActivity.KEY_FOLDER_URI, null);
        if (folderUri != null && !hasFolderPermission(folderUri)) {
            // Antes la lista quedaba vacía sin explicación (p. ej. tras restaurar un respaldo
            // o reinstalar). La biblioteca guardada se sigue mostrando; no se re-escanea.
            Snackbar.make(binding.getRoot(),
                    "Se perdió el acceso a la carpeta de música. Vuelve a elegirla desde el inicio.",
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction("Entendido", v -> {}).show();
            return;
        }
        viewModel.scanFolder(folderUri);
    }

    private boolean hasFolderPermission(String folderUri) {
        Uri target = Uri.parse(folderUri);
        for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(target) && p.isReadPermission()) return true;
        }
        return false;
    }

    @Override
    public void onSongClick(Song song, int position) {
        PlayerManager pm = PlayerManager.getInstance();
        // If the tapped song is already the one playing, just open the player
        // without restarting it so the current position is preserved.
        Song currentlyPlaying = pm.getCurrentSongValue();
        if (currentlyPlaying != null && currentlyPlaying.getId() == song.getId()) {
            startActivity(new Intent(this, PlayerActivity.class));
            ActivityTransitions.forward(this);
            return;
        }
        List<Song> adapterList = adapter.getCurrentList();
        List<Song> playQueue = (adapterList != null && !adapterList.isEmpty())
                ? new ArrayList<>(adapterList) : new ArrayList<>(allSongs);
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
                    else if (w == 1) songActions.pickCover(song);
                    else if (w == 2) songActions.showRenameDialog(song);
                    else songActions.showDeleteDialog(song);
                }).show();
    }

    private void showPermissionDenied() {
        Snackbar.make(binding.getRoot(), getString(R.string.permission_required), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.grant_permission), v -> checkPermissionAndLoad()).show();
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
                binding.btnMiniPlayPause.setImageResource(
                        playing ? R.drawable.ic_pause : R.drawable.ic_play));
        binding.miniPlayer.setOnClickListener(v -> {
            startActivity(new Intent(this, PlayerActivity.class));
            ActivityTransitions.forward(this);
        });
        binding.btnMiniPlayPause.setOnClickListener(v -> { ViewAnimUtils.pulse(v); pm.togglePlayPause(); });
        binding.btnMiniNext.setOnClickListener(v -> { ViewAnimUtils.pulse(v); pm.playNext(); });
        binding.btnMiniPrevious.setOnClickListener(v -> { ViewAnimUtils.pulse(v); pm.playPrevious(); });

        // Barra de progreso (seek bar) del mini player — se llama "mini seek bar"
        // Se actualiza cada 500ms mientras la canción está activa.
    }

    private final android.os.Handler miniProgressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable miniProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateMiniPlayerProgress();
            miniProgressHandler.postDelayed(this, 500);
        }
    };

    private void startMiniPlayerProgressUpdater() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        miniProgressHandler.post(miniProgressRunnable);
    }

    private void updateMiniPlayerProgress() {
        if (binding == null) return;
        PlayerManager pm = PlayerManager.getInstance();
        long duration = pm.getDuration();
        long position = pm.getPositionMs();
        if (duration <= 0 || binding.miniPlayer.getVisibility() != View.VISIBLE) return;
        int progress = (int) ((position * 1000L) / duration);
        binding.miniPlayerProgress.setProgress(progress);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        ActivityTransitions.back(this);
        return true;
    }

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        songActions.saveState(outState);
    }

    // El sondeo del progreso solo corre mientras la pantalla es visible (onStart..onStop).
    @Override
    protected void onStart() {
        super.onStart();
        startMiniPlayerProgressUpdater();
    }

    @Override
    protected void onStop() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        super.onDestroy();
        binding = null;
    }
}
