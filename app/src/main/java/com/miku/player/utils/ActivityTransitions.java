package com.miku.player.utils;

import android.app.Activity;
import com.miku.player.R;

/**
 * Transiciones de pantalla consistentes para toda la app.
 *
 * Antes, casi ninguna navegación tenía transición — abrir cualquier pantalla
 * (Playlists, Favoritos, Detalle de playlist, Configuración, el Reproductor)
 * era un corte instantáneo. Solo PlayerActivity tenía una transición, y
 * únicamente al cerrarse (con las animaciones stock de Android).
 *
 * Ahora toda la navegación usa el mismo par de transiciones sutiles
 * (slide + fade), consistente en ambas direcciones y en toda la app.
 */
public final class ActivityTransitions {

    private ActivityTransitions() {}

    /** Llamar justo después de startActivity() al abrir una pantalla nueva ("hacia adelante"). */
    public static void forward(Activity activity) {
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /** Llamar justo después de finish() al volver a la pantalla anterior ("hacia atrás"). */
    public static void back(Activity activity) {
        activity.overridePendingTransition(R.anim.slide_in_left_fade, R.anim.slide_out_right_fade);
    }
}
