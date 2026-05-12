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
        try {
            File tempFile = copyToTemp(ctx, songPath);
            if (tempFile == null) {
                callback.onError("No se pudo leer el archivo de audio.");
                return;
            }
            try {
                AudioFile af = AudioFileIO.read(tempFile);
                Tag tag = af.getTagOrCreateAndSetDefault();
                tag.deleteArtworkField();
                tag.setField(ArtworkFactory.createArtworkFromFile(new File(coverPath)));
                af.commit();
                // After commit, write the (now modified) temp file back to the source
                writeBackToSource(ctx, tempFile, songPath);
                callback.onSuccess();
            } finally {
                tempFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "writeCover failed", e);
            callback.onError("Error al escribir la carátula: " + e.getMessage());
        }
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

    private static String extensionFromPath(String path) {
        if (path == null) return ".mp3";
        String lower = path.toLowerCase();
        if (lower.contains(".flac")) return ".flac";
        if (lower.contains(".ogg"))  return ".ogg";
        if (lower.contains(".m4a"))  return ".m4a";
        if (lower.contains(".mp4"))  return ".mp4";
        if (lower.contains(".wav"))  return ".wav";
        if (lower.contains(".aac"))  return ".aac";
        return ".mp3";
    }
}
