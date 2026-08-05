package com.miku.player.utils;

import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Diálogo reutilizable: al cambiar la carátula de una canción, pregunta si el
 * cambio debe escribirse también en el archivo de audio original, o solo
 * guardarse dentro de la app.
 *
 * Para archivos .opus se salta el diálogo directamente y se aplica "solo
 * app" — ya sabemos que la librería de tags (JAudioTagger) no soporta ese
 * formato, así que preguntar sería ofrecer una opción garantizada a fallar.
 */
public final class CoverWriteConfirm {

    private CoverWriteConfirm() {}

    public interface DecisionCallback {
        /** writeToFile = true si el usuario eligió "Sí" (escribir también en el archivo). */
        void onDecision(boolean writeToFile);
    }

    public static void ask(Context ctx, String songPath, DecisionCallback callback) {
        if (TagWriter.isOpus(songPath)) {
            callback.onDecision(false);
            return;
        }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Cambiar carátula")
                .setMessage("¿Quiere realizar el cambio en el archivo original?\n\n"
                        + "Sí: se cambia dentro de la app y en el archivo de audio.\n"
                        + "No: se cambia solo dentro de la app, el archivo original no se toca.")
                .setPositiveButton("Sí", (d, w) -> callback.onDecision(true))
                .setNegativeButton("No", (d, w) -> callback.onDecision(false))
                .setCancelable(false)
                .show();
    }
}
