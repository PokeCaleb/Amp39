package com.miku.player.utils;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.ArtworkFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Escribe metadatos (título, carátula) directamente en las etiquetas del archivo de audio.
 *
 * Estrategia segura (evita dejar un archivo truncado/corrupto):
 *   1. Copia el original a un archivo de RESPALDO temporal (único por operación).
 *   2. Copia el respaldo a un archivo de TRABAJO y modifica sus etiquetas con JAudioTagger.
 *   3. Verifica que el archivo de trabajo se pueda volver a leer.
 *   4. Escribe el resultado sobre el original y verifica que el tamaño escrito coincida.
 *   5. Si CUALQUIER paso de escritura falla, restaura el original desde el respaldo.
 *      Si ni siquiera la restauración funciona, el respaldo se conserva en
 *      filesDir/tag_recovery y se avisa al usuario con la ruta.
 *
 * Las operaciones se serializan (un solo cerrojo) para que dos ediciones simultáneas
 * no compartan archivos temporales.
 */
public class TagWriter {

    private static final String TAG = "TagWriter";
    private static final Object LOCK = new Object();

    /** Extensiones que JAudioTagger 3.0.1 sabe leer/escribir. Cualquier otra se rechaza. */
    private static final String[] SUPPORTED = {
            ".mp3", ".m4a", ".mp4", ".m4p", ".ogg", ".flac",
            ".wav", ".aif", ".aiff", ".dsf", ".wma"
    };

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    public interface Result {
        void onSuccess();
        void onError(String message);
    }

    private interface TagEdit {
        void apply(Tag tag) throws Exception;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public static void writeTitle(Context ctx, String songPath,
                                  String newTitle, Result callback) {
        writeTitle(ctx, songPath, songPath, newTitle, callback);
    }

    /**
     * @param formatPath ruta/nombre que contiene la extensión real (para elegir el formato)
     * @param ioTarget   dónde se lee y escribe de verdad (ruta, URI SAF o URI de MediaStore)
     */
    public static void writeTitle(Context ctx, String formatPath, String ioTarget,
                                  String newTitle, Result callback) {
        edit(ctx, ioTarget, formatPath, callback,
                "Los archivos .opus no son compatibles con guardar el nombre "
                        + "en el archivo original (limitación de la librería de etiquetado) — "
                        + "quedó guardado solo en la app.",
                "el título",
                tag -> tag.setField(FieldKey.TITLE, newTitle));
    }

    public static void writeCover(Context ctx, String songPath,
                                  String coverPath, Result callback) {
        writeCover(ctx, songPath, songPath, coverPath, callback);
    }

    public static void writeCover(Context ctx, String formatPath, String ioTarget,
                                  String coverPath, Result callback) {
        edit(ctx, ioTarget, formatPath, callback,
                "Los archivos .opus no son compatibles con guardar la carátula "
                        + "en el archivo original (limitación de la librería de etiquetado) — "
                        + "quedó guardada solo en la app.",
                "la carátula",
                tag -> {
                    // deleteArtworkField() solo limpia UN campo por llamada; se repite con tope.
                    int safety = 0;
                    while (tag.getFirstArtwork() != null && safety++ < 20) {
                        tag.deleteArtworkField();
                    }
                    tag.setField(ArtworkFactory.createArtworkFromFile(new File(coverPath)));
                });
    }

    /** true si la ruta corresponde a un archivo .opus (por la extensión real del nombre). */
    public static boolean isOpus(String path) {
        if (path == null) return false;
        return Uri.decode(path).toLowerCase(Locale.ROOT).endsWith(".opus");
    }

    // ── Núcleo ────────────────────────────────────────────────────────────────

