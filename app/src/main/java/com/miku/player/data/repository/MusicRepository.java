package com.miku.player.data.repository;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.lifecycle.LiveData;
import com.miku.player.data.model.*;
import com.miku.player.utils.MediaStoreAccess;
import com.miku.player.utils.TagWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicRepository {

    private static final String TAG = "MikuRepo";
    private static final String REPO_PREFS = "repo_prefs";
    private static final String KEY_EXCLUDED = "excluded_song_ids";
    private static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;

    private final ContentResolver resolver;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService fastExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile MusicRepository INSTANCE;
    private final File coversDir;
    // IDs de canciones que el usuario quitó de la app — el escaneo no las reinserta.
    // Se persisten para que no reaparezcan al reiniciar la app.
    private final Set<Long> excludedSongIds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MusicRepository(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.resolver = appContext.getContentResolver();
        // Carpeta interna: incluida en el respaldo y borrada solo al desinstalar.
        this.coversDir = new File(appContext.getFilesDir(), "covers");
        if (!coversDir.exists()) coversDir.mkdirs();
        loadExcluded();
    }

    public static MusicRepository getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (MusicRepository.class) {
                if (INSTANCE == null) INSTANCE = new MusicRepository(ctx);
            }
        }
        return INSTANCE;
    }

    /**
     * Siempre se pide la instancia vigente: si la base se reinicia (restauración de
     * respaldo) no se queda una referencia a una base ya cerrada.
     */
    private AppDatabase db() { return AppDatabase.getInstance(appContext); }

    // ── Exclusiones persistentes ───────────────────────────────────────────────

    private void loadExcluded() {
        Set<String> saved = appContext.getSharedPreferences(REPO_PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_EXCLUDED, null);
        if (saved == null) return;
        for (String s : saved) {
            try { excludedSongIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
        }
    }

    private synchronized void saveExcluded() {
        Set<String> out = new HashSet<>();
        for (Long id : excludedSongIds) out.add(String.valueOf(id));
        appContext.getSharedPreferences(REPO_PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_EXCLUDED, out).apply();
    }

    /** Se llama al elegir una carpeta nueva: las canciones quitadas vuelven a poder aparecer. */
    public void clearExcluded() {
        excludedSongIds.clear();
        saveExcluded();
    }

    // ── Songs ──────────────────────────────────────────────────────────────────

    public LiveData<List<Song>> getAllSongs() { return db().songDao().getAllSongs(); }

    public LiveData<List<Song>> search(String query) {
        // Se escapan % _ \ para que se busquen como texto literal (el DAO usa ESCAPE '\').
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return db().songDao().search("%" + escaped + "%");
    }

    private LiveData<List<Song>> favoritesLiveData;
    public LiveData<List<Song>> getFavoriteSongs() {
        if (favoritesLiveData == null) favoritesLiveData = db().songDao().getFavoriteSongs();
        return favoritesLiveData;
    }

    public void setFavorite(Song song, boolean favorite) {
        final long songId = song.getId();
        fastExecutor.execute(() -> {
            long order = favorite ? System.currentTimeMillis() : 0L;
            db().songDao().setFavoriteWithOrder(songId, favorite, order);
        });
    }

    public void toggleFavorite(Song song) {
        setFavorite(song, !song.isFavorite());
    }

    /** Lee una canción por id en segundo plano y entrega el resultado en el hilo principal. */
    public void getSongByIdAsync(long id, Callback<Song> callback) {
        fastExecutor.execute(() -> {
            Song s = db().songDao().getSongById(id);
            mainHandler.post(() -> callback.onResult(s));
        });
    }

    /**
     * Lee varias canciones por id (en el mismo orden) en segundo plano. Entrega null si
     * alguna no existe en la base. El callback llega en el hilo principal.
     */
    public void getSongsByIdsAsync(List<Long> ids, Callback<List<Song>> callback) {
        fastExecutor.execute(() -> {
            List<Song> out = new ArrayList<>();
            try {
                for (Long id : ids) {
                    Song s = db().songDao().getSongById(id);
                    if (s == null) { out = null; break; }
                    out.add(s);
                }
            } catch (Exception e) {
                out = null;
            }
            final List<Song> result = out;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Resultado de un escaneo de carpeta SAF. */
    private static class SafScan {
        final List<Song> songs = new ArrayList<>();
        // id -> {nombre de archivo, documentId} para enriquecer con MediaStore
        final Map<Long, String[]> meta = new HashMap<>();
        boolean complete = true;
    }

    public void scanFolder(String folderUriStr, Callback<List<Song>> callback) {
        executor.execute(() -> {
            final boolean isSaf = folderUriStr != null;
            List<Song> songs;
            Map<Long, String[]> meta = null;
            boolean complete;

            if (isSaf) {
                SafScan scan = scanSAFFolder(folderUriStr);
                songs = scan.songs;
                meta = scan.meta;
                complete = scan.complete;
            } else {
                boolean[] ok = {true};
                songs = scanMediaStore(ok);
                complete = ok[0];
            }

            // ── Fase 1 (sin transacción): lecturas lentas — metadatos y portadas ──
            List<Song> toInsert = new ArrayList<>();
            List<Song> toUpdate = new ArrayList<>();
            for (Song newSong : songs) {
                if (excludedSongIds.contains(newSong.getId())) continue;

                Song existing = db().songDao().getSongById(newSong.getId());
                String[] m = meta != null ? meta.get(newSong.getId()) : null;

                if (existing != null) {
                    newSong.setFavorite(existing.isFavorite());
                    newSong.setFavoriteOrder(existing.getFavoriteOrder());
                    newSong.setCustomTitle(existing.getCustomTitle());
                    // Preservar la fecha de agregado ORIGINAL; dateModified sí se actualiza.
                    newSong.setDateAdded(existing.getDateAdded());

                    if (isSaf) {
                        boolean unchanged = existing.getDuration() > 0
                                && existing.getDateModified() == newSong.getDateModified();
                        if (unchanged) {
                            // El archivo no cambió: reutilizar metadatos ya obtenidos.
                            newSong.setTitle(existing.getTitle());
                            newSong.setArtist(existing.getArtist());
                            newSong.setAlbum(existing.getAlbum());
                            newSong.setDuration(existing.getDuration());
                        } else if (m != null) {
                            enrichFromMediaStore(newSong, m[0], m[1]);
                        }
                    }

                    String existingCover = existing.getCustomCoverPath();
                    if (existingCover != null && !existingCover.isEmpty()
                            && new File(existingCover).length() > 0) {
                        newSong.setCustomCoverPath(existingCover);
                    } else {
                        newSong.setCustomCoverPath(cacheEmbeddedCover(newSong));
                    }
                    toUpdate.add(newSong);
                } else {
                    if (isSaf && m != null) enrichFromMediaStore(newSong, m[0], m[1]);
                    String cachedCover = cacheEmbeddedCover(newSong);
                    if (cachedCover != null) newSong.setCustomCoverPath(cachedCover);
                    toInsert.add(newSong);
                }
            }

            // ── Fase 2: todas las escrituras en UNA transacción (un solo refresco de UI) ──
            final boolean doCleanup = complete && !songs.isEmpty();
            final List<Long> removedIds = new ArrayList<>();
            try {
                db().runInTransaction(() -> {
                    SongDao dao = db().songDao();
                    for (Song s : toInsert) dao.insert(s);
                    for (Song s : toUpdate) dao.update(s);

                    // Limpieza SOLO si el escaneo terminó completo: un listado parcial
                    // (error de SAF, tarjeta SD desmontada) no debe borrar la biblioteca.
                    if (doCleanup) {
                        Set<Long> scanned = new HashSet<>();
                        for (Song s : songs) scanned.add(s.getId());
                        List<Long> toDelete = new ArrayList<>();
                        for (Long id : dao.getRemovableSongIds()) {
                            if (!scanned.contains(id)) toDelete.add(id);
                        }
                        // Por lotes: evita el límite de variables de SQLite en listas grandes.
                        for (int i = 0; i < toDelete.size(); i += 500) {
                            dao.deleteByIds(toDelete.subList(i, Math.min(i + 500, toDelete.size())));
                        }
                        removedIds.addAll(toDelete);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error guardando el escaneo", e);
            }

            for (Long id : removedIds) {
                File cover = new File(coversDir, "cover_" + id + ".jpg");
                if (cover.exists()) cover.delete();
            }

            if (callback != null) callback.onResult(songs);
        });
    }

    /**
     * Extrae y cachea la carátula embebida de una canción en filesDir/covers/cover_<id>.jpg.
     * Devuelve la ruta, o null si no hay carátula. Decodifica con submuestreo y captura
     * OutOfMemoryError para que una imagen enorme no tire la app durante el escaneo.
     */
    private String cacheEmbeddedCover(Song song) {
        try {
            File outFile = new File(coversDir, "cover_" + song.getId() + ".jpg");
            if (outFile.exists()) {
                if (outFile.length() > 0) return outFile.getAbsolutePath();
                outFile.delete(); // archivo vacío/corrupto de un intento anterior
            }

            byte[] artBytes = null;

            android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
            try {
                Uri fileUri = Uri.parse(song.getPath());
                String scheme = fileUri.getScheme();
                if (scheme != null && (scheme.equals("content") || scheme.equals("file"))) {
                    android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(fileUri, "r");
                    if (pfd != null) {
                        try {
                            mmr.setDataSource(pfd.getFileDescriptor());
                        } finally {
                            pfd.close();
                        }
                    }
                } else {
                    mmr.setDataSource(song.getPath());
                }
                artBytes = mmr.getEmbeddedPicture();
            } catch (Exception ignored) {
            } finally {
                try { mmr.release(); } catch (Exception ignored2) {}
            }

            if (artBytes == null) {
                try {
                    long albumId = resolveAlbumId(song);
                    if (albumId != -1) {
                        Uri msArt = Uri.parse("content://media/external/audio/albumart/" + albumId);
                        try (InputStream is = resolver.openInputStream(msArt)) {
                            if (is != null) {
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = is.read(buf)) != -1) {
                                    bos.write(buf, 0, n);
                                    if (bos.size() > MAX_IMAGE_BYTES) { bos = null; break; }
                                }
                                if (bos != null) artBytes = bos.toByteArray();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (artBytes == null || artBytes.length == 0) return null;

            // 1) Leer solo dimensiones; 2) decodificar con submuestreo (máx. ~2048 px).
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, opts);
            if (bmp == null) return null;

            // Límite de 1024 px en el lado largo, conservando la proporción original.
            int maxDim = Math.max(bmp.getWidth(), bmp.getHeight());
            if (maxDim > 1024) {
                float scale = 1024f / maxDim;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp,
                        Math.round(bmp.getWidth() * scale),
                        Math.round(bmp.getHeight() * scale), true);
                bmp.recycle();
                bmp = scaled;
            }

            // Se escribe a un temporal y se renombra: nunca queda un .jpg a medias.
            File tmp = new File(coversDir, outFile.getName() + ".tmp");
            boolean ok;
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                ok = bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            }
            bmp.recycle();
            if (!ok || tmp.length() == 0 || !tmp.renameTo(outFile)) {
                tmp.delete();
                return null;
            }
            return outFile.getAbsolutePath();

        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "cacheEmbeddedCover failed for " + song.getTitle(), e);
            return null;
        }
    }

    public void scanFolder(String folderUriStr) { scanFolder(folderUriStr, null); }
    public void scanMedia(Callback<List<Song>> callback) { scanFolder(null, callback); }

    private SafScan scanSAFFolder(String folderUriStr) {
        SafScan scan = new SafScan();
        try {
            Uri folderUri = Uri.parse(folderUriStr);
            scanSAFDirectory(folderUri, DocumentsContract.getTreeDocumentId(folderUri), scan);
        } catch (Exception e) {
            Log.w(TAG, "scanSAFFolder error", e);
            scan.complete = false;
        }
        return scan;
    }

    /** ID estable (mismo cálculo de siempre, para no invalidar datos ya guardados). */
    private static long idForDoc(String docId) {
        return Math.abs((long) docId.hashCode() ^ ((long) docId.length() << 32));
    }

    /** ID alternativo de 64 bits (FNV-1a) para resolver colisiones del ID legado. */
    private static long altIdForDoc(String docId) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < docId.length(); i++) {
            h ^= docId.charAt(i);
            h *= 0x100000001b3L;
        }
        return h & Long.MAX_VALUE;
    }

    /** Escanea recursivamente un directorio SAF incluyendo todos sus subdirectorios. */
    private void scanSAFDirectory(Uri treeUri, String parentDocId, SafScan scan) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) { scan.complete = false; return; }
            while (cursor.moveToNext()) {
                String docId      = cursor.getString(0);
                String name       = cursor.getString(1);
                String mimeType   = cursor.getString(2);
                long lastModified = cursor.isNull(3) ? 0L : cursor.getLong(3);
                if (mimeType == null || docId == null || name == null) continue;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    scanSAFDirectory(treeUri, docId, scan);
                } else if (mimeType.startsWith("audio/")) {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;

                    long stableId = idForDoc(docId);
                    String[] owner = scan.meta.get(stableId);
                    if (owner != null && !owner[1].equals(docId)) {
                        // Colisión del ID de 32 bits: se usa un ID alternativo determinista.
                        stableId = altIdForDoc(docId);
                        String[] owner2 = scan.meta.get(stableId);
                        if (owner2 != null && !owner2[1].equals(docId)) {
                            Log.w(TAG, "Colisión de ID irresoluble, se omite: " + name);
                            continue;
                        }
                    }

                    long dateAdded = lastModified > 0 ? lastModified : System.currentTimeMillis();
                    Song s = new Song(stableId, title, "Artista desconocido",
                            "Álbum desconocido", 0L, fileUri.toString(), dateAdded);
                    s.setDateModified(dateAdded);
                    scan.songs.add(s);
                    scan.meta.put(stableId, new String[]{name, docId});
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "scanSAFDirectory error en " + parentDocId, e);
            scan.complete = false;
        }
    }

    /**
     * Busca la canción en MediaStore por nombre de archivo EXACTO (y carpeta relativa
     * cuando es posible). Antes usaba LIKE '%título%' y tomaba la primera fila, lo que
     * podía asignar los metadatos de OTRA canción.
     */
    private void enrichFromMediaStore(Song s, String displayName, String docId) {
        if (displayName == null) return;
        try {
            String[] proj = {
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
            };
            StringBuilder sel = new StringBuilder(MediaStore.Audio.Media.DISPLAY_NAME + " = ?");
            List<String> args = new ArrayList<>();
            args.add(displayName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && docId != null) {
                int colon = docId.indexOf(':');
                int slash = docId.lastIndexOf('/');
                if (colon >= 0 && slash > colon) {
                    sel.append(" AND ").append(MediaStore.MediaColumns.RELATIVE_PATH).append(" = ?");
                    args.add(docId.substring(colon + 1, slash + 1));
                }
            }
            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            try (Cursor c = resolver.query(collection, proj, sel.toString(),
                    args.toArray(new String[0]), null)) {
                if (c != null && c.moveToFirst()) {
                    String t = c.getString(0), ar = c.getString(1), al = c.getString(2);
                    if (t != null && !t.isEmpty()) s.setTitle(t);
                    if (ar != null && !"<unknown>".equals(ar)) s.setArtist(ar);
                    if (al != null && !al.isEmpty()) s.setAlbum(al);
                    s.setDuration(c.getLong(3));
                }
            }
        } catch (Exception ignored) {}
    }

    private List<Song> scanMediaStore(boolean[] ok) {
        List<Song> songs = new ArrayList<>();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
                + " AND " + MediaStore.Audio.Media.DURATION + " >= 30000";

        try (Cursor cursor = resolver.query(collection, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor == null) { ok[0] = false; return songs; }
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                String artist = cursor.getString(artistCol);
                if (artist == null || "<unknown>".equals(artist)) artist = "Artista desconocido";
                String path = cursor.getString(dataCol);
                if (path == null) continue;
                Song s = new Song(cursor.getLong(idCol), cursor.getString(titleCol),
                        artist, cursor.getString(albumCol),
                        cursor.getLong(durCol), path, cursor.getLong(dateCol));
                s.setDateModified(cursor.getLong(dateModCol));
                songs.add(s);
            }
        } catch (Exception e) {
            Log.w(TAG, "scanMediaStore error", e);
            ok[0] = false;
        }
        return songs;
    }

    // ── Rutas seguras ──────────────────────────────────────────────────────────

    /** true si {@code f} está dentro de {@code dir} (comparando rutas canónicas). */
    private static boolean isInside(File dir, File f) {
        try {
            String d = dir.getCanonicalPath() + File.separator;
            return f.getCanonicalPath().startsWith(d);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Un archivo de audio solo se puede borrar si está en almacenamiento del usuario
     * y NUNCA dentro de los datos privados de la app (la base, preferencias, etc.).
     */
    private boolean isSafeUserFile(File f) {
        try {
            String c = f.getCanonicalPath();
            if (isInside(appContext.getDataDir(), f)) return false;
            if (appContext.getDataDir() != null && c.startsWith(appContext.getDataDir().getCanonicalPath())) return false;
            String ext = Environment.getExternalStorageDirectory().getCanonicalPath();
            return c.startsWith(ext + File.separator) || c.startsWith("/storage/");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Portadas ───────────────────────────────────────────────────────────────

    public String getExpectedCoverPath(long songId) {
        return new File(coversDir, "cover_" + songId + ".jpg").getAbsolutePath();
    }

    public interface CoverUpdateCallback {
        void onCoverReady(long songId, String localPath);
    }

    public void updateSongCover(Song song, String coverUriStr) {
        updateSongCover(song, coverUriStr, true, null);
    }

    public void updateSongCover(Song song, String coverUriStr, CoverUpdateCallback callback) {
        updateSongCover(song, coverUriStr, true, callback);
    }

    /**
     * Copia la imagen a filesDir/covers/ (validando que sea una imagen) y guarda la
     * ruta local en la DB. Si la copia falla NO se toca la portada actual.
     *
     * @param writeToFile si es true, además escribe la carátula en el archivo de audio.
     */
    public void updateSongCover(Song song, String coverUriStr, boolean writeToFile,
                                CoverUpdateCallback callback) {
        final long songId = song.getId();
        executor.execute(() -> {
            String localPath = copyImageToFile(coverUriStr,
                    new File(coversDir, "cover_" + songId + ".jpg"));
            if (localPath == null) {
                toast("No se pudo guardar la imagen seleccionada");
                return;
            }

            Song dbSong = db().songDao().getSongById(songId);
            if (dbSong != null) {
                dbSong.setCustomCoverPath(localPath);
                db().songDao().update(dbSong);
            } else {
                song.setCustomCoverPath(localPath);
                db().songDao().update(song);
            }

            if (writeToFile && !MediaStoreAccess.hasWriteGrant(appContext, song)) {
                toast("Sin permiso del sistema: la carátula se guardó solo en la app");
            } else if (writeToFile) {
                TagWriter.writeCover(appContext, song.getPath(), MediaStoreAccess.ioTarget(song),
                        localPath, new TagWriter.Result() {
                    public void onSuccess() { Log.d(TAG, "Cover written to audio file tags"); }
                    public void onError(String msg) {
                        Log.w(TAG, "Could not write cover to tags: " + msg);
                        toast("La carátula se guardó en la app, pero no se pudo escribir en el archivo original");
                    }
                });
            }

            if (callback != null) {
                mainHandler.post(() -> callback.onCoverReady(songId, localPath));
            }
        });
    }

    /**
     * Copia una imagen (URI o content://) a {@code out} de forma atómica: escribe a un
     * temporal, valida que se pueda decodificar como imagen y recién entonces reemplaza
     * el destino. Devuelve la ruta final o null si algo falla (sin tocar el destino).
     */
    private String copyImageToFile(String uriStr, File out) {
        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
        try {
            Uri uri = Uri.parse(uriStr);
            long total = 0;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return null;
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_IMAGE_BYTES) throw new IOException("Imagen demasiado grande");
                        fos.write(buf, 0, n);
                    }
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getAbsolutePath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                tmp.delete();
                return null; // no es una imagen válida
            }
            if (out.exists() && !out.delete()) { tmp.delete(); return null; }
            if (!tmp.renameTo(out)) { tmp.delete(); return null; }
            return out.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy image", e);
            tmp.delete();
            return null;
        }
    }

    private void toast(String msg) {
        mainHandler.post(() -> Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show());
    }

    // ── Custom title ─────────────────────────────────────────────────────────────

    /**
     * Guarda un título personalizado (DB inmediata) y lo escribe en las etiquetas del
     * archivo en segundo plano. Los callbacks llegan en el hilo principal. El parámetro
     * {@code activity} se conserva por compatibilidad pero ya no se captura (evita fugas).
     */
    public void updateCustomTitle(Song song, String newTitle, Activity activity,
                                  TagWriter.Result callback) {
        updateCustomTitle(song, newTitle, activity, true, callback);
    }

    /**
     * @param writeToFile si es false solo se guarda en la app (el archivo no se toca) y el
     *                    callback recibe onSuccess de inmediato.
     */
    public void updateCustomTitle(Song song, String newTitle, Activity activity,
                                  boolean writeToFile, TagWriter.Result callback) {
        final long songId = song.getId();
        final String path = song.getPath();
        final String fallbackTitle = song.getTitle();
        fastExecutor.execute(() -> db().songDao().setCustomTitle(songId, newTitle));

        if (!writeToFile) {
            mainHandler.post(callback::onSuccess);
            return;
        }
        // Android 11+: sin el permiso del sistema no se puede tocar un archivo de otra app.
        if (!MediaStoreAccess.hasWriteGrant(appContext, song)) {
            mainHandler.post(() -> callback.onError(
                    "sin permiso del sistema para modificar el archivo original"));
            return;
        }
        final String ioTarget = MediaStoreAccess.ioTarget(song);

        executor.execute(() -> TagWriter.writeTitle(appContext, path, ioTarget,
                newTitle != null ? newTitle : fallbackTitle,
                new TagWriter.Result() {
                    public void onSuccess() { mainHandler.post(callback::onSuccess); }
                    public void onError(String msg) { mainHandler.post(() -> callback.onError(msg)); }
                }));
    }

    // ── Playlists ──────────────────────────────────────────────────────────────

    public LiveData<List<PlaylistWithSongs>> getAllPlaylistsWithSongs() {
        return db().playlistDao().getAllPlaylistsWithSongs();
    }

    public LiveData<PlaylistWithSongs> getPlaylistWithSongs(long id) {
        return db().playlistDao().getPlaylistWithSongs(id);
    }

    public void createPlaylist(String name, Callback<Long> callback) {
        executor.execute(() -> {
            long id = db().playlistDao().insertPlaylist(new Playlist(name));
            if (callback != null) callback.onResult(id);
        });
    }

    public void renamePlaylist(Playlist playlist, String newName) {
        executor.execute(() -> { playlist.setName(newName); db().playlistDao().updatePlaylist(playlist); });
    }

    public void deletePlaylist(Playlist playlist) {
        executor.execute(() -> {
            db().playlistDao().deletePlaylist(playlist);
            deleteOwnCoverFile(playlist.getCoverPath());
        });
    }

    /** Borra un archivo de portada SOLO si está dentro de covers/ (nunca una ruta arbitraria). */
    private void deleteOwnCoverFile(String path) {
        if (path == null || !path.startsWith("/")) return;
        try {
            File f = new File(path);
            if (f.exists() && isInside(coversDir, f)) f.delete();
        } catch (Exception ignored) {}
    }

    /**
     * Elimina una canción de la base (y de todas las playlists, renumerando posiciones)
     * y, si deleteFile es true, también borra el archivo de audio.
     */
    public void deleteSong(Song song, boolean deleteFile, Callback<Boolean> callback) {
        excludedSongIds.add(song.getId());
        saveExcluded();
        executor.execute(() -> {
            final long songId = song.getId();
            db().runInTransaction(() -> {
                PlaylistDao pdao = db().playlistDao();
                List<Long> affected = pdao.getPlaylistIdsForSong(songId);
                pdao.removeSongFromAllPlaylists(songId);
                db().songDao().delete(song);
                for (Long pid : affected) renumberPlaylist(pid);
            });

            boolean fileOk = true;
            if (deleteFile) fileOk = deleteAudioFile(song);

            deleteOwnCoverFile(song.getCustomCoverPath());

            final boolean result = fileOk;
            if (callback != null) mainHandler.post(() -> callback.onResult(result));
        });
    }

    private boolean deleteAudioFile(Song song) {
        String path = song.getPath();
        if (path == null) return false;
        try {
            if (path.startsWith("content://")) {
                Uri uri = Uri.parse(path);
                try {
                    if (DocumentsContract.isDocumentUri(appContext, uri)) {
                        return DocumentsContract.deleteDocument(resolver, uri);
                    } else if (path.contains("/tree/")) {
                        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                                DocumentsContract.getTreeDocumentId(uri));
                        return DocumentsContract.deleteDocument(resolver, docUri);
                    }
                } catch (Exception ignored) {}
                return resolver.delete(uri, null, null) > 0;
            } else {
                File file = new File(path);
                if (!isSafeUserFile(file)) {
                    Log.w(TAG, "Ruta fuera del almacenamiento del usuario, no se borra: " + path);
                    return false;
                }
                if (!file.exists()) return false;
                boolean deleted = file.delete();
                if (deleted) {
                    try {
                        resolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                MediaStore.Audio.Media.DATA + "=?", new String[]{path});
                    } catch (Exception ignored) {}
                }
                return deleted;
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo eliminar el archivo: " + path, e);
            return false;
        }
    }

    public LiveData<List<Song>> getSongsInPlaylist(long playlistId) {
        return db().playlistDao().getSongsInPlaylist(playlistId);
    }

    public void addSongToPlaylist(long playlistId, Song song) {
        executor.execute(() -> {
            try {
                db().runInTransaction(() -> {
                    Song existing = db().songDao().getSongById(song.getId());
                    if (existing == null) db().songDao().insert(song);
                    PlaylistDao pdao = db().playlistDao();
                    if (pdao.getSongCountInPlaylistBySong(playlistId, song.getId()) > 0) return;
                    // MAX+1 (no COUNT): evita posiciones duplicadas tras quitar canciones.
                    int position = pdao.getMaxPosition(playlistId) + 1;
                    pdao.addSongToPlaylist(new PlaylistSong(playlistId, song.getId(), position));
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to add song to playlist", e);
            }
        });
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        executor.execute(() -> db().runInTransaction(() -> {
            db().playlistDao().removeSongFromPlaylistById(playlistId, songId);
            renumberPlaylist(playlistId);
        }));
    }

    /** Deja las posiciones de una playlist consecutivas (0..n-1) sin huecos ni duplicados. */
    private void renumberPlaylist(long playlistId) {
        PlaylistDao pdao = db().playlistDao();
        List<PlaylistSong> entries = pdao.getPlaylistSongEntries(playlistId);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).position != i) {
                pdao.updateSongPosition(playlistId, entries.get(i).songId, i);
            }
        }
    }

    /** Reordena las canciones de una playlist: todas las posiciones en UNA transacción. */
    public void reorderPlaylistSongs(long playlistId, List<Long> orderedSongIds) {
        executor.execute(() -> db().runInTransaction(() -> {
            for (int i = 0; i < orderedSongIds.size(); i++) {
                db().playlistDao().updateSongPosition(playlistId, orderedSongIds.get(i), i);
            }
        }));
    }

    /** Persiste el orden manual de las playlists (valores descendentes: sortOrder DESC). */
    public void reorderPlaylists(List<Long> orderedPlaylistIds) {
        executor.execute(() -> db().runInTransaction(() -> {
            int n = orderedPlaylistIds.size();
            for (int i = 0; i < n; i++) {
                db().playlistDao().updatePlaylistSortOrder(orderedPlaylistIds.get(i), n - i);
            }
        }));
    }

    public void updatePlaylistCover(Playlist playlist, String coverPath) {
        executor.execute(() -> {
            String localPath = copyImageToFile(coverPath,
                    new File(coversDir, "playlist_cover_" + playlist.getId() + ".jpg"));
            if (localPath == null) {
                toast("No se pudo guardar la imagen seleccionada");
                return;
            }
            playlist.setCoverPath(localPath);
            db().playlistDao().updatePlaylist(playlist);
        });
    }

    public void removePlaylistCover(Playlist playlist) {
        executor.execute(() -> {
            deleteOwnCoverFile(playlist.getCoverPath());
            playlist.setCoverPath(null);
            db().playlistDao().updatePlaylist(playlist);
        });
    }

    /** ALBUM_ID de MediaStore para rutas de archivo reales (no aplica a documentos SAF). */
    private long resolveAlbumId(Song song) {
        String path = song.getPath();
        if (path == null || path.startsWith("content://")) return -1;
        try (Cursor c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.ALBUM_ID},
                MediaStore.Audio.Media.DATA + " = ?", new String[]{path}, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Exception ignored) {}
        return -1;
    }

    public LiveData<Song> getSongByIdLive(long id) {
        return db().songDao().getSongByIdLive(id);
    }

    public void shutdown() {
        executor.shutdown();
        fastExecutor.shutdown();
    }

    public interface Callback<T> { void onResult(T result); }
}
