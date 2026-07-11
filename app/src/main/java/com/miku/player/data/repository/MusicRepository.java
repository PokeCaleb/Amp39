package com.miku.player.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.os.Build;
import androidx.lifecycle.LiveData;
import com.miku.player.data.model.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicRepository {

    private final AppDatabase db;
    private final ContentResolver resolver;
    private final android.content.Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService fastExecutor = Executors.newSingleThreadExecutor();
    private static volatile MusicRepository INSTANCE;
    private final File coversDir;
    // IDs de canciones eliminadas por el usuario — el scan no las reinsertará
    private final Set<Long> excludedSongIds =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MusicRepository(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.db = AppDatabase.getInstance(ctx);
        this.resolver = ctx.getContentResolver();
        // Covers dir: internal files dir so Android Auto Backup includes it.
        // getExternalFilesDir is wiped on uninstall; getFilesDir is backed up via backup_rules.xml.
        this.coversDir = new File(ctx.getFilesDir(), "covers");
        if (!coversDir.exists()) coversDir.mkdirs();
    }

    public static MusicRepository getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (MusicRepository.class) {
                if (INSTANCE == null) INSTANCE = new MusicRepository(ctx);
            }
        }
        return INSTANCE;
    }

    // ── Songs ──────────────────────────────────────────────────────────────────

    public LiveData<List<Song>> getAllSongs() { return db.songDao().getAllSongs(); }

    public LiveData<List<Song>> search(String query) {
        return db.songDao().search("%" + query + "%");
    }

    private androidx.lifecycle.LiveData<java.util.List<Song>> favoritesLiveData;
    public androidx.lifecycle.LiveData<java.util.List<Song>> getFavoriteSongs() {
        if (favoritesLiveData == null) favoritesLiveData = db.songDao().getFavoriteSongs();
        return favoritesLiveData;
    }

    /**
     * Explicitly sets a song's favorite state to the given value.
     * Guarda el timestamp actual como favoriteOrder para preservar el orden de selección.
     */
    public void setFavorite(Song song, boolean favorite) {
        final long songId = song.getId();
        fastExecutor.execute(() -> {
            long order = favorite ? System.currentTimeMillis() : 0L;
            db.songDao().setFavoriteWithOrder(songId, favorite, order);
        });
    }

    public void toggleFavorite(Song song) {
        final boolean newFav = !song.isFavorite();
        final long songId = song.getId();
        fastExecutor.execute(() -> {
            long order = newFav ? System.currentTimeMillis() : 0L;
            db.songDao().setFavoriteWithOrder(songId, newFav, order);
        });
    }

    public void scanFolder(String folderUriStr, Callback<List<Song>> callback) {
        executor.execute(() -> {
            List<Song> songs = folderUriStr != null
                    ? scanSAFFolder(folderUriStr)
                    : scanMediaStore();

            for (Song newSong : songs) {
                // No reinsertar canciones que el usuario eliminó explícitamente
                if (excludedSongIds.contains(newSong.getId())) continue;

                Song existing = db.songDao().getSongById(newSong.getId());
                if (existing != null) {
                    newSong.setFavorite(existing.isFavorite());
                    newSong.setFavoriteOrder(existing.getFavoriteOrder());
                    newSong.setCustomTitle(existing.getCustomTitle());
                    String existingCover = existing.getCustomCoverPath();
                    if (existingCover != null && !existingCover.isEmpty()
                            && new File(existingCover).exists()) {
                        newSong.setCustomCoverPath(existingCover);
                    } else {
                        String cachedCover = cacheEmbeddedCover(newSong);
                        newSong.setCustomCoverPath(cachedCover);
                    }
                    db.songDao().update(newSong);
                } else {
                    String cachedCover = cacheEmbeddedCover(newSong);
                    if (cachedCover != null) newSong.setCustomCoverPath(cachedCover);
                    db.songDao().insert(newSong);
                }
            }

            if (!songs.isEmpty()) {
                List<Long> scannedIds = new ArrayList<>();
                for (Song s : songs) scannedIds.add(s.getId());
                db.songDao().deleteOrphanSongsNotIn(scannedIds);
            }

            if (callback != null) callback.onResult(songs);
        });
    }

    /**
     * Extracts and caches the embedded album art of a song.
     *
     * Strategy (in order):
     * 1. MediaMetadataRetriever on the audio file URI — works for ANY file,
     *    whether scanned via MediaStore or SAF folder picker. This reads the
     *    ID3/FLAC/Vorbis artwork tag directly from the file bytes.
     * 2. MediaStore content URI (content://media/external/audio/albumart/<id>)
     *    as fallback for MediaStore-indexed files when embedded tag reading fails.
     *
     * Result is saved to filesDir/covers/cover_<id>.jpg and that path is returned.
     * Returns null if no artwork is found in the file.
     */
    private String cacheEmbeddedCover(Song song) {
        try {
            File outFile = new File(coversDir, "cover_" + song.getId() + ".jpg");
            // If we already cached it before, return immediately
            if (outFile.exists()) return outFile.getAbsolutePath();

            byte[] artBytes = null;

            // Strategy 1: read embedded tag directly from the audio file
            android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
            try {
                Uri fileUri = Uri.parse(song.getPath());
                String scheme = fileUri.getScheme();
                if (scheme != null && (scheme.equals("content") || scheme.equals("file"))) {
                    // FIX: guardar el PFD para cerrarlo explícitamente y evitar fuga de recursos
                    android.os.ParcelFileDescriptor pfd =
                            resolver.openFileDescriptor(fileUri, "r");
                    if (pfd != null) {
                        try {
                            mmr.setDataSource(pfd.getFileDescriptor());
                        } finally {
                            pfd.close();
                        }
                    }
                } else {
                    // Absolute file path
                    mmr.setDataSource(song.getPath());
                }
                artBytes = mmr.getEmbeddedPicture(); // null if no embedded art
            } catch (Exception ignored) {
            } finally {
                try { mmr.release(); } catch (Exception ignored2) {}
            }

            // Strategy 2: MediaStore album art URI (works for MediaStore-indexed songs)
            // FIX: la URI correcta usa albumId, no el id de la canción
            if (artBytes == null) {
                try {
                    long albumId = resolveAlbumId(song);
                    if (albumId != -1) {
                        Uri msArt = Uri.parse(
                                "content://media/external/audio/albumart/" + albumId);
                        try (InputStream is = resolver.openInputStream(msArt)) {
                            if (is != null) {
                                java.io.ByteArrayOutputStream bos =
                                        new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                                artBytes = bos.toByteArray();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (artBytes == null || artBytes.length == 0) return null;

            // Decode, scale to 512x512 max (safe for notifications), write to disk
            Bitmap bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            if (bmp == null) return null;

            // Scale down if larger than 512×512 to avoid memory issues in notifications
            if (bmp.getWidth() > 512 || bmp.getHeight() > 512) {
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 512, 512, true);
                bmp.recycle();
                bmp = scaled;
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            bmp.recycle();
            return outFile.getAbsolutePath();

        } catch (Exception e) {
            android.util.Log.w("MikuRepo", "cacheEmbeddedCover failed for " + song.getTitle(), e);
            return null;
        }
    }

    public void scanFolder(String folderUriStr) { scanFolder(folderUriStr, null); }
    public void scanMedia(Callback<List<Song>> callback) { scanFolder(null, callback); }

    private List<Song> scanSAFFolder(String folderUriStr) {
        List<Song> songs = new ArrayList<>();
        try {
            Uri folderUri = Uri.parse(folderUriStr);
            // FIX: escaneo recursivo para incluir subcarpetas
            scanSAFDirectory(folderUri, DocumentsContract.getTreeDocumentId(folderUri), songs);
            enrichFromMediaStore(songs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

    /** Escanea recursivamente un directorio SAF incluyendo todos sus subdirectorios. */
    private void scanSAFDirectory(Uri treeUri, String parentDocId, List<Song> songs) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String mimeType   = cursor.getString(2);
                String name       = cursor.getString(1);
                String docId      = cursor.getString(0);
                if (mimeType == null) continue;

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    // Subdirectorio: descender recursivamente
                    scanSAFDirectory(treeUri, docId, songs);
                } else if (mimeType.startsWith("audio/")) {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    // FIX: usar Long.parseUnsignedLong como hash de 64 bits para reducir
                    // colisiones. String.hashCode() es int (32 bits) y Math.abs puede
                    // devolver negativo para Integer.MIN_VALUE.
                    long stableId = Math.abs((long) docId.hashCode()
                            ^ ((long) docId.length() << 32));
                    Song s = new Song(stableId, title, "Artista desconocido",
                            "Álbum desconocido", 0L, fileUri.toString(), System.currentTimeMillis());
                    songs.add(s);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("MikuRepo", "scanSAFDirectory error en " + parentDocId, e);
        }
    }

    private void enrichFromMediaStore(List<Song> songs) {
        for (Song s : songs) {
            String[] proj = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
            };
            String displayName = s.getTitle();
            String sel = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?";
            try (Cursor c = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj, sel, new String[]{"%" + displayName + "%"}, null)) {
                if (c != null && c.moveToFirst()) {
                    s.setTitle(c.getString(1));
                    s.setArtist(c.getString(2));
                    s.setAlbum(c.getString(3));
                    s.setDuration(c.getLong(4));
                }
            } catch (Exception ignored) {}
        }
    }

    private List<Song> scanMediaStore() {
        List<Song> songs = new ArrayList<>();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
                + " AND " + MediaStore.Audio.Media.DURATION + " >= 30000";

        try (Cursor cursor = resolver.query(collection, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor == null) return songs;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

            while (cursor.moveToNext()) {
                String artist = cursor.getString(artistCol);
                if ("<unknown>".equals(artist)) artist = "Artista desconocido";
                songs.add(new Song(cursor.getLong(idCol), cursor.getString(titleCol),
                        artist, cursor.getString(albumCol),
                        cursor.getLong(durCol), cursor.getString(dataCol),
                        cursor.getLong(dateCol)));
            }
        }
        return songs;
    }

    /**
     * Returns the absolute path where the cover for this song will be stored.
     * The file may not exist yet (copy is async) but the path is deterministic,
     * so the UI can update optimistically before the copy finishes.
     */
    public String getExpectedCoverPath(long songId) {
        return new File(coversDir, "cover_" + songId + ".jpg").getAbsolutePath();
    }

    /**
     * Callback que notifica cuando la copia local del archivo de portada ha terminado.
     * Sustituye los delays hardcodeados de 800/900ms que usaban PlayerActivity y SongsActivity.
     */
    public interface CoverUpdateCallback {
        void onCoverReady(long songId, String localPath);
    }

    /**
     * Sobreescritura sin callback — mantiene compatibilidad con código que no necesita
     * saber cuándo terminó la copia.
     */
    public void updateSongCover(Song song, String coverUriStr) {
        updateSongCover(song, coverUriStr, null);
    }

    /**
     * Copies the image at coverUri into filesDir/covers/ and stores the local
     * path in the DB. Invoca el callback en el hilo principal cuando la copia termina.
     */
    public void updateSongCover(Song song, String coverUriStr, CoverUpdateCallback callback) {
        final long songId = song.getId();
        executor.execute(() -> {
            String localPath = copyImageToCoversDir(songId, coverUriStr);
            String pathToStore = (localPath != null) ? localPath : coverUriStr;

            Song dbSong = db.songDao().getSongById(songId);
            if (dbSong != null) {
                dbSong.setCustomCoverPath(pathToStore);
                db.songDao().update(dbSong);
            } else {
                song.setCustomCoverPath(pathToStore);
                db.songDao().update(song);
            }

            if (localPath != null) {
                com.miku.player.utils.TagWriter.writeCover(appContext, song.getPath(), localPath,
                    new com.miku.player.utils.TagWriter.Result() {
                        public void onSuccess() {
                            android.util.Log.d("MikuRepo", "Cover written to audio file tags");
                        }
                        public void onError(String msg) {
                            android.util.Log.w("MikuRepo", "Could not write cover to tags: " + msg);
                        }
                    });
            }

            // Notificar al caller en el hilo principal cuando la ruta local ya existe
            if (callback != null && localPath != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onCoverReady(songId, localPath));
            }
        });
    }

    /**
     * Copies any URI-addressable image into filesDir/covers/cover_<id>.jpg.
     * Returns the absolute path of the saved file, or null on failure.
     */
    private String copyImageToCoversDir(long songId, String uriStr) {
        try {
            android.net.Uri uri = android.net.Uri.parse(uriStr);
            File outFile = new File(coversDir, "cover_" + songId + ".jpg");
            try (java.io.InputStream in = resolver.openInputStream(uri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(outFile)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            android.util.Log.e("MikuRepo", "Failed to copy cover image", e);
            return null;
        }
    }

    // ── Custom title ─────────────────────────────────────────────────────────────

    /**
     * Sets a custom display title for a song.
     * Also writes the title into the audio file tags so other apps see it too.
     * Pass null to revert to the original tag title.
     */
    public void updateCustomTitle(com.miku.player.data.model.Song song, String newTitle,
                                   android.app.Activity activity,
                                   com.miku.player.utils.TagWriter.Result callback) {
        final long songId = song.getId();
        // 1. Persist custom title in DB immediately
        fastExecutor.execute(() -> db.songDao().setCustomTitle(songId, newTitle));

        // 2. Write to audio file tags in background
        executor.execute(() -> {
            com.miku.player.utils.TagWriter.writeTitle(activity, song.getPath(),
                newTitle != null ? newTitle : song.getTitle(),
                new com.miku.player.utils.TagWriter.Result() {
                    public void onSuccess() {
                        activity.runOnUiThread(() -> callback.onSuccess());
                    }
                    public void onError(String msg) {
                        // DB is already updated — file write failing is non-critical
                        activity.runOnUiThread(() -> callback.onError(msg));
                    }
                });
        });
    }

    // ── Playlists ──────────────────────────────────────────────────────────────

    public LiveData<List<PlaylistWithSongs>> getAllPlaylistsWithSongs() {
        return db.playlistDao().getAllPlaylistsWithSongs();
    }

    public LiveData<PlaylistWithSongs> getPlaylistWithSongs(long id) {
        return db.playlistDao().getPlaylistWithSongs(id);
    }

    public void createPlaylist(String name, Callback<Long> callback) {
        executor.execute(() -> {
            long id = db.playlistDao().insertPlaylist(new Playlist(name));
            if (callback != null) callback.onResult(id);
        });
    }

    public void renamePlaylist(Playlist playlist, String newName) {
        executor.execute(() -> { playlist.setName(newName); db.playlistDao().updatePlaylist(playlist); });
    }

    public void deletePlaylist(Playlist playlist) {
        executor.execute(() -> db.playlistDao().deletePlaylist(playlist));
    }

    /**
     * Elimina una canción de la base de datos y, si deleteFile es true,
     * también borra el archivo de audio del almacenamiento del dispositivo.
     * Devuelve true en el callback si el archivo fue eliminado correctamente
     * (o si deleteFile era false), false si hubo un error al borrar el archivo.
     */
    public void deleteSong(Song song, boolean deleteFile, Callback<Boolean> callback) {
        // Registrar el ID como "excluido" antes del scan para que no sea reinsertado
        excludedSongIds.add(song.getId());
        executor.execute(() -> {
            // 1. Eliminar de la base de datos
            db.songDao().delete(song);

            boolean fileOk = true;
            if (deleteFile) {
                fileOk = deleteAudioFile(song);
            }

            // 2. Eliminar también portada personalizada si existe
            String coverPath = song.getCustomCoverPath();
            if (coverPath != null && coverPath.startsWith("/")) {
                try { new java.io.File(coverPath).delete(); } catch (Exception ignored) {}
            }

            final boolean result = fileOk;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onResult(result));
            }
        });
    }

    private boolean deleteAudioFile(Song song) {
        String path = song.getPath();
        if (path == null) return false;
        try {
            if (path.startsWith("content://")) {
                android.net.Uri uri = android.net.Uri.parse(path);
                // Intentar con DocumentsContract primero (SAF)
                try {
                    if (android.provider.DocumentsContract.isDocumentUri(appContext, uri)) {
                        return android.provider.DocumentsContract.deleteDocument(resolver, uri);
                    } else if (path.contains("/tree/")) {
                        // URI de árbol SAF — construir URI de documento
                        android.net.Uri docUri = android.provider.DocumentsContract
                                .buildDocumentUriUsingTree(uri,
                                        android.provider.DocumentsContract.getTreeDocumentId(uri));
                        return android.provider.DocumentsContract.deleteDocument(resolver, docUri);
                    }
                } catch (Exception ignored) {}
                // Fallback: MediaStore o ContentResolver genérico
                int deleted = resolver.delete(uri, null, null);
                return deleted > 0;
            } else {
                // Ruta absoluta del sistema de archivos
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        // Notificar a MediaStore para que retire la canción de su índice
                        try {
                            resolver.delete(
                                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                android.provider.MediaStore.Audio.Media.DATA + "=?",
                                new String[]{path});
                        } catch (Exception ignored) {}
                    }
                    return deleted;
                }
                return false;
            }
        } catch (Exception e) {
            android.util.Log.w("MikuRepo", "No se pudo eliminar el archivo: " + path, e);
            return false;
        }
    }

    public LiveData<List<Song>> getSongsInPlaylist(long playlistId) {
        return db.playlistDao().getSongsInPlaylist(playlistId);
    }

    public void addSongToPlaylist(long playlistId, Song song) {
        executor.execute(() -> {
            try {
                Song existing = db.songDao().getSongById(song.getId());
                if (existing == null) db.songDao().insert(song);
                int alreadyIn = db.playlistDao().getSongCountInPlaylistBySong(playlistId, song.getId());
                if (alreadyIn > 0) return;
                int position = db.playlistDao().getSongCountInPlaylist(playlistId);
                db.playlistDao().addSongToPlaylist(new PlaylistSong(playlistId, song.getId(), position));
            } catch (Exception e) {
                android.util.Log.e("MikuRepo", "Failed to add song to playlist", e);
            }
        });
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        executor.execute(() -> db.playlistDao().removeSongFromPlaylistById(playlistId, songId));
    }

    /** Reorder songs in a playlist: updates all positions in one transaction */
    public void reorderPlaylistSongs(long playlistId, List<Long> orderedSongIds) {
        executor.execute(() -> {
            for (int i = 0; i < orderedSongIds.size(); i++) {
                db.playlistDao().updateSongPosition(playlistId, orderedSongIds.get(i), i);
            }
        });
    }

    public void updatePlaylistCover(Playlist playlist, String coverPath) {
        executor.execute(() -> { playlist.setCoverPath(coverPath); db.playlistDao().updatePlaylist(playlist); });
    }

    /**
     * Resuelve el ALBUM_ID de MediaStore para una canción dado su path o URI.
     * Se necesita para construir la URI correcta de albumart (albumart/<ALBUM_ID>).
     * Devuelve -1 si no se encuentra.
     */
    private long resolveAlbumId(Song song) {
        String[] proj = { MediaStore.Audio.Media.ALBUM_ID };
        String sel;
        String[] args;
        String path = song.getPath();
        if (path.startsWith("content://")) {
            // Para URIs SAF intentar buscar por nombre de archivo
            sel  = MediaStore.Audio.Media.DISPLAY_NAME + " = ?";
            args = new String[]{ path.substring(path.lastIndexOf('/') + 1) };
        } else {
            sel  = MediaStore.Audio.Media.DATA + " = ?";
            args = new String[]{ path };
        }
        try (Cursor c = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, args, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Exception ignored) {}
        return -1;
    }

    /** Observa en tiempo real solo la canción con ese ID (más eficiente que getAllSongs). */
    public androidx.lifecycle.LiveData<Song> getSongByIdLive(long id) {
        return db.songDao().getSongByIdLive(id);
    }

    /**
     * Cierra los ExecutorService cuando ya no se necesitan.
     * Llamar desde Application.onTerminate() o pruebas unitarias.
     */
    public void shutdown() {
        executor.shutdown();
        fastExecutor.shutdown();
    }

    public interface Callback<T> { void onResult(T result); }
}
