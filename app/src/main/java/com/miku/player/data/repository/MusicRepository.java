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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicRepository {

    private final AppDatabase db;
    private final ContentResolver resolver;
    private final android.content.Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Dedicated executor for fast, lightweight DB writes (favorites, etc.)
    // Separate from the main executor so scan/cover operations don't block them.
    private final ExecutorService fastExecutor = Executors.newSingleThreadExecutor();
    private static volatile MusicRepository INSTANCE;
    private final File coversDir;

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
     * Safer than toggleFavorite when the caller needs a guaranteed outcome
     * (e.g. "remove from favorites" should always set false).
     */
    public void setFavorite(Song song, boolean favorite) {
        final long songId = song.getId();
        fastExecutor.execute(() -> db.songDao().setFavorite(songId, favorite));
    }

    public void toggleFavorite(Song song) {
        final boolean newFav = song.isFavorite();
        final long songId = song.getId();
        fastExecutor.execute(() -> db.songDao().setFavorite(songId, newFav));
    }

    public void scanFolder(String folderUriStr, Callback<List<Song>> callback) {
        executor.execute(() -> {
            List<Song> songs = folderUriStr != null
                    ? scanSAFFolder(folderUriStr)
                    : scanMediaStore();

            for (Song newSong : songs) {
                Song existing = db.songDao().getSongById(newSong.getId());
                if (existing != null) {
                    // Preserve user data
                    newSong.setFavorite(existing.isFavorite());
                    // Preserve custom title (user-set name)
                    newSong.setCustomTitle(existing.getCustomTitle());

                    // Always update raw metadata from the fresh scan so tag edits
                    // in external apps are reflected. customTitle overrides on display.

                    // Keep custom cover if the file still exists; otherwise re-extract
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
                    // Brand new song — extract and cache its embedded cover
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
                    mmr.setDataSource(resolver.openFileDescriptor(fileUri, "r")
                            .getFileDescriptor());
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
            if (artBytes == null) {
                try {
                    Uri msArt = Uri.parse(
                            "content://media/external/audio/albumart/" + song.getId());
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
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri, DocumentsContract.getTreeDocumentId(folderUri));

            String[] projection = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
            };

            try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
                if (cursor == null) return songs;
                while (cursor.moveToNext()) {
                    String mimeType = cursor.getString(2);
                    String name = cursor.getString(1);
                    String docId = cursor.getString(0);
                    if (mimeType == null || !mimeType.startsWith("audio/")) continue;
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId);
                    String title = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    // Use the document ID (not the full URI) as the stable identifier.
                    // The docId is assigned by the filesystem and does NOT change when
                    // the file is renamed, so renaming a file preserves its DB record.
                    long stableId = Math.abs(docId.hashCode());
                    Song s = new Song(stableId, title, "Artista desconocido",
                            "Álbum desconocido", 0L, fileUri.toString(), System.currentTimeMillis());
                    songs.add(s);
                }
            }
            enrichFromMediaStore(songs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
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
     * Copies the image at coverUri into filesDir/covers/ and stores the local
     * path in the DB. This ensures covers are always in a known location that
     * the backup system can include.
     */
    public void updateSongCover(Song song, String coverUriStr) {
        executor.execute(() -> {
            String localPath = copyImageToCoversDir(song.getId(), coverUriStr);
            String pathToStore = (localPath != null) ? localPath : coverUriStr;

            // Fetch the current DB row so we don't overwrite isFavorite or other
            // fields that may have changed since the UI object was created.
            Song dbSong = db.songDao().getSongById(song.getId());
            if (dbSong != null) {
                dbSong.setCustomCoverPath(pathToStore);
                db.songDao().update(dbSong);
            } else {
                song.setCustomCoverPath(pathToStore);
                db.songDao().update(song);
            }

            // Write the cover into the audio file tags so other apps see it too.
            // This is best-effort: if the file is not writable (e.g. locked SD card),
            // the app cover still works fine from localPath.
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

    public interface Callback<T> { void onResult(T result); }
}
