package com.miku.player.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.miku.player.R;
import com.miku.player.databinding.ActivityMainBinding;
import com.miku.player.ui.backup.BackupActivity;
import com.miku.player.ui.favorites.FavoritesActivity;
import com.miku.player.ui.player.PlayerActivity;
import com.miku.player.ui.playlists.PlaylistsActivity;
import com.miku.player.ui.songs.SongsActivity;
import com.miku.player.utils.ActivityTransitions;
import com.miku.player.utils.PlayerManager;
import com.miku.player.utils.ViewAnimUtils;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS           = "amp39_prefs";
    public static final String KEY_FOLDER_URI  = "folder_uri";
    public static final String KEY_FOLDER_NAME = "folder_name";

    private ActivityMainBinding binding;

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String name = uri.getLastPathSegment();
                if (name != null && name.contains(":"))
                    name = name.substring(name.lastIndexOf(':') + 1);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_FOLDER_URI,  uri.toString())
                        .putString(KEY_FOLDER_NAME, name != null ? name : "Carpeta seleccionada")
                        .apply();
                updateFolderLabel();
                startActivity(new Intent(this, SongsActivity.class));
                ActivityTransitions.forward(this);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Amp39");

        PlayerManager.getInstance().connect(this);
        setupButtons();
        observeMiniPlayer();
        updateFolderLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFolderLabel();
    }

    private void setupButtons() {
        binding.cardAllSongs.setOnClickListener(v -> {
            startActivity(new Intent(this, SongsActivity.class));
            ActivityTransitions.forward(this);
        });
        binding.cardPlaylists.setOnClickListener(v -> {
            startActivity(new Intent(this, PlaylistsActivity.class));
            ActivityTransitions.forward(this);
        });
        binding.cardFavorites.setOnClickListener(v -> {
            startActivity(new Intent(this, FavoritesActivity.class));
            ActivityTransitions.forward(this);
        });
        binding.cardSelectFolder.setOnClickListener(v -> folderPicker.launch(null));
        binding.cardSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, com.miku.player.ui.settings.SettingsActivity.class));
            ActivityTransitions.forward(this);
        });

        binding.miniPlayer.setOnClickListener(v -> {
            if (PlayerManager.getInstance().getCurrentSongValue() != null) {
                startActivity(new Intent(this, PlayerActivity.class));
                ActivityTransitions.forward(this);
            }
        });
        binding.btnMiniPlayPause.setOnClickListener(v -> {
            ViewAnimUtils.pulse(v);
            PlayerManager.getInstance().togglePlayPause();
        });
        binding.btnMiniNext.setOnClickListener(v -> {
            ViewAnimUtils.pulse(v);
            PlayerManager.getInstance().playNext();
        });
        binding.btnMiniPrevious.setOnClickListener(v -> {
            ViewAnimUtils.pulse(v);
            PlayerManager.getInstance().playPrevious();
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
                binding.btnMiniPlayPause.setImageResource(
                        playing ? R.drawable.ic_pause : R.drawable.ic_play));
        startMiniProgressUpdater();
    }

    private final android.os.Handler miniProgressHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable miniProgressRunnable = new Runnable() {
        @Override public void run() {
            if (binding != null
                    && binding.miniPlayer.getVisibility() == View.VISIBLE) {
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

    private void updateFolderLabel() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String name = prefs.getString(KEY_FOLDER_NAME, null);
        binding.tvSelectedFolder.setText(name != null ? name
                : "Sin carpeta seleccionada — toca para elegir");
    }

    @Override
    protected void onDestroy() {
        miniProgressHandler.removeCallbacks(miniProgressRunnable);
        super.onDestroy();
        binding = null;
    }
}
