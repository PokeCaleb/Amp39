package com.miku.player.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.ArtworkFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes metadata (title, cover art) directly into audio file tags.
 *
 * Strategy for SAF URIs (Micro SD / scoped storage):
 *   1. Copy the audio file from its SAF URI to an internal temp file
 *   2. Modify the temp file's tags with JAudioTagger (operates on real File)
 *   3. Read the modified temp file and write ALL its bytes back to the SAF URI
 *      using "rwt" mode (read-write-truncate) so the file is fully replaced
 *   4. Delete the temp file
 *
 * This is necessary because JAudioTagger only works with java.io.File, not
 * with content:// URIs or file descriptors.
 */
public class TagWriter {

    private static final String TAG = "TagWriter";

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    public interface Result {
        void onSuccess();
        void onError(String message);
    }

    public static void writeTitle(Context ctx, String songPath,
                                  String newTitle, Result callback) {
        if (isOpus(songPath)) {
            callback.onError("Los archivos .opus no son compatibles con guardar el nombre "
                    + "en el archivo original (limitación de la librería de etiquetado) — "
                    + "quedó guardado solo en la app.");
            return;
        }
        // FIX: reescribir el archivo (necesario para grabar el tag) actualiza su
        // fecha de modificación real — por eso aparecía como "más reciente" en el
        // administrador de archivos del sistema. Si el archivo es de acceso directo
        // (no vía SAF/content://), se puede restaurar la fecha original después de
        // escribir. Para archivos accedidos vía SAF esto NO es posible — no existe
        // una API pública de Android para fijar la fecha de modificación de un
        // documento SAF, la controla el proveedor de almacenamiento (ver nota en
        // restoreLastModified()).
        long originalLastModified = getOriginalLastModified(songPath);
        try {
            File tempFile = copyToTemp(ctx, songPath);
            if (tempFile == null) {
                callback.onError("No se pudo leer el archivo de audio.");
                return;
            }
            try {
                AudioFile af = AudioFileIO.read(tempFile);
                Tag tag = af.getTagOrCreateAndSetDefault();
                tag.setField(FieldKey.TITLE, newTitle);
                af.commit();
                // After commit, JAudioTagger may have rewritten tempFile in place
                writeBackToSource(ctx, tempFile, songPath);
                restoreLastModified(songPath, originalLastModified);
                callback.onSuccess();
            } finally {
                tempFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "writeTitle failed", e);
            callback.onError("Error al escribir el título: " + e.getMessage());
        }
    }

    public static void writeCover(Context ctx, String songPath,
                                  String coverPath, Result callback) {
        // FIX: JAudioTagger 3.0.1 (la versión que usa esta app) NO soporta el
        // formato Opus — su propia página oficial lista los formatos
        // soportados (Mp3, Mp4/M4a/M4p, Ogg Vorbis, Flac, Wav, Aif, Dsf, Wma) y
        // Opus no está entre ellos. No es un bug nuestro a nivel de lógica, es
        // una limitación real de la librería de tags. Antes de este chequeo, la
        // app copiaba el archivo completo a un temporal y luego JAudioTagger
        // fallaba al intentar leerlo — trabajo desperdiciado que además terminaba
        // en un mensaje genérico. Ahora se detecta antes y se explica la razón
        // real, sin gastar ese trabajo.
        if (isOpus(songPath)) {
            callback.onError("Los archivos .opus no son compatibles con guardar la carátula "
                    + "en el archivo original (limitación de la librería de etiquetado) — "
                    + "quedó guardada solo en la app.");
            return;
        }
        long originalLastModified = getOriginalLastModified(songPath);
        try {
            File tempFile = copyToTemp(ctx, songPath);
            if (tempFile == null) {
                callback.onError("No se pudo leer el archivo de audio.");
                return;
            }
            try {
                AudioFile af = AudioFileIO.read(tempFile);
                Tag tag = af.getTagOrCreateAndSetDefault();
                // FIX: deleteArtworkField() solo limpia UN campo de artwork por llamada.
                // Canciones que YA traían una carátula embebida (sobre todo si el
                // tagger original guardó varias imágenes — portada + contraportada,
                // por ejemplo) podían quedar con artwork residual o en un estado
                // inconsistente que hacía fallar af.commit() más abajo — esa
                // excepción se silenciaba y la canción quedaba con la carátula
                // guardada solo en la app, nunca en el archivo. Ahora se limpia en
                // bucle hasta que no quede ningún campo de artwork (con un límite de
                // seguridad para no arriesgar un loop infinito si algún formato de
                // tag no responde como se espera).
                int safety = 0;
                while (tag.getFirstArtwork() != null && safety++ < 20) {
                    tag.deleteArtworkField();
                }
                tag.setField(ArtworkFactory.createArtworkFromFile(new File(coverPath)));
                af.commit();
                // After commit, write the (now modified) temp file back to the source
                writeBackToSource(ctx, tempFile, songPath);
                restoreLastModified(songPath, originalLastModified);
                callback.onSuccess();
            } finally {
                tempFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "writeCover failed for " + songPath, e);
            callback.onError("Error al escribir la carátula: " + e.getMessage());
        }
    }

    /** true si la ruta corresponde a un archivo .opus (extensión real, no la de un temporal). */
    public static boolean isOpus(String path) {
        return path != null && path.toLowerCase().contains(".opus");
    }

