package com.miku.player.utils;

import android.content.Context;
import android.content.SharedPreferences;
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

    /** Call when the user changes a specific song's cover. */
    public void invalidateSong(long songId) {
        int current = prefs.getInt(KEY_PREFIX + songId, 0);
        prefs.edit().putInt(KEY_PREFIX + songId, current + 1).apply();
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
