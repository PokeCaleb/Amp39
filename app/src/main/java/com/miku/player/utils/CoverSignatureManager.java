package com.miku.player.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.bumptech.glide.signature.ObjectKey;

/**
 * Manages Glide cache-busting signatures for song cover images.
 *
 * Two levels of invalidation:
 *   1. Per-song: incremented when the user changes a specific song's cover.
 *   2. Global:   incremented after a backup import so ALL cached covers are
 *                discarded, preventing stale images from the previous install.
 */
public class CoverSignatureManager {

    private static final String PREFS        = "cover_sig_prefs";
    private static final String KEY_GLOBAL   = "global_version";
    private static final String KEY_PREFIX   = "song_sig_";

    private static volatile CoverSignatureManager INSTANCE;
    private final SharedPreferences prefs;

    /**
     * Evento global: se emite cada vez que invalidateSong() se llama, sin importar
     * desde qué pantalla. Cualquier pantalla que muestre esa canción (lista de
     * canciones, favoritos, detalle de playlist, reproductor) puede observarlo y
     * refrescar SOLO esa fila/imagen, sin depender de que la pantalla que originó
     * el cambio conozca a las demás. Esto es lo que faltaba: PlayerActivity
     * cambiaba la carátula pero nunca avisaba a las demás pantallas.
     */
    private final MutableLiveData<Long> invalidationEvent = new MutableLiveData<>();

    private CoverSignatureManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static CoverSignatureManager getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (CoverSignatureManager.class) {
                if (INSTANCE == null) INSTANCE = new CoverSignatureManager(ctx);
            }
        }
        return INSTANCE;
    }

    /** Returns a Glide ObjectKey that encodes both global version and per-song version. */
    public ObjectKey signatureFor(long songId) {
        int global = prefs.getInt(KEY_GLOBAL, 0);
        int perSong = prefs.getInt(KEY_PREFIX + songId, 0);
        return new ObjectKey(global + "_" + songId + "_" + perSong);
    }

    /** Observa esto para enterarte cuándo cambió la carátula de una canción, la haya cambiado quien la haya cambiado. */
    public LiveData<Long> getInvalidationEvent() { return invalidationEvent; }

    /** Call when the user changes a specific song's cover. */
    public void invalidateSong(long songId) {
        int current = prefs.getInt(KEY_PREFIX + songId, 0);
        prefs.edit().putInt(KEY_PREFIX + songId, current + 1).apply();
        invalidationEvent.postValue(songId);
    }

    /**
     * Call after a backup import. Bumps the global version so every song's
     * Glide signature changes and ALL cached cover images are discarded.
     */
    public void invalidateAll() {
        int current = prefs.getInt(KEY_GLOBAL, 0);
        prefs.edit().putInt(KEY_GLOBAL, current + 1).apply();
    }
}
