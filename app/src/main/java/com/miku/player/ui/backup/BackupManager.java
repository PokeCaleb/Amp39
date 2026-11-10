package com.miku.player.ui.backup;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.miku.player.data.repository.AppDatabase;
import com.miku.player.ui.main.MainActivity;
import com.miku.player.utils.CoverSignatureManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Exportación (a Descargas) e importación (desde un URI elegido por el usuario).
 *
 * Formato del ZIP:
 *   database/miku_player.db   — base Room completa (WAL ya volcado)
 *   covers/<nombre>.jpg       — portadas
 *   prefs.properties          — carpeta de música elegida
 *
 * La restauración es defensiva (el ZIP se trata como NO confiable):
 *   1. Se extrae a una carpeta temporal con límites de tamaño y nombres validados
 *      (sin rutas con "..", barras ni caracteres raros → no hay "zip slip").
 *   2. Se valida la base: integridad, tablas esperadas y versión no mayor a la soportada.
 *   3. Se sanean las rutas de portada de la base (solo apuntan a covers/ de la app).
 *   4. Solo entonces se cierra la base real y se intercambian archivos, con respaldo
 *      del estado anterior y reversión si algo falla.
 * Si cualquier paso de 1-3 falla, la app queda exactamente como estaba.
 */
public class BackupManager {

    private static final String TAG = "BackupManager";

    static final String BACKUP_FOLDER    = "Amp39";
    private static final String DB_FILE  = "miku_player.db";
    private static final String COVERS   = "covers";
    private static final String KEY_TS   = "last_backup_timestamp";

    private static final String ZIP_DB      = "database/miku_player.db";
    private static final String ZIP_COVERS  = "covers/";
    private static final String ZIP_PREFS   = "prefs.properties";

    /** Versión de esquema Room más alta que esta build sabe abrir (ver AppDatabase). */
    private static final int MAX_DB_VERSION = 11;

    private static final long MAX_DB_BYTES     = 1024L * 1024 * 1024;       // 1 GB
    private static final long MAX_COVER_BYTES  = 30L * 1024 * 1024;         // 30 MB
    private static final long MAX_PREFS_BYTES  = 64L * 1024;                // 64 KB
    private static final long MAX_TOTAL_BYTES  = 2L * 1024 * 1024 * 1024;   // 2 GB
    private static final int  MAX_ENTRIES      = 50_000;
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private final Context ctx;

