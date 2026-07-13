package com.miku.player.ui.player;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.activity.OnBackPressedCallback;
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

    // ── Swipe vertical estilo reels (siguiente/anterior canción) ───────────
    // Ver campos e implementación completa junto a setupSwipeGesture() más abajo.
    private boolean isSwipeAnimating = false;

    // FIX: sustituye el delay hardcodeado de 800ms.
    // El repositorio llama a este callback cuando la copia del archivo termina.
    private final MusicRepository.CoverUpdateCallback coverUpdateCallback =
            new MusicRepository.CoverUpdateCallback() {
                @Override
                public void onCoverReady(long songId, String localPath) {
                    if (currentSong == null || currentSong.getId() != songId) return;
                    runOnUiThread(() -> {
                        currentSong.setCustomCoverPath(localPath);
                        loadAlbumArt(currentSong);
                        PlayerManager.getInstance().updateCurrentSongCover(currentSong);
                        // FIX: avisar a TODAS las pantallas (listas de canciones, favoritos,
                        // detalle de playlist) de que esta carátula cambió. Sin esto, el
                        // cambio se veía en el reproductor y en el mini-player, pero las
                        // listas seguían mostrando la carátula vieja hasta reabrir la app,
                        // porque su caché de Glide nunca se invalidaba.
                        com.miku.player.utils.CoverSignatureManager.getInstance(PlayerActivity.this)
                                .invalidateSong(songId);
                    });
                }
            };

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || currentSong == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}

                // 1. Mostrar la imagen elegida inmediatamente en la UI
                currentSong.setCustomCoverPath(uri.toString());
                loadAlbumArt(currentSong);

                // 2. Iniciar copia asíncrona + actualización de DB.
                //    El callback notifica cuando la ruta local ya está lista.
                repository.updateSongCover(currentSong, uri.toString(), coverUpdateCallback);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = MusicRepository.getInstance(this);

        // FIX: onBackPressed() deprecado → usar OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(
                        android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            }
        });

        binding.toolbar.setNavigationOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        });

        setupControls();
        observePlayer();
        startProgressUpdater();
        setupMenu();
        setupSwipeGesture();

        binding.tvSongTitle.setSelected(true);
    }

    /**
     * Swipe vertical tipo TikTok/Reels para cambiar de canción, con arrastre
     * interactivo: la carátula sigue al dedo en tiempo real y muestra una
     * vista previa (preload) de la carátula de la canción siguiente/anterior
     * ANTES de confirmar el cambio. Si el usuario suelta sin cruzar el
     * umbral, todo vuelve a su lugar (spring-back) y no cambia la canción.
     *
     * - Deslizar hacia ARRIBA  → siguiente canción
     * - Deslizar hacia ABAJO   → canción anterior
     *
     * Se maneja manualmente en dispatchTouchEvent (no con GestureDetector.onFling,
     * que solo informa al soltar) para poder mover las vistas en cada
     * ACTION_MOVE. El gesto solo se activa si el toque inicial cae dentro de
     * la "zona de swipe" (entre el toolbar y el seekBar), así el Slider de
     * progreso y los botones de control siguen recibiendo sus eventos
     * normalmente, sin ningún conflicto.
     */
    private int touchSlopPx;
    private float dragStartX, dragStartY;
    private boolean touchStartedInSwipeZone = false;
    private boolean isDragging = false;
    private boolean dragIsNext = false;
    private float dragMaxDistance = 0f;
    private float lastDragProgress = 0f;

    private void setupSwipeGesture() {
        touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        handleSwipeTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void handleSwipeTouch(MotionEvent ev) {
        if (binding == null || isSwipeAnimating) return;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = ev.getRawX();
                dragStartY = ev.getRawY();
                isDragging = false;
                touchStartedInSwipeZone = isInSwipeZone(ev.getRawY());
                break;

            case MotionEvent.ACTION_MOVE:
                if (!touchStartedInSwipeZone) return;
                float dx = ev.getRawX() - dragStartX;
                float dy = ev.getRawY() - dragStartY;

                if (!isDragging) {
                    if (Math.abs(dy) > touchSlopPx && Math.abs(dy) > Math.abs(dx)) {
                        boolean wantNext = dy < 0;
                        PlayerManager pm = PlayerManager.getInstance();
                        Song preview = wantNext ? pm.peekNextSong() : pm.peekPreviousSong();
                        if (preview == null) return; // no hay a dónde ir (fin de la cola)
                        isDragging = true;
                        dragIsNext = wantNext;
                        beginDrag(preview);
                    }
                } else {
                    updateDragProgress(dy);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    endDrag();
                }
                touchStartedInSwipeZone = false;
                break;
        }
    }

    /** true si el toque cae entre el toolbar y el seekBar (la "tarjeta" de la canción). */
    private boolean isInSwipeZone(float rawY) {
        int[] topLoc = new int[2];
        binding.toolbar.getLocationOnScreen(topLoc);
        float zoneTop = topLoc[1] + binding.toolbar.getHeight();

        int[] bottomLoc = new int[2];
        binding.seekBar.getLocationOnScreen(bottomLoc);
        float zoneBottom = bottomLoc[1];

        return rawY >= zoneTop && rawY <= zoneBottom;
    }

    private void beginDrag(Song previewSong) {
        // Usamos la altura de TODO el bloque (carátula + título + artista), no solo
        // la carátula, para que la vista previa entrante siempre quede justo detrás
        // del borde del bloque saliente, sin sobreponerse al título/artista.
        dragMaxDistance = computeBlockHeight();
        lastDragProgress = 0f;

        float startTranslation = dragIsNext ? dragMaxDistance : -dragMaxDistance;
        binding.ivAlbumArtPreview.animate().cancel();
        binding.ivAlbumArtPreview.setTranslationY(startTranslation);
        binding.ivAlbumArtPreview.setAlpha(0.15f);
        binding.ivAlbumArtPreview.setVisibility(View.VISIBLE);
        binding.ivAlbumArtPreview.setImageResource(R.drawable.ic_default_album_art);

        Object src = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(previewSong);
        Glide.with(this).clear(binding.ivAlbumArtPreview);
        Glide.with(this).asBitmap().load(src)
                .signature(com.miku.player.utils.CoverSignatureManager.getInstance(this)
                        .signatureFor(previewSong.getId()))
                .error(R.drawable.ic_default_album_art)
                .into(binding.ivAlbumArtPreview);
    }

    /** Distancia vertical entre el borde superior de la carátula y el borde inferior del artista. */
    private float computeBlockHeight() {
        int[] topLoc = new int[2];
        binding.ivAlbumArt.getLocationOnScreen(topLoc);
        int[] bottomLoc = new int[2];
        binding.tvArtist.getLocationOnScreen(bottomLoc);
        float height = (bottomLoc[1] + binding.tvArtist.getHeight()) - topLoc[1];
        return height > 0 ? height : 300f * getResources().getDisplayMetrics().density;
    }

    private void updateDragProgress(float dy) {
        // Solo se mueve en la dirección bloqueada al iniciar el gesto (0..dragMaxDistance)
        float progress = dragIsNext
                ? clamp(-dy, 0f, dragMaxDistance)
                : clamp(dy, 0f, dragMaxDistance);
        lastDragProgress = progress;
        float fraction = progress / dragMaxDistance;

        // El bloque completo (carátula + título + artista) se mueve como una sola unidad
        float signedCurrent = dragIsNext ? -progress : progress;
        binding.ivAlbumArt.setTranslationY(signedCurrent);
        binding.tvSongTitle.setTranslationY(signedCurrent);
        binding.tvArtist.setTranslationY(signedCurrent);

        // Desvanecimiento progresivo: la carátula actual se disuelve mientras
        // la carátula entrante aparece, como un cross-fade
        binding.ivAlbumArt.setAlpha(1f - fraction * 0.85f);
        binding.ivAlbumArtPreview.setAlpha(0.15f + fraction * 0.85f);

        // La preview viaja la misma distancia que el bloque, así su borde
        // siempre queda justo pegado al borde saliente del bloque, nunca lo cubre
        float previewTranslation = dragMaxDistance - progress;
        binding.ivAlbumArtPreview.setTranslationY(dragIsNext ? previewTranslation : -previewTranslation);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void endDrag() {
        boolean shouldCommit = lastDragProgress > dragMaxDistance * 0.32f;
        if (shouldCommit) commitDrag(); else cancelDrag();
    }

    /** El gesto cruzó el umbral: completa la transición y confirma el cambio de canción. */
    private void commitDrag() {
        isSwipeAnimating = true;
        float target = dragIsNext ? -dragMaxDistance : dragMaxDistance;

        binding.ivAlbumArt.animate().cancel();
        binding.ivAlbumArt.animate().translationY(target).alpha(0f).setDuration(140).start();
        binding.tvSongTitle.animate().cancel();
        binding.tvSongTitle.animate().translationY(target).alpha(0f).setDuration(140).start();
        binding.tvArtist.animate().cancel();
        binding.tvArtist.animate().translationY(target).alpha(0f).setDuration(140).start();

        binding.ivAlbumArtPreview.animate().cancel();
        binding.ivAlbumArtPreview.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(160)
                .withEndAction(() -> {
                    if (binding == null) return;
                    // Sella el swap visual: la preview (ya cargada) pasa a ser la carátula real,
                    // así no hay parpadeo mientras Glide recarga ivAlbumArt para la nueva canción.
                    binding.ivAlbumArt.setImageDrawable(binding.ivAlbumArtPreview.getDrawable());
                    binding.ivAlbumArt.setTranslationY(0f);
                    binding.ivAlbumArt.setAlpha(1f);
                    binding.tvSongTitle.setTranslationY(0f);
                    binding.tvSongTitle.setAlpha(1f);
                    binding.tvArtist.setTranslationY(0f);
                    binding.tvArtist.setAlpha(1f);
                    binding.ivAlbumArtPreview.setVisibility(View.GONE);

                    PlayerManager pm = PlayerManager.getInstance();
                    if (dragIsNext) pm.playNext(); else pm.playPreviousForced();

                    isSwipeAnimating = false;
                })
                .start();
    }

    /** El gesto no cruzó el umbral: todo vuelve a su lugar, sin cambiar de canción. */
    private void cancelDrag() {
        isSwipeAnimating = true;

        binding.ivAlbumArt.animate().cancel();
        binding.ivAlbumArt.animate().translationY(0f).alpha(1f).setDuration(200).start();
        binding.tvSongTitle.animate().cancel();
        binding.tvSongTitle.animate().translationY(0f).alpha(1f).setDuration(200).start();
        binding.tvArtist.animate().cancel();
        binding.tvArtist.animate().translationY(0f).alpha(1f).setDuration(200).start();

        float offscreen = dragIsNext ? dragMaxDistance : -dragMaxDistance;
        binding.ivAlbumArtPreview.animate().cancel();
        binding.ivAlbumArtPreview.animate()
                .translationY(offscreen)
                .alpha(0.15f)
                .setDuration(200)
                .withEndAction(() -> {
                    if (binding == null) return;
                    binding.ivAlbumArtPreview.setVisibility(View.GONE);
                    isSwipeAnimating = false;
                })
                .start();
    }

    private void setupControls() {
        PlayerManager pm = PlayerManager.getInstance();

        // FIX: sincronizar con el estado REAL del reproductor al entrar a la pantalla.
        // Antes 'shuffleOn'/'repeatMode' siempre arrancaban en su valor por defecto
        // (false/OFF) sin importar el estado real — si volvías a esta pantalla con
        // shuffle o repeat ya activados, los íconos se veían apagados hasta volver
        // a tocarlos.
        shuffleOn = pm.isShuffleOn();
        repeatMode = pm.getRepeatMode();
        binding.btnShuffle.setAlpha(shuffleOn ? 1f : 0.4f);
        updateRepeatIcon();

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

            // FIX: observar SOLO la canción actual por ID en lugar de toda la lista.
            // Esto reemplaza el observeCoverUpdates() que iteraba O(n) songs en cada cambio.
            repository.getSongByIdLive(song.getId()).observe(this, updated -> {
                if (updated == null || currentSong == null
                        || updated.getId() != currentSong.getId()) return;

                // Sincronizar portada si cambió
                String newPath = updated.getCustomCoverPath();
                String oldPath = currentSong.getCustomCoverPath();
                boolean coverChanged = (newPath == null ? oldPath != null : !newPath.equals(oldPath));
                if (coverChanged) {
                    currentSong.setCustomCoverPath(newPath);
                    loadAlbumArt(currentSong);
                    PlayerManager.getInstance().updateCurrentSongCover(currentSong);
                }

                // Sincronizar favorito si lo cambió otra pantalla
                if (updated.isFavorite() != currentSong.isFavorite()) {
                    currentSong.setFavorite(updated.isFavorite());
                    updateFavoriteIcon();
                }
            });
        });

        // Si OTRA pantalla (lista de canciones, favoritos, playlist) cambia la carátula
        // de la canción que se está reproduciendo ahora mismo, refrescarla aquí también.
        com.miku.player.utils.CoverSignatureManager.getInstance(this).getInvalidationEvent()
                .observe(this, songId -> {
                    if (songId != null && currentSong != null && currentSong.getId() == songId) {
                        loadAlbumArt(currentSong);
                    }
                });

        pm.getIsPlaying().observe(this, playing ->
                binding.btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));
    }

    private void updateFavoriteIcon() {
        if (currentSong == null) return;
        binding.btnFavoritePlayer.setImageResource(
            currentSong.isFavorite() ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private void loadAlbumArt(Song song) {
        // FIX parpadeo: NO limpiar/poner placeholder antes de cargar. Como aquí solo
        // hay UNA vista de carátula (no es un RecyclerView reciclando vistas), no hay
        // riesgo de mostrar la portada de otra canción por error — así que simplemente
        // dejamos la imagen anterior visible hasta que la nueva esté lista, y recién
        // ahí se reemplaza. Antes se hacía Glide.clear() + setImageResource(placeholder)
        // de forma inmediata Y ADEMÁS se forzaba skipMemoryCache/diskCacheStrategy(NONE),
        // obligando a una re-decodificación completa desde disco en CADA cambio de
        // canción: esos dos factores juntos causaban el parpadeo/desaparición reportada.
        //
        // Ahora se usa caché normal + firma de CoverSignatureManager (el mismo mecanismo
        // que ya usan las listas de canciones), que además hace que volver a mostrar una
        // carátula ya vista sea instantáneo, y se sigue invalidando correctamente cuando
        // el usuario cambia una carátula (ver coverUpdateCallback más arriba).
        Object src = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(song);
        Glide.with(this).asBitmap().load(src)
                .signature(com.miku.player.utils.CoverSignatureManager.getInstance(this)
                        .signatureFor(song.getId()))
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
        // FIX: antes REPEAT_MODE_ONE y REPEAT_MODE_ALL se veían idénticos (mismo
        // ícono, mismo alpha). El badge "1" distingue "repetir esta canción" de
        // "repetir toda la lista".
        binding.tvRepeatOneBadge.setVisibility(
                repeatMode == Player.REPEAT_MODE_ONE ? View.VISIBLE : View.GONE);
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
    protected void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        binding = null;
    }
}
