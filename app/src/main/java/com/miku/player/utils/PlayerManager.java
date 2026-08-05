package com.miku.player.utils;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.miku.player.data.model.Song;
import com.miku.player.data.service.MusicService;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton managing playback state and MediaController connection.
 *
 * Key responsibility for covers:
 * - playSong() builds MediaItems with artworkUri pointing to the local cover file
 *   (or the MediaStore album art URI if no custom cover exists).
 * - updateCurrentSongCover() replaces the current MediaItem in-place with updated
 *   metadata so Media3 refreshes the notification artwork immediately.
 */
public class PlayerManager {

    private static volatile PlayerManager INSTANCE;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    private final MutableLiveData<Boolean> isPlaying    = new MutableLiveData<>(false);
    private final MutableLiveData<Song>    currentSong  = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(-1);

    private List<Song> queue      = new ArrayList<>();
    private int        queueIndex = -1;

    private boolean shuffleOn  = false;
    private int     repeatMode = Player.REPEAT_MODE_OFF;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    private PlayerManager() {}

    public static PlayerManager getInstance() {
        if (INSTANCE == null) {
            synchronized (PlayerManager.class) {
                if (INSTANCE == null) INSTANCE = new PlayerManager();
            }
        }
        return INSTANCE;
    }

    public void connect(Context context) {
        if (controller != null && controller.isConnected()) return;
        SessionToken token = new SessionToken(
                context, new ComponentName(context, MusicService.class));
        controllerFuture = new MediaController.Builder(context, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                attachListeners();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void attachListeners() {
        controller.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying.postValue(playing);
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (controller == null) return;
                int idx = controller.getCurrentMediaItemIndex();
                if (idx >= 0 && idx < queue.size()) {
                    queueIndex = idx;
                    currentSong.postValue(queue.get(idx));
                    currentIndex.postValue(idx);
                }
            }
        });
    }

    public void release() {
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * FIX de rendimiento: antes, el bucle que arma un MediaItem por cada canción
     * de la cola (buildMediaItem() → resolveArtworkUri(), que hace un
     * File.exists() por canción para resolver la carátula) se ejecutaba de forma
     * SÍNCRONA en el hilo principal, cada vez que tocabas cualquier canción de
     * una lista — no solo al usar los botones de siguiente/anterior (esos usan
     * seekToNextMediaItem()/seekToPreviousMediaItem() sobre la cola ya armada,
     * eso sí era rápido). En listas grandes (cientos de canciones), esos
     * cientos de comprobaciones de archivo bloqueaban el hilo principal el
     * tiempo suficiente para sentirse como un retraso antes de que sonara la
     * canción. Ahora el armado ocurre en segundo plano, y solo se vuelve al
     * hilo principal para hablar con el MediaController (Media3 lo exige).
     */
    public void playSong(Song song, List<Song> songs) {
        if (controller == null) return;

        queue = new ArrayList<>(songs);
        // Use ID comparison — Song has no equals() override so indexOf()
        // compares object references and returns -1 when lists are different instances.
        int idx = -1;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == song.getId()) { idx = i; break; }
        }
        if (idx == -1) idx = 0;
        final int finalIdx = idx;
        queueIndex = idx;

        bgExecutor.execute(() -> {
            List<MediaItem> items = new ArrayList<>();
            for (Song s : songs) {
                items.add(buildMediaItem(s));
            }
            mainHandler.post(() -> {
                if (controller == null) return;
                controller.setMediaItems(items, finalIdx, 0L);
                controller.prepare();
                controller.play();

                currentSong.postValue(song);
                currentIndex.postValue(finalIdx);
                isPlaying.postValue(true);
            });
        });
    }

    /**
     * Called after the user changes the cover of the currently playing song.
     * Updates the MediaItem metadata in-place so the notification artwork
     * refreshes without interrupting playback.
     */
    public void updateCurrentSongCover(Song song) {
        if (controller == null || queueIndex < 0 || queueIndex >= queue.size()) return;

        // Update our in-memory queue reference
        queue.set(queueIndex, song);

        // Replace the MediaItem at the current index with updated artwork URI.
        // Media3 will re-read artworkUri and update the notification automatically.
        MediaItem updated = buildMediaItem(song);
        controller.replaceMediaItem(queueIndex, updated);
    }

    /**
     * Igual que updateCurrentSongCover() — buildMediaItem() ya reconstruye TODOS
     * los metadatos (título, artista, carátula), así que sirve para cualquier
     * cambio. Se agrega con este nombre para usarlo tras renombrar una canción:
     * antes, renombrar la canción que se está reproduciendo no actualizaba la
     * notificación/pantalla de bloqueo hasta el siguiente cambio de pista.
     */
    public void updateCurrentSongMetadata(Song song) {
        updateCurrentSongCover(song);
    }

    /**
     * Builds a MediaItem for a Song, embedding the correct artworkUri so that
     * DefaultMediaNotificationProvider can load it into the notification.
     *
     * Priority:
     *   1. Local file path  (set by user via cover picker, stored in filesDir/covers/)
     *   2. MediaStore album art URI (embedded in the audio file)
     */
    private MediaItem buildMediaItem(Song song) {
        Uri artworkUri = resolveArtworkUri(song);

        return new MediaItem.Builder()
                .setUri(Uri.parse(song.getPath()))
                .setMediaId(String.valueOf(song.getId()))
                .setMediaMetadata(new MediaMetadata.Builder()
                        // FIX: usaba el título ORIGINAL del archivo — una canción
                        // renombrada seguía mostrando su nombre viejo en la
                        // notificación, pantalla de bloqueo, Bluetooth del auto, etc.
                        .setTitle(song.getDisplayTitle())
                        .setArtist(song.getArtist())
                        .setAlbumTitle(song.getAlbum())
                        .setArtworkUri(artworkUri)
                        .build())
                .build();
    }

    /**
     * Returns the best URI for artwork:
     * - If customCoverPath is an absolute file path → file:// URI
     * - If customCoverPath is already a URI string   → parse it
     * - Otherwise                                    → MediaStore album art URI
     *
     * FIX: la URI de albumart usa ALBUM_ID, que en songs escaneados por MediaStore
     * coincide con song.getId() solo por casualidad. Para ser seguros usamos el
     * customCoverPath cuando existe; el fallback apunta al albumart por ID de álbum
     * que en MediaStore se obtiene de ALBUM_ID. Como Song no almacena albumId,
     * la fallback URI queda como intento best-effort por id.
     */
    private Uri resolveArtworkUri(Song song) {
        String cp = song.getCustomCoverPath();
        if (cp != null && !cp.isEmpty()) {
            if (cp.startsWith("/")) {
                File f = new File(cp);
                if (f.exists()) return Uri.fromFile(f); // file:///data/user/...
            }
            return Uri.parse(cp);
        }
        // Fallback: embedded MediaStore album art.
        // Nota: content://media/external/audio/albumart/<id> espera el ALBUM_ID,
        // no el _ID de la canción. Si la canción viene de MediaStore scan, el id
        // almacenado en Song es el _ID; en ese caso este fallback puede fallar
        // para algunas canciones. La portada siempre queda en customCoverPath
        // tras el escaneo (cacheEmbeddedCover), por lo que este path solo se
        // alcanza en casos excepcionales.
        return Uri.parse("content://media/external/audio/albumart/" + song.getId());
    }

    public void togglePlayPause() {
        if (controller == null) return;
        if (controller.isPlaying()) controller.pause();
        else controller.play();
    }

    public void playNext() {
        if (controller == null || queue.isEmpty()) return;
        if (controller.hasNextMediaItem()) controller.seekToNextMediaItem();
    }

    public void playPrevious() {
        if (controller == null || queue.isEmpty()) return;
        if (controller.getCurrentPosition() > 3000) controller.seekTo(0);
        else if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem();
    }

    /**
     * Igual que playPrevious() pero SIN el comportamiento de "reiniciar la
     * canción actual si ya lleva más de 3s de reproducción". Se usa desde el
     * gesto de swipe, donde el usuario ya eligió explícitamente ir a la
     * canción anterior (a diferencia del botón físico ⏮, que replica el
     * comportamiento estándar tipo Spotify).
     */
    public void playPreviousForced() {
        if (controller == null || queue.isEmpty()) return;
        if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem();
    }

    /**
     * Devuelve la canción siguiente en la cola (para precargar su carátula
     * durante el gesto de swipe, antes de confirmar el cambio de pista).
     * Nota: usa el orden lineal de la cola; si el shuffle está activo esto es
     * solo una aproximación visual — la navegación real siempre la resuelve
     * el MediaController con seekToNextMediaItem()/seekToPreviousMediaItem().
     */
    public Song peekNextSong() {
        if (queue.isEmpty() || queueIndex < 0) return null;
        int idx = queueIndex + 1;
        if (idx >= queue.size()) {
            if (repeatMode == Player.REPEAT_MODE_ALL) idx = 0; else return null;
        }
        return queue.get(idx);
    }

    /** Ver peekNextSong(). Misma lógica pero hacia atrás. */
    public Song peekPreviousSong() {
        if (queue.isEmpty() || queueIndex < 0) return null;
        int idx = queueIndex - 1;
        if (idx < 0) {
            if (repeatMode == Player.REPEAT_MODE_ALL) idx = queue.size() - 1; else return null;
        }
        return queue.get(idx);
    }

    public void seekTo(long ms) {
        if (controller != null) controller.seekTo(ms);
    }

    public void setShuffleEnabled(boolean enabled) {
        if (controller != null) { shuffleOn = enabled; controller.setShuffleModeEnabled(enabled); }
    }

    public void setRepeatMode(@Player.RepeatMode int mode) {
        if (controller != null) { repeatMode = mode; controller.setRepeatMode(mode); }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public LiveData<Boolean>  getIsPlaying()       { return isPlaying; }
    public LiveData<Song>     getCurrentSong()      { return currentSong; }
    public LiveData<Integer>  getCurrentIndexLive() { return currentIndex; }

    public long    getDuration()       { return controller != null ? controller.getDuration() : 0L; }
    public long    getPositionMs()     { return controller != null ? controller.getCurrentPosition() : 0L; }
    public boolean isConnected()       { return controller != null && controller.isConnected(); }
    public boolean isPlaying()         { return controller != null && controller.isPlaying(); }
    public boolean isShuffleOn()       { return shuffleOn; }
    public int     getRepeatMode()     { return repeatMode; }
    public Song    getCurrentSongValue() { return currentSong.getValue(); }
    public int     getCurrentQueueIndex() { return queueIndex; }
    public List<Song> getQueue()       { return queue; }
}