    /**
     * Captura la fecha de modificación ANTES de reescribir el archivo, para
     * poder restaurarla después. Solo tiene efecto real para rutas de archivo
     * directas — para URIs content:// (SAF) devuelve -1, ya que no hay forma
     * de restaurarla de todas formas (ver restoreLastModified()).
     */
    private static long getOriginalLastModified(String songPath) {
        if (songPath == null || songPath.startsWith("content://")) return -1L;
        return new File(songPath).lastModified();
    }

    /**
     * Restaura la fecha de modificación original tras reescribir el archivo.
     *
     * FIX: reescribir un archivo (necesario para grabar un tag) actualiza su
     * fecha de modificación real a nivel de sistema operativo — así es como
     * funciona cualquier sistema de archivos, no es algo que la app decida.
     * Por eso una canción editada aparecía como "más reciente" en el
     * administrador de archivos del teléfono.
     *
     * Para archivos de acceso DIRECTO (ruta de archivo normal, no vía SAF) esto
     * SÍ se puede corregir: java.io.File.setLastModified() es una API pública
     * soportada, así que se restaura la fecha original inmediatamente después
     * de escribir.
     *
     * Para archivos accedidos vía SAF (content://, que es el caso más común en
     * esta app ya que así es como se accede a la carpeta que el usuario
     * selecciona) esto NO se puede hacer: Android no expone ninguna API pública
     * para fijar la fecha de modificación de un documento SAF — esa fecha la
     * deriva y controla el proveedor de almacenamiento (normalmente
     * ExternalStorageProvider), reflejando la fecha real del archivo subyacente.
     * No hay ninguna llamada de DocumentsContract para "escribir estos bytes
     * pero mantén la fecha vieja". Esto es una limitación de la plataforma, no
     * del código de la app.
     */
    private static void restoreLastModified(String songPath, long originalLastModified) {
        if (originalLastModified <= 0 || songPath == null || songPath.startsWith("content://")) return;
        boolean ok = new File(songPath).setLastModified(originalLastModified);
        if (!ok) Log.w(TAG, "No se pudo restaurar la fecha de modificación de " + songPath);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Copies the audio file (from path or content:// URI) to a temp file
     * in the app's cache directory. Returns the temp File, or null on failure.
     */
    private static File copyToTemp(Context ctx, String songPath) {
        try {
            File tempDir = new File(ctx.getCacheDir(), "tag_temp");
            tempDir.mkdirs();
            String ext = extensionFromPath(songPath);
            File tempFile = new File(tempDir, "audio_edit" + ext);

            if (songPath.startsWith("content://")) {
                Uri uri = Uri.parse(songPath);
                try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                    if (in == null) return null;
                    pipe(in, out);
                }
            } else {
                File src = new File(songPath);
                if (!src.exists()) return null;
                try (InputStream in = new FileInputStream(src);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                    pipe(in, out);
                }
            }
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "copyToTemp failed", e);
            return null;
        }
    }

    /**
     * Writes the modified temp file back to the original source location.
     * For content:// URIs uses "rwt" which truncates first then writes fresh bytes.
     * For regular paths overwrites the file directly.
     */
    private static void writeBackToSource(Context ctx, File tempFile,
                                           String songPath) throws IOException {
        if (songPath.startsWith("content://")) {
            Uri uri = Uri.parse(songPath);
            // "rwt" = read-write-truncate: clears the file then writes from scratch
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "rwt")) {
                if (out == null) throw new IOException("Cannot open URI for writing: " + uri);
                try (FileInputStream fis = new FileInputStream(tempFile)) {
                    pipe(fis, out);
                }
            }
        } else {
            // Regular file path — overwrite directly
            File dest = new File(songPath);
            if (!dest.canWrite()) throw new IOException("File not writable: " + songPath);
            try (FileInputStream fis = new FileInputStream(tempFile);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                pipe(fis, fos);
            }
        }
        Log.d(TAG, "writeBackToSource success: " + songPath);
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    /**
     * FIX: antes esto solo reconocía 6 extensiones a mano y cualquier otra
     * (incluyendo .opus) caía al "return .mp3" del final — es decir, un archivo
     * .opus se copiaba a un archivo temporal llamado "audio_edit.mp3", y luego
     * JAudioTagger intentaba leerlo como si fuera un MP3 real (por la extensión
     * del archivo temporal, no por su contenido real). Esto fallaba 100% de las
     * veces para .opus — no es que JAudioTagger no pudiera leer el opus, es que
     * ni siquiera lo intentaba correctamente: trataba de interpretar bytes de
     * Opus/Ogg como si fueran de MP3. Ahora se usa la extensión REAL del archivo
     * siempre que se pueda determinar, y solo se cae a .mp3 si de verdad no hay
     * ninguna extensión reconocible en la ruta.
     */
    private static String extensionFromPath(String path) {
        if (path == null) return ".mp3";
        String lower = path.toLowerCase();
        int dot = lower.lastIndexOf('.');
        int lastSlash = lower.lastIndexOf('/');
        if (dot > lastSlash && dot >= 0 && dot < lower.length() - 1) {
            String ext = lower.substring(dot); // incluye el punto, ej ".opus"
            if (ext.matches("\\.[a-z0-9]{2,5}")) return ext;
        }
        return ".mp3";
    }
}
