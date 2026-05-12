package com.miku.player.ui.player;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.data.repository.MusicRepository;
import com.miku.player.databinding.ActivityPlayerBinding;
import com.miku.player.ui.playlists.AddToPlaylistBottomSheet;
import com.miku.player.utils.PlayerManager;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isSeeking = false;
    private boolean shuffleOn = false;
    private int repeatMode = Player.REPEAT_MODE_OFF;
    private Song currentSong;
    private MusicRepository repository;

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || currentSong == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}

                // Determine the local path where the file will land
                String expectedPath = repository.getExpectedCoverPath(currentSong.getId());

                // 1. Start async copy + DB update
                repository.updateSongCover(currentSong, uri.toString());

                // 2. Update player UI immediately — load from the chosen URI directly
                //    (file copy may not be done yet, so use the original URI for now)
                currentSong.setCustomCoverPath(uri.toString());
                loadAlbumArt(currentSong);

                // 3. After copy finishes (~800ms), switch to local path and refresh notification
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    currentSong.setCustomCoverPath(expectedPath);
                    loadAlbumArt(currentSong);
                    PlayerManager.getInstance().updateCurrentSongCover(currentSong);
                }, 800);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = MusicRepository.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        });

        setupControls();
        observePlayer();
        observeCoverUpdates();
        startProgressUpdater();
        setupMenu();

        binding.tvSongTitle.setSelected(true);
    }

    private void setupControls() {
        PlayerManager pm = PlayerManager.getInstance();

        binding.btnPlayPause.setOnClickListener(v -> pm.togglePlayPause());
        binding.btnNext.setOnClickListener(v -> pm.playNext());
        binding.btnPrevious.setOnClickListener(v -> pm.playPrevious());

        binding.btnShuffle.setOnClickListener(v -> {
            shuffleOn = !shuffleOn;
            pm.setShuffleEnabled(shuffleOn);
            binding.btnShuffle.setAlpha(shuffleOn ? 1f : 0.4f);
        });

        binding.btnRepeat.setOnClickListener(v -> {
            repeatMode = (repeatMode + 1) % 3;
            pm.setRepeatMode(repeatMode);
            updateRepeatIcon();
        });

        binding.btnFavoritePlayer.setOnClickListener(v -> {
            if (currentSong != null) {
                boolean newState = !currentSong.isFavorite();
                currentSong.setFavorite(newState);
                updateFavoriteIcon();
                // Use explicit state so DB always gets the correct value
                repository.setFavorite(currentSong, newState);
            }
        });

        binding.seekBar.addOnSliderTouchListener(
                new com.google.android.material.slider.Slider.OnSliderTouchListener() {
                    public void onStartTrackingTouch(com.google.android.material.slider.Slider s) { isSeeking = true; }
                    public void onStopTrackingTouch(com.google.android.material.slider.Slider s) {
                        isSeeking = false;
                        pm.seekTo((long)(s.getValue() / 100f * pm.getDuration()));
                    }
                });

        binding.ivAlbumArt.setOnLongClickListener(v -> { coverPickerLauncher.launch("image/*"); return true; });

        binding.btnShuffle.setAlpha(0.4f);
        binding.btnRepeat.setAlpha(0.4f);
    }

    private void observePlayer() {
        PlayerManager pm = PlayerManager.getInstance();
        pm.getCurrentSong().observe(this, song -> {
            if (song == null) return;
            currentSong = song;
            binding.tvSongTitle.setText(song.getDisplayTitle());
            binding.tvArtist.setText(song.getArtist());
            loadAlbumArt(song);
            updateFavoriteIcon();
        });
        pm.getIsPlaying().observe(this, playing ->
                binding.btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));
    }

    /**
     * Watches the songs LiveData so that when updateSongCover() finishes copying
     * the image and updates Room, the cover refreshes immediately without needing
     * to change tracks.
     */
    /**
     * Watches getAllSongs() LiveData to react to changes made from OTHER screens:
     * - Cover path changes  → reload artwork
     * - isFavorite changes  → update heart icon
     * Both can happen when the user acts from SongsActivity while this is in the back stack.
     */
    private void observeCoverUpdates() {
        repository.getAllSongs().observe(this, songs -> {
            if (songs == null || currentSong == null) return;
            for (com.miku.player.data.model.Song s : songs) {
                if (s.getId() == currentSong.getId()) {
                    // Sync cover path
                    String newPath = s.getCustomCoverPath();
                    String oldPath = currentSong.getCustomCoverPath();
                    boolean coverChanged = (newPath == null ? oldPath != null
                            : !newPath.equals(oldPath));
                    if (coverChanged) {
                        currentSong.setCustomCoverPath(newPath);
                        loadAlbumArt(currentSong);
                        PlayerManager.getInstance().updateCurrentSongCover(currentSong);
                    }

                    // Sync favorite state changed from another screen
                    if (s.isFavorite() != currentSong.isFavorite()) {
                        currentSong.setFavorite(s.isFavorite());
                        updateFavoriteIcon();
                    }
                    break;
                }
            }
        });
    }

    private void updateFavoriteIcon() {
        if (currentSong == null) return;
        binding.btnFavoritePlayer.setImageResource(
            currentSong.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private void loadAlbumArt(Song song) {
        Glide.with(this).clear(binding.ivAlbumArt);
        binding.ivAlbumArt.setImageResource(R.drawable.ic_default_album_art);

        Object src = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(song);
        Glide.with(this).asBitmap().load(src)
                .skipMemoryCache(true)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .error(R.drawable.ic_default_album_art)
                .into(new CustomTarget<Bitmap>() {
                    public void onResourceReady(Bitmap bmp, Transition<? super Bitmap> t) {
                        if (binding == null) return;
                        binding.ivAlbumArt.setImageBitmap(bmp);
                    }
                    public void onLoadCleared(android.graphics.drawable.Drawable p) {
                        if (binding != null) binding.ivAlbumArt.setImageResource(R.drawable.ic_default_album_art);
                    }
                    public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                        if (binding != null) binding.ivAlbumArt.setImageResource(R.drawable.ic_default_album_art);
                    }
                });
    }

    private void startProgressUpdater() {
        progressHandler.postDelayed(new Runnable() {
            public void run() {
                if (!isSeeking) updateProgress();
                progressHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void updateProgress() {
        PlayerManager pm = PlayerManager.getInstance();
        long pos = pm.getPositionMs();
        long dur = pm.getDuration();
        if (dur <= 0) return;
        binding.seekBar.setValue(Math.min((pos * 100f) / dur, 100f));
        binding.tvCurrentTime.setText(fmt(pos));
        binding.tvDuration.setText(fmt(dur));
    }

    private String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void updateRepeatIcon() {
        binding.btnRepeat.setAlpha(repeatMode == Player.REPEAT_MODE_OFF ? 0.4f : 1f);
    }

    private void setupMenu() {
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_change_cover) {
                coverPickerLauncher.launch("image/*"); return true;
            } else if (id == R.id.action_add_to_playlist) {
                if (currentSong != null)
                    AddToPlaylistBottomSheet.newInstance(currentSong).show(getSupportFragmentManager(), "add");
                return true;
            } else if (id == R.id.action_rename) {
                showRenameDialog(); return true;
            }
            return false;
        });
    }

    private void showRenameDialog() {
        if (currentSong == null) return;
        com.google.android.material.textfield.TextInputLayout inputLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Nombre de la canción");
        inputLayout.setBoxBackgroundMode(
                com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        android.widget.EditText editText =
                new com.google.android.material.textfield.TextInputEditText(inputLayout.getContext());
        editText.setText(currentSong.getDisplayTitle());
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
                    if (newName.isEmpty() || newName.equals(currentSong.getDisplayTitle())) return;
                    repository.updateCustomTitle(currentSong, newName, this,
                        new com.miku.player.utils.TagWriter.Result() {
                            public void onSuccess() {
                                currentSong.setCustomTitle(newName);
                                binding.tvSongTitle.setText(newName);
                            }
                            public void onError(String msg) {
                                // Title saved in DB, but file write failed — show subtle warning
                                currentSong.setCustomTitle(newName);
                                binding.tvSongTitle.setText(newName);
                                com.google.android.material.snackbar.Snackbar.make(
                                    binding.getRoot(),
                                    "Nombre guardado (no se pudo escribir en el archivo: " + msg + ")",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                            }
                        });
                })
                .setNeutralButton("Restablecer nombre original", (d, w) -> {
                    repository.updateCustomTitle(currentSong, null, this,
                        new com.miku.player.utils.TagWriter.Result() {
                            public void onSuccess() {
                                currentSong.setCustomTitle(null);
                                binding.tvSongTitle.setText(currentSong.getDisplayTitle());
                            }
                            public void onError(String msg) {
                                currentSong.setCustomTitle(null);
                                binding.tvSongTitle.setText(currentSong.getDisplayTitle());
                            }
                        });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        binding = null;
    }
}