    /** songPath = destino real de E/S; formatPath = de dónde sale la extensión del formato. */
    private static void edit(Context ctx, String songPath, String formatPath, Result callback,
                             String opusMessage, String what, TagEdit edit) {
        if (songPath == null || songPath.isEmpty()) {
            callback.onError("No se pudo leer el archivo de audio.");
            return;
        }
        if (isOpus(formatPath)) {
            callback.onError(opusMessage);
            return;
        }
        String ext = extensionFromPath(formatPath);
        if (ext == null) {
            callback.onError("Formato de audio no compatible con edición de etiquetas — "
                    + "el cambio quedó guardado solo en la app.");
            return;
        }

        // Nunca editar archivos de los datos privados de la app (p. ej. una ruta
        // manipulada que venga de una base restaurada).
        if (!songPath.startsWith("content://")) {
            try {
                String canonical = new File(songPath).getCanonicalPath();
                String dataDir = ctx.getDataDir().getCanonicalPath() + File.separator;
                if (canonical.startsWith(dataDir)) {
                    callback.onError("Ruta de archivo no permitida.");
                    return;
                }
            } catch (IOException e) {
                callback.onError("No se pudo verificar la ruta del archivo.");
                return;
            }
        }

        String errorMessage = null;
        synchronized (LOCK) {
            long originalLastModified = getOriginalLastModified(songPath);
            File dir = new File(ctx.getCacheDir(), "tag_temp");
            dir.mkdirs();
            purgeOldTemps(dir);

            File backup = null;
            File work = null;
            boolean keepBackup = false;
            try {
                long srcSize = sourceSize(ctx, songPath);
                if (srcSize <= 0) throw new IOException("No se pudo leer el archivo de audio.");
                // Se necesitan dos copias temporales (respaldo + trabajo) más margen.
                if (dir.getUsableSpace() < srcSize * 2 + 8L * 1024 * 1024) {
                    throw new IOException("Espacio insuficiente para editar el archivo.");
                }

                backup = File.createTempFile("orig_", ext, dir);
                work = File.createTempFile("work_", ext, dir);

                copyFromSource(ctx, songPath, backup);
                if (backup.length() != srcSize) {
                    throw new IOException("La copia del archivo quedó incompleta.");
                }
                copyFile(backup, work);

                // ── Editar etiquetas en el archivo de trabajo (el original sigue intacto) ──
                AudioFile af = AudioFileIO.read(work);
                Tag tag = af.getTagOrCreateAndSetDefault();
                edit.apply(tag);
                af.commit();

                // ── Verificar que el resultado sea un audio legible ──
                if (!work.exists() || work.length() <= 0) {
                    throw new IOException("El archivo editado quedó vacío.");
                }
                AudioFileIO.read(work);

                // ── Escribir sobre el original; si falla, restaurar ──
                try {
                    writeBackToSource(ctx, work, songPath);
                    long written = sourceSize(ctx, songPath);
                    if (written != work.length()) {
                        throw new IOException("Verificación fallida: tamaño escrito distinto.");
                    }
                } catch (Exception writeError) {
                    Log.e(TAG, "Escritura falló, restaurando original", writeError);
                    try {
                        writeBackToSource(ctx, backup, songPath);
                    } catch (Exception restoreError) {
                        Log.e(TAG, "Restauración falló", restoreError);
                        keepBackup = true;
                        String saved = saveRecovery(ctx, backup, ext);
                        throw new IOException("Error grave: no se pudo escribir ni restaurar el "
                                + "archivo. Copia de seguridad en: " + saved);
                    }
                    throw writeError;
                }

                restoreLastModified(songPath, originalLastModified);
            } catch (Exception e) {
                Log.e(TAG, "Edición de " + what + " falló para " + songPath, e);
                errorMessage = "Error al escribir " + what + ": " + e.getMessage();
            } finally {
                if (work != null) work.delete();
                if (backup != null && !keepBackup) backup.delete();
            }
        }

        if (errorMessage != null) callback.onError(errorMessage);
        else callback.onSuccess();
    }

    // ── Fechas de modificación ────────────────────────────────────────────────

    private static long getOriginalLastModified(String songPath) {
        if (songPath == null || songPath.startsWith("content://")) return -1L;
        return new File(songPath).lastModified();
    }

