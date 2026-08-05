package com.miku.player.utils;

import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * Animaciones reutilizables para elementos de UI compartidos entre varias pantallas.
 * Centralizado aquí para no duplicar la misma lógica de animación en cada Activity.
 */
public final class ViewAnimUtils {

    private ViewAnimUtils() {}

    /**
     * Muestra u oculta el mini-player con un slide-up + fade, en vez del corte
     * instantáneo de setVisibility(GONE/VISIBLE) que había antes.
     *
     * Es idempotente a propósito: el LiveData de la canción actual dispara este
     * observer en CADA cambio de canción, no solo cuando el mini-player aparece
     * o desaparece — sin este chequeo, cambiar de canción reiniciaría la animación
     * de entrada cada vez, aunque el mini-player ya estuviera visible.
     */
    public static void animateMiniPlayerVisibility(View miniPlayer, boolean show) {
        boolean isCurrentlyShown = miniPlayer.getVisibility() == View.VISIBLE
                && miniPlayer.getAlpha() > 0.01f;
        if (show == isCurrentlyShown) return;

        miniPlayer.animate().cancel();
        float distance = miniPlayer.getHeight() > 0
                ? miniPlayer.getHeight()
                : 72f * miniPlayer.getResources().getDisplayMetrics().density;

        if (show) {
            miniPlayer.setTranslationY(distance);
            miniPlayer.setAlpha(0f);
            miniPlayer.setVisibility(View.VISIBLE);
            miniPlayer.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            miniPlayer.animate()
                    .translationY(distance)
                    .alpha(0f)
                    .setDuration(180)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> miniPlayer.setVisibility(View.GONE))
                    .start();
        }
    }

    /**
     * Pequeño "pulso" de feedback táctil: el botón se encoge un poco y rebota de
     * vuelta. Se suma al ripple que ya existe (selectableItemBackgroundBorderless)
     * para dar una sensación más "física" al tocar los controles principales
     * (play/pause, favorito, shuffle, repeat, siguiente/anterior, mini-player).
     */
    public static void pulse(View view) {
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate()
                .scaleX(0.82f).scaleY(0.82f)
                .setDuration(80)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f))
                        .start())
                .start();
    }
}
