package com.miku.player.ui.songs;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
import com.miku.player.utils.PlayerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SongsActivity extends AppCompatActivity
        implements SongsAdapter.OnSongClickListener {

    // Sort modes
    private static final int SORT_AZ     = 0;
    private static final int SORT_RECENT = 1;
    private static final int SORT_ARTIST = 2;

    private ActivitySongsBinding binding;
    private SongsAdapter adapter;
    private SongsViewModel viewModel;
    private List<Song> allSongs = new ArrayList<>();   // full unfiltered+sorted list
    private String currentQuery = "";
    private int currentSort = SORT_AZ;
    private Song pendingCoverSong;

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && pendingCoverSong != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    final com.miku.player.data.model.Song songRef = pendingCoverSong;
                    final String expectedPath = viewModel.getExpectedCoverPath(songRef.getId());

                    // 1. Start the async file copy + DB update
                    viewModel.updateSongCover(songRef, uri.toString());

                    // 2. Invalidate Glide signature immediately — forces cache-busted reload
                    adapter.invalidateCover(this, songRef.getId());

                    // 3. After the file copy finishes, invalidate again to load the real file
                    //    and update the notification if this song is playing
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        adapter.invalidateCover(this, songRef.getId());
                        com.miku.player.data.model.Song playing =
                                com.miku.player.utils.PlayerManager.getInstance().getCurrentSongValue();
                        if (playing != null && playing.getId() == songRef.getId()) {
                            songRef.setCustomCoverPath(expectedPath);
                            com.miku.player.utils.PlayerManager.getInstance()
                                    .updateCurrentSongCover(songRef);
                        }
                    }, 900);
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) loadSongs(); else showPermissionDenied();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        observeMiniPlayer();
        checkPermissionAndLoad();
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
        binding.rvSongs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSongs.setHasFixedSize(true);
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
    }

    private void setupChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipSortAZ)     currentSort = SORT_AZ;
            else if (id == R.id.chipSortRecent) currentSort = SORT_RECENT;
            else if (id == R.id.chipSortArtist) currentSort = SORT_ARTIST;
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
                if (s.getTitle().toLowerCase().contains(lower)
                        || s.getArtist().toLowerCase().contains(lower)) {
                    filtered.add(s);
                }
            }
        }

        // 2. Sort
        switch (currentSort) {
            case SORT_RECENT:
                Collections.sort(filtered, (a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));
                break;
            case SORT_ARTIST:
                Collections.sort(filtered, Comparator.comparing(
                        s -> (s.getArtist() + s.getTitle()).toLowerCase()));
                break;
            case SORT_AZ:
            default:
                Collections.sort(filtered, Comparator.comparing(
                        s -> s.getTitle().toLowerCase()));
                break;
        }

        adapter.submitList(filtered, () -> {
            Song playing = PlayerManager.getInstance().getCurrentSongValue();
            if (playing != null) adapter.setCurrentlyPlayingId(playing.getId());
            if (scrollToTop) binding.rvSongs.scrollToPosition(0);
        });

        binding.rvSongs.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void observeViewModel() {
        viewModel.getSongs().observe(this, songs -> {
            allSongs = songs != null ? songs : new ArrayList<>();
            applyFilterAndSort(false);
        });
    }

    private void observeCurrentSong() {
        PlayerManager.getInstance().getCurrentSong().observe(this, song -> {
            long id = song != null ? song.getId() : -1;
            adapter.setCurrentlyPlayingId(id);
        });
    }

    private void checkPermissionAndLoad() {
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
        viewModel.scanFolder(folderUri);
    }

    @Override
    public void onSongClick(Song song, int position) {
        PlayerManager pm = PlayerManager.getInstance();
        // If the tapped song is already the one playing, just open the player
        // without restarting it so the current position is preserved.
        Song currentlyPlaying = pm.getCurrentSongValue();
        if (currentlyPlaying != null && currentlyPlaying.getId() == song.getId()) {
            startActivity(new Intent(this, PlayerActivity.class));
            return;
        }
        List<Song> adapterList = adapter.getCurrentList();
        List<Song> playQueue = (adapterList != null && !adapterList.isEmpty())
                ? new ArrayList<>(adapterList) : new ArrayList<>(allSongs);
        pm.playSong(song, playQueue);
        startActivity(new Intent(this, PlayerActivity.class));
    }

    @Override
    public void onMoreClick(Song song, int position) {
        String[] options = {
            getString(R.string.add_to_playlist),
            getString(R.string.change_cover),
            "Cambiar nombre"
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(song.getDisplayTitle())
                .setItems(options, (d, w) -> {
                    if (w == 0) AddToPlaylistBottomSheet.newInstance(song)
                            .show(getSupportFragmentManager(), "add");
                    else if (w == 1) { pendingCoverSong = song; coverPickerLauncher.launch("image/*"); }
                    else showRenameDialog(song);
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
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(p, p / 2, p, 0);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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

    private void showPermissionDenied() {
        Snackbar.make(binding.getRoot(), getString(R.string.permission_required), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.grant_permission), v -> checkPermissionAndLoad()).show();
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
