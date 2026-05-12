package com.miku.player.ui.backup;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.miku.player.data.repository.AppDatabase;
import com.miku.player.ui.main.MainActivity;
import com.miku.player.utils.CoverSignatureManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Handles export (to Downloads via MediaStore or File) and import (from a user-picked URI).
 *
 * Export format: a ZIP file at Downloads/MikuPlayer/mikuplayer_backup_YYYYMMDD_HHmmss.zip
 *   database/miku_player.db   — full Room database (WAL checkpointed)
 *   covers/<name>.jpg         — user-set cover images
 *   prefs.properties          — folder URI and name
 *
 * Android 10+ uses MediaStore.Downloads (no WRITE permission needed).
 * Android 9-  uses File access (needs WRITE_EXTERNAL_STORAGE).
 */
public class BackupManager {

    static final String BACKUP_FOLDER    = "MikuPlayer";
    private static final String DB_FILE  = "miku_player.db";
    private static final String COVERS   = "covers";
    private static final String PREFS_F  = "prefs.properties";
    private static final String KEY_TS   = "last_backup_timestamp";

    // ZIP entry names
    private static final String ZIP_DB      = "database/miku_player.db";
    private static final String ZIP_COVERS  = "covers/";
    private static final String ZIP_PREFS   = "prefs.properties";

    private final Context ctx;

    public BackupManager(Context context) {
        this.ctx = context.getApplicationContext();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Creates a ZIP backup and saves it to Downloads/MikuPlayer/.
     * Returns the file name so it can be shown to the user.
     */
    public String export() throws IOException {
        // 1. Checkpoint WAL
        checkpointWal();

        String fileName = "mikuplayer_backup_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())
                + ".zip";

        // 2. Open output stream to Downloads — method differs by API level
        OutputStream out = openDownloadsOutputStream(fileName);

        // 3. Write ZIP
        ZipOutputStream zos = new ZipOutputStream(out);
        try {
            // Database
            addFileToZip(zos, ctx.getDatabasePath(DB_FILE), ZIP_DB);

            // Covers
            File coversDir = new File(ctx.getFilesDir(), COVERS);
            if (coversDir.exists()) {
                File[] covers = coversDir.listFiles();
                if (covers != null) {
                    for (File cover : covers) {
                        addFileToZip(zos, cover, ZIP_COVERS + cover.getName());
                    }
                }
            }

            // Preferences
            byte[] prefsBytes = buildPrefsBytes();
            zos.putNextEntry(new ZipEntry(ZIP_PREFS));
            zos.write(prefsBytes);
            zos.closeEntry();

        } finally {
            zos.finish();
            zos.close();
        }

        // 4. Record timestamp
        String ts = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TS, ts).apply();

        return fileName;
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Restores from a ZIP URI chosen by the user via the system file picker.
     * Returns the number of cover images restored.
     */
    public int importFromUri(Uri zipUri) throws IOException {
        InputStream rawIn = ctx.getContentResolver().openInputStream(zipUri);
        if (rawIn == null) throw new IOException("No se pudo abrir el archivo seleccionado.");

        // Close DB BEFORE overwriting its file
        AppDatabase.resetInstance();

        File dbFile     = ctx.getDatabasePath(DB_FILE);
        File coversDir  = new File(ctx.getFilesDir(), COVERS);
        dbFile.getParentFile().mkdirs();

        // Clear old covers so removed ones don't linger
        deleteDir(coversDir);
        coversDir.mkdirs();

        int restoredCovers = 0;
        boolean dbRestored = false;

        ZipInputStream zis = new ZipInputStream(rawIn);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            if (ZIP_DB.equals(name)) {
                writeStreamToFile(zis, dbFile);
                // Remove stale WAL/SHM so Room opens the DB cleanly
                new File(dbFile.getPath() + "-wal").delete();
                new File(dbFile.getPath() + "-shm").delete();
                dbRestored = true;
            } else if (name.startsWith(ZIP_COVERS)) {
                String coverName = name.substring(ZIP_COVERS.length());
                if (!coverName.isEmpty()) {
                    writeStreamToFile(zis, new File(coversDir, coverName));
                    restoredCovers++;
                }
            } else if (ZIP_PREFS.equals(name)) {
                restorePrefsFromStream(zis);
            }
            zis.closeEntry();
        }
        zis.close();

        if (!dbRestored) {
            throw new IOException(
                "El archivo seleccionado no es un respaldo válido de MikuPlayer.");
        }

        // Fix any customCoverPath that was stored as an external URI (content://).
        // After restore those URIs are invalid; remap them to local files if they exist.
        fixCoverPathsAfterRestore(coversDir);

        // Bump the global Glide signature so ALL cached cover images are discarded.
        // Without this, Glide serves stale cached bitmaps from the previous install
        // even though the files on disk have been replaced by the restored ones.
        CoverSignatureManager.getInstance(ctx).invalidateAll();

        return restoredCovers;
    }