    public BackupManager(Context context) {
        this.ctx = context.getApplicationContext();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Crea un respaldo ZIP en Descargas/Amp39/ y devuelve el nombre del archivo.
     * Si algo falla, el archivo parcial se elimina (no queda un ZIP corrupto).
     */
    public String export() throws IOException {
        // Si el volcado del WAL falla se aborta: un .db sin -wal sería un respaldo viejo.
        checkpointWal();

        String fileName = "amp39_backup_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".zip";

        DownloadTarget target = openDownloadTarget(fileName);
        try {
            try (ZipOutputStream zos = new ZipOutputStream(target.out)) {
                addFileToZip(zos, ctx.getDatabasePath(DB_FILE), ZIP_DB);

                File coversDir = new File(ctx.getFilesDir(), COVERS);
                File[] covers = coversDir.listFiles();
                if (covers != null) {
                    for (File cover : covers) {
                        if (cover.isFile() && !cover.getName().endsWith(".tmp")) {
                            addFileToZip(zos, cover, ZIP_COVERS + cover.getName());
                        }
                    }
                }

                zos.putNextEntry(new ZipEntry(ZIP_PREFS));
                zos.write(buildPrefsBytes());
                zos.closeEntry();
            }
            target.commit();
        } catch (IOException | RuntimeException e) {
            target.discard();
            throw e;
        }

        String ts = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TS, ts).apply();

        return fileName;
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /** Restaura base + portadas + preferencias. Devuelve cuántas portadas se restauraron. */
    public int importFromUri(Uri zipUri) throws IOException {
        return restore(zipUri, false);
    }

    /**
     * Restaura solo la base (playlists, favoritos, orden), ignorando las portadas del
     * respaldo; todas las rutas de portada se limpian para que el próximo escaneo las
     * vuelva a extraer de los archivos de audio.
     */
    public void importFromUriListsOnly(Uri zipUri) throws IOException {
        restore(zipUri, true);
    }

    private static class Staged {
        File db;
        File covers;           // carpeta temporal con las portadas extraídas
        Properties prefs;
        int coverCount;
    }

    private int restore(Uri zipUri, boolean listsOnly) throws IOException {
        File stage = new File(ctx.getCacheDir(), "restore_stage");
        deleteDir(stage);
        if (!stage.mkdirs()) throw new IOException("No se pudo preparar la carpeta temporal.");

        try {
            // 1) Extraer y validar TODO fuera de los datos reales de la app.
            Staged st = extractToStage(zipUri, stage, listsOnly);

            File coversDir = new File(ctx.getFilesDir(), COVERS);
            // 2) Validar la base y sanear rutas (aún sobre la copia temporal).
            validateAndSanitizeDb(st, coversDir, listsOnly);

            // 3) Intercambio con respaldo y reversión.
            swapIn(st, stage, coversDir);

            // 4) Preferencias (después de que todo salió bien).
            applyPrefs(st.prefs);

            // Descartar bitmaps cacheados por Glide de la instalación anterior.
            CoverSignatureManager.getInstance(ctx).invalidateAll();
            return st.coverCount;
        } finally {
            deleteDir(stage);
        }
    }

    private Staged extractToStage(Uri zipUri, File stage, boolean listsOnly) throws IOException {
        InputStream rawIn = ctx.getContentResolver().openInputStream(zipUri);
        if (rawIn == null) throw new IOException("No se pudo abrir el archivo seleccionado.");

        Staged st = new Staged();
        File stageCovers = new File(stage, "covers_new");
        long[] total = {0};
        int entries = 0;

        try (ZipInputStream zis = new ZipInputStream(rawIn)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("El respaldo tiene demasiados archivos.");
                if (entry.isDirectory()) continue;
                String name = entry.getName();

                if (ZIP_DB.equals(name)) {
                    File out = new File(stage, "restored.db");
                    copyLimited(zis, out, MAX_DB_BYTES, total);
                    st.db = out;
                } else if (!listsOnly && name.startsWith(ZIP_COVERS)) {
                    String coverName = name.substring(ZIP_COVERS.length());
                    // Solo nombres planos y seguros: bloquea "..", barras y rutas absolutas.
                    if (!SAFE_NAME.matcher(coverName).matches()) {
                        Log.w(TAG, "Entrada de portada ignorada por nombre no válido: " + coverName);
                        continue;
                    }
                    stageCovers.mkdirs();
                    File out = new File(stageCovers, coverName);
                    if (!isInside(stageCovers, out)) continue;
                    copyLimited(zis, out, MAX_COVER_BYTES, total);
                    st.coverCount++;
                } else if (ZIP_PREFS.equals(name)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    long read = 0;
                    while ((n = zis.read(buf)) != -1) {
                        read += n;
                        if (read > MAX_PREFS_BYTES) throw new IOException("prefs.properties demasiado grande.");
                        bos.write(buf, 0, n);
                    }
                    Properties p = new Properties();
                    p.load(new ByteArrayInputStream(bos.toByteArray()));
                    st.prefs = p;
                }
                // Cualquier otra entrada se ignora.
            }
        }

        if (st.db == null) {
            throw new IOException("El archivo seleccionado no es un respaldo válido de Amp39.");
        }
        st.covers = stageCovers;
        return st;
    }