    /**
     * Restaura la fecha de modificación original. Solo es posible con rutas de archivo
     * directas; para documentos SAF Android no expone ninguna API pública.
     */
    private static void restoreLastModified(String songPath, long originalLastModified) {
        if (originalLastModified <= 0 || songPath == null || songPath.startsWith("content://")) return;
        boolean ok = new File(songPath).setLastModified(originalLastModified);
        if (!ok) Log.w(TAG, "No se pudo restaurar la fecha de modificación de " + songPath);
    }

    // ── Helpers de E/S ────────────────────────────────────────────────────────

    private static long sourceSize(Context ctx, String songPath) throws IOException {
        if (songPath.startsWith("content://")) {
            try (ParcelFileDescriptor pfd =
                         ctx.getContentResolver().openFileDescriptor(Uri.parse(songPath), "r")) {
                if (pfd == null) throw new IOException("No se pudo abrir el archivo.");
                return pfd.getStatSize();
            }
        }
        File f = new File(songPath);
        if (!f.exists()) throw new IOException("El archivo ya no existe.");
        return f.length();
    }

    private static void copyFromSource(Context ctx, String songPath, File dest) throws IOException {
        InputStream in = songPath.startsWith("content://")
                ? ctx.getContentResolver().openInputStream(Uri.parse(songPath))
                : new FileInputStream(new File(songPath));
        if (in == null) throw new IOException("No se pudo abrir el archivo de origen.");
        try (InputStream src = in; FileOutputStream out = new FileOutputStream(dest)) {
            pipe(src, out);
            out.getFD().sync();
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            pipe(in, out);
            out.getFD().sync();
        }
    }

    /**
     * Escribe el archivo temporal de vuelta al origen. Para content:// usa "rwt"
     * (truncar y escribir). Esta es la única fase destructiva, por eso quien la
     * llama conserva un respaldo para restaurar si algo falla.
     */
    private static void writeBackToSource(Context ctx, File tempFile,
                                          String songPath) throws IOException {
        if (songPath.startsWith("content://")) {
            Uri uri = Uri.parse(songPath);
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "rwt")) {
                if (out == null) throw new IOException("No se pudo abrir para escritura: " + uri);
                try (FileInputStream fis = new FileInputStream(tempFile)) {
                    pipe(fis, out);
                }
                out.flush();
            }
        } else {
            File dest = new File(songPath);
            if (!dest.canWrite()) throw new IOException("Archivo sin permiso de escritura: " + songPath);
            try (FileInputStream fis = new FileInputStream(tempFile);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                pipe(fis, fos);
                fos.getFD().sync();
            }
        }
    }

    private static String saveRecovery(Context ctx, File backup, String ext) {
        try {
            File dir = new File(ctx.getFilesDir(), "tag_recovery");
            dir.mkdirs();
            File target = new File(dir, "recovery_" + System.currentTimeMillis() + ext);
            copyFile(backup, target);
            return target.getAbsolutePath();
        } catch (Exception e) {
            return backup.getAbsolutePath();
        }
    }

    private static void purgeOldTemps(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long limit = System.currentTimeMillis() - 60L * 60 * 1000;
        for (File f : files) if (f.lastModified() < limit) f.delete();
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    /**
     * Devuelve la extensión REAL (con punto) si es un formato soportado; null si no.
     * Antes caía a ".mp3" por defecto, lo que podía hacer que JAudioTagger interpretara
     * un FLAC/M4A como MP3 y escribiera un tag ID3 dentro de un archivo que no lo es.
     */
    private static String extensionFromPath(String path) {
        if (path == null) return null;
        String lower = Uri.decode(path).toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        int lastSlash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf(':'));
        if (dot <= lastSlash || dot >= lower.length() - 1) return null;
        String ext = lower.substring(dot);
        for (String s : SUPPORTED) if (s.equals(ext)) return ext;
        return null;
    }
}