    // ── Info ──────────────────────────────────────────────────────────────────

    public String getLastBackupTime() {
        return ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TS, null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Opens an OutputStream into Downloads/MikuPlayer/<fileName>.
     * Uses MediaStore on Android 10+ (API 29+) — no WRITE permission needed.
     * Uses File directly on Android 9 and below.
     */
    private OutputStream openDownloadsOutputStream(String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — Scoped Storage, use MediaStore
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_FOLDER);
            Uri uri = ctx.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                throw new IOException(
                    "No se pudo crear el archivo en Descargas. "
                    + "Verifica que el almacenamiento esté disponible.");
            }
            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            if (os == null) throw new IOException("No se pudo abrir el archivo para escritura.");
            return os;
        } else {
            // Android 9 and below — direct File access
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("No se pudo crear la carpeta Descargas/" + BACKUP_FOLDER);
            }
            return new FileOutputStream(new File(dir, fileName));
        }
    }

    private void checkpointWal() {
        try {
            AppDatabase.getInstance(ctx)
                    .getOpenHelper().getWritableDatabase()
                    .execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (Exception ignored) {}
    }

    private byte[] buildPrefsBytes() throws IOException {
        SharedPreferences prefs = ctx.getSharedPreferences(
                MainActivity.PREFS, Context.MODE_PRIVATE);
        Properties props = new Properties();
        String folderUri  = prefs.getString(MainActivity.KEY_FOLDER_URI,  "");
        String folderName = prefs.getString(MainActivity.KEY_FOLDER_NAME, "");
        if (folderUri  != null) props.setProperty(MainActivity.KEY_FOLDER_URI,  folderUri);
        if (folderName != null) props.setProperty(MainActivity.KEY_FOLDER_NAME, folderName);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        props.store(bos, "MikuPlayer backup");
        return bos.toByteArray();
    }

    private void restorePrefsFromStream(InputStream in) throws IOException {
        // Read without closing the parent ZipInputStream
        byte[] buf = new byte[4096];
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);

        Properties props = new Properties();
        props.load(new java.io.ByteArrayInputStream(bos.toByteArray()));

        SharedPreferences.Editor ed = ctx
                .getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        if (props.containsKey(MainActivity.KEY_FOLDER_URI))
            ed.putString(MainActivity.KEY_FOLDER_URI,
                    props.getProperty(MainActivity.KEY_FOLDER_URI));
        if (props.containsKey(MainActivity.KEY_FOLDER_NAME))
            ed.putString(MainActivity.KEY_FOLDER_NAME,
                    props.getProperty(MainActivity.KEY_FOLDER_NAME));
        ed.apply();
    }

    private static void addFileToZip(ZipOutputStream zos, File file, String entryName)
            throws IOException {
        if (file == null || !file.exists()) return;
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            pipe(fis, zos);
        }
        zos.closeEntry();
    }

    private static void writeStreamToFile(InputStream src, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            pipe(src, fos);
        }
    }

    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f);
            else f.delete();
        }
        dir.delete();
    }
    /**
     * After a restore, songs whose customCoverPath is a content:// URI (set before
     * the copy-on-assign fix) will no longer be readable. This method scans the songs
     * table and, for each such song, checks whether a local file cover_<id>.jpg exists
     * in the restored covers dir and updates the path accordingly.
     */
    private void fixCoverPathsAfterRestore(File coversDir) {
        try {
            android.database.sqlite.SQLiteDatabase db =
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    ctx.getDatabasePath(DB_FILE).getAbsolutePath(),
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);

            android.database.Cursor cursor = db.rawQuery(
                "SELECT id, customCoverPath FROM songs WHERE customCoverPath IS NOT NULL", null);

            while (cursor.moveToNext()) {
                long id   = cursor.getLong(0);
                String cp = cursor.getString(1);
                if (cp == null) continue;

                // If path is a content:// URI or doesn't exist as a file, try the local copy
                boolean needsFix = cp.startsWith("content://") || !new java.io.File(cp).exists();
                if (needsFix) {
                    java.io.File localFile = new java.io.File(coversDir, "cover_" + id + ".jpg");
                    if (localFile.exists()) {
                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put("customCoverPath", localFile.getAbsolutePath());
                        db.update("songs", cv, "id = ?",
                                new String[]{String.valueOf(id)});
                    }
                }
            }
            cursor.close();
            db.close();
        } catch (Exception e) {
            android.util.Log.e("BackupManager", "fixCoverPathsAfterRestore failed", e);
        }
    }

    /**
     * Imports only the database (playlists, favorites, song order) from the ZIP,
     * ignoring any cover images in the backup.
     *
     * After restore, ALL customCoverPath values are cleared from the DB so that
     * the next folder scan extracts covers fresh from the audio files themselves
     * using MediaMetadataRetriever.
     */
    public void importFromUriListsOnly(Uri zipUri) throws IOException {
        InputStream rawIn = ctx.getContentResolver().openInputStream(zipUri);
        if (rawIn == null) throw new IOException("No se pudo abrir el archivo seleccionado.");

        AppDatabase.resetInstance();

        File dbFile = ctx.getDatabasePath(DB_FILE);
        dbFile.getParentFile().mkdirs();

        boolean dbRestored = false;

        ZipInputStream zis = new ZipInputStream(rawIn);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            if (ZIP_DB.equals(name)) {
                writeStreamToFile(zis, dbFile);
                new File(dbFile.getPath() + "-wal").delete();
                new File(dbFile.getPath() + "-shm").delete();
                dbRestored = true;
            } else if (ZIP_PREFS.equals(name)) {
                restorePrefsFromStream(zis);
            }
            // Intentionally skip covers/* entries
            zis.closeEntry();
        }
        zis.close();

        if (!dbRestored) {
            throw new IOException(
                "El archivo seleccionado no es un respaldo válido de MikuPlayer.");
        }

        // Clear ALL customCoverPath values so the next scan extracts covers
        // directly from the audio files using MediaMetadataRetriever.
        clearAllCoverPaths();

        // Also delete any previously cached cover files so they get re-extracted
        deleteDir(new File(ctx.getFilesDir(), COVERS));

        // Invalidate all Glide signatures so stale cached covers are discarded
        CoverSignatureManager.getInstance(ctx).invalidateAll();
    }

    /**
     * Sets customCoverPath = NULL for every song in the database.
     * Called after a lists-only restore so covers get re-extracted on next scan.
     */
    private void clearAllCoverPaths() {
        try {
            android.database.sqlite.SQLiteDatabase db =
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    ctx.getDatabasePath(DB_FILE).getAbsolutePath(),
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.putNull("customCoverPath");
            db.update("songs", cv, null, null);
            db.close();
        } catch (Exception e) {
            android.util.Log.e("BackupManager", "clearAllCoverPaths failed", e);
        }
    }


}