    /**
     * Abre la base temporal y comprueba: versión soportada, integridad y tablas
     * esperadas. Después limpia las rutas de portada para que SOLO puedan apuntar a
     * archivos dentro de covers/ de esta app (o a nada).
     */
    private void validateAndSanitizeDb(Staged st, File finalCoversDir, boolean listsOnly)
            throws IOException {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(st.db.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE);

            int version = db.getVersion();
            if (version > MAX_DB_VERSION) {
                throw new IOException("El respaldo fue creado con una versión más reciente de la "
                        + "app. Actualiza la app para poder restaurarlo.");
            }
            if (version < 1) throw new IOException("El respaldo no contiene una base válida.");

            try (Cursor c = db.rawQuery("PRAGMA integrity_check", null)) {
                if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) {
                    throw new IOException("La base de datos del respaldo está dañada.");
                }
            }
            for (String table : new String[]{"songs", "playlists", "playlist_songs"}) {
                try (Cursor c = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                        new String[]{table})) {
                    if (!c.moveToFirst()) {
                        throw new IOException("El respaldo no es válido (falta la tabla " + table + ").");
                    }
                }
            }

            db.beginTransaction();
            try {
                sanitizeCoverColumn(db, "songs", "customCoverPath", "cover_",
                        st.covers, finalCoversDir, listsOnly);
                sanitizeCoverColumn(db, "playlists", "coverPath", "playlist_cover_",
                        st.covers, finalCoversDir, listsOnly);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("No se pudo validar el respaldo: " + e.getMessage());
        } finally {
            if (db != null) try { db.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Cada ruta de portada se reemplaza por la ruta LOCAL esperada (<prefijo><id>.jpg
     * dentro de covers/) si ese archivo vino en el respaldo; si no, queda en NULL.
     * Así ninguna ruta de la base puede apuntar fuera de la carpeta de portadas.
     */
    private void sanitizeCoverColumn(SQLiteDatabase db, String table, String column,
                                     String filePrefix, File stagedCovers, File finalCoversDir,
                                     boolean listsOnly) {
        try (Cursor c = db.rawQuery("SELECT id FROM " + table
                + " WHERE " + column + " IS NOT NULL", null)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String fileName = filePrefix + id + ".jpg";
                ContentValues cv = new ContentValues();
                if (!listsOnly && new File(stagedCovers, fileName).isFile()) {
                    cv.put(column, new File(finalCoversDir, fileName).getAbsolutePath());
                } else {
                    cv.putNull(column);
                }
                db.update(table, cv, "id = ?", new String[]{String.valueOf(id)});
            }
        }
    }

    /** Intercambia los archivos reales por los validados; si algo falla, revierte. */
    private void swapIn(Staged st, File stage, File coversDir) throws IOException {
        File dbFile = ctx.getDatabasePath(DB_FILE);
        File parent = dbFile.getParentFile();
        if (parent != null) parent.mkdirs();

        File dbBackup = new File(stage, "previous.db");
        File oldCovers = new File(stage, "covers_old");
        boolean hadDb = dbFile.exists();
        boolean movedCovers = false;

        // Ya validado todo: recién ahora se cierra la base real.
        AppDatabase.resetInstance();

        try {
            if (hadDb) copyFile(dbFile, dbBackup);

            if (coversDir.exists()) {
                if (!coversDir.renameTo(oldCovers)) {
                    throw new IOException("No se pudo apartar la carpeta de portadas actual.");
                }
                movedCovers = true;
            }

            // Escritura atómica de la base: temporal en la misma carpeta + renombrar.
            File tmpDb = new File(parent, DB_FILE + ".restore");
            copyFile(st.db, tmpDb);
            new File(dbFile.getPath() + "-wal").delete();
            new File(dbFile.getPath() + "-shm").delete();
            if (dbFile.exists() && !dbFile.delete()) {
                tmpDb.delete();
                throw new IOException("No se pudo reemplazar la base de datos.");
            }
            if (!tmpDb.renameTo(dbFile)) {
                tmpDb.delete();
                throw new IOException("No se pudo escribir la base de datos restaurada.");
            }

            if (st.covers.isDirectory()) {
                if (!st.covers.renameTo(coversDir)) {
                    throw new IOException("No se pudo instalar la carpeta de portadas.");
                }
            } else {
                coversDir.mkdirs();
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Fallo al intercambiar archivos; revirtiendo", e);
            try {
                if (hadDb && dbBackup.exists()) {
                    new File(dbFile.getPath() + "-wal").delete();
                    new File(dbFile.getPath() + "-shm").delete();
                    copyFile(dbBackup, dbFile);
                }
                if (movedCovers) {
                    deleteDir(coversDir);
                    oldCovers.renameTo(coversDir);
                }
            } catch (Exception rollbackError) {
                Log.e(TAG, "La reversión también falló", rollbackError);
            }
            throw (e instanceof IOException) ? (IOException) e
                    : new IOException("Error al restaurar: " + e.getMessage());
        }
    }

    private void applyPrefs(Properties props) {
        if (props == null) return;
        SharedPreferences.Editor ed = ctx
                .getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        String uri = props.getProperty(MainActivity.KEY_FOLDER_URI);
        String name = props.getProperty(MainActivity.KEY_FOLDER_NAME);
        // Solo se acepta un URI de documentos de tamaño razonable.
        if (uri != null && uri.startsWith("content://") && uri.length() < 2048) {
            ed.putString(MainActivity.KEY_FOLDER_URI, uri);
        }
        if (name != null && name.length() < 256) {
            ed.putString(MainActivity.KEY_FOLDER_NAME, name);
        }
        ed.apply();
    }

    // ── Info ──────────────────────────────────────────────────────────────────

    public String getLastBackupTime() {
        return ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TS, null);
    }

    // ── Helpers de exportación ────────────────────────────────────────────────

    /** Destino en Descargas; en Android 10+ el archivo queda "pendiente" hasta terminar. */
    private class DownloadTarget {
        OutputStream out;
        Uri uri;     // Android 10+
        File file;   // Android 9-

        void commit() {
            if (uri != null) {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.IS_PENDING, 0);
                    ctx.getContentResolver().update(uri, cv, null, null);
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo publicar el respaldo", e);
                }
            }
        }

        void discard() {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try {
                if (uri != null) ctx.getContentResolver().delete(uri, null, null);
                else if (file != null) file.delete();
            } catch (Exception ignored) {}
        }
    }

    private DownloadTarget openDownloadTarget(String fileName) throws IOException {
        DownloadTarget t = new DownloadTarget();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_FOLDER);
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                throw new IOException("No se pudo crear el archivo en Descargas. "
                        + "Verifica que el almacenamiento esté disponible.");
            }
            t.uri = uri;
            t.out = ctx.getContentResolver().openOutputStream(uri);
            if (t.out == null) {
                t.discard();
                throw new IOException("No se pudo abrir el archivo para escritura.");
            }
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("No se pudo crear la carpeta Descargas/" + BACKUP_FOLDER);
            }
            t.file = new File(dir, fileName);
            t.out = new FileOutputStream(t.file);
        }
        return t;
    }

    /** Vuelca el WAL al archivo principal; lanza IOException si no se pudo garantizar. */
    private void checkpointWal() throws IOException {
        try {
            SupportSQLiteDatabase sdb = AppDatabase.getInstance(ctx)
                    .getOpenHelper().getWritableDatabase();
            // PRAGMA devuelve filas: se consulta con query() (execSQL puede rechazarlo).
            try (Cursor c = sdb.query("PRAGMA wal_checkpoint(TRUNCATE)")) {
                if (c.moveToFirst() && c.getInt(0) != 0) {
                    throw new IOException("La base de datos está ocupada. Intenta de nuevo.");
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("No se pudo preparar la base de datos para el respaldo: "
                    + e.getMessage());
        }
    }

    private byte[] buildPrefsBytes() throws IOException {
        SharedPreferences prefs = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        Properties props = new Properties();
        String folderUri  = prefs.getString(MainActivity.KEY_FOLDER_URI,  "");
        String folderName = prefs.getString(MainActivity.KEY_FOLDER_NAME, "");
        if (folderUri  != null) props.setProperty(MainActivity.KEY_FOLDER_URI,  folderUri);
        if (folderName != null) props.setProperty(MainActivity.KEY_FOLDER_NAME, folderName);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        props.store(bos, "Amp39 backup");
        return bos.toByteArray();
    }

    // ── Helpers de archivos ───────────────────────────────────────────────────

    private static void addFileToZip(ZipOutputStream zos, File file, String entryName)
            throws IOException {
        if (file == null || !file.exists()) return;
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }

    /** Copia con tope por archivo y tope total (protege contra ZIP bombs). */
    private static void copyLimited(InputStream in, File dest, long maxBytes, long[] total)
            throws IOException {
        File parent = dest.getParentFile();
        if (parent != null) parent.mkdirs();
        long written = 0;
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                written += n;
                total[0] += n;
                if (written > maxBytes || total[0] > MAX_TOTAL_BYTES) {
                    throw new IOException("El respaldo excede el tamaño permitido.");
                }
                fos.write(buf, 0, n);
            }
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.getFD().sync();
        }
    }

    private static boolean isInside(File dir, File f) {
        try {
            return f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
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
}
