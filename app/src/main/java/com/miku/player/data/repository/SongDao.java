package com.miku.player.data.repository;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.miku.player.data.model.Song;
import java.util.List;

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Song> songs);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Song song);

    @Update
    void update(Song song);

    @Delete
    void delete(Song song);

    @Query("SELECT * FROM songs ORDER BY title ASC")
    LiveData<List<Song>> getAllSongs();

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query OR (customTitle IS NOT NULL AND customTitle LIKE :query) ORDER BY title ASC")
    LiveData<List<Song>> search(String query);

    @Query("SELECT * FROM songs WHERE id = :id")
    Song getSongById(long id);

    /** Versión reactiva — emite cada vez que cambia solo esta canción. */
    @Query("SELECT * FROM songs WHERE id = :id")
    LiveData<Song> getSongByIdLive(long id);

    // Favoritos ordenados por fecha de selección: el primero marcado aparece primero
    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY favoriteOrder ASC")
    LiveData<List<Song>> getFavoriteSongs();

    @Query("UPDATE songs SET isFavorite = :fav, favoriteOrder = :order WHERE id = :id")
    void setFavoriteWithOrder(long id, boolean fav, long order);

    @Query("UPDATE songs SET isFavorite = :fav WHERE id = :id")
    void setFavorite(long id, boolean fav);

    @Query("UPDATE songs SET customTitle = :title WHERE id = :id")
    void setCustomTitle(long id, String title);

    @Query("UPDATE songs SET customCoverPath = :path WHERE id = :id")
    void setCustomCoverPath(long id, String path);

    @Query("DELETE FROM songs WHERE id NOT IN (SELECT DISTINCT songId FROM playlist_songs) AND id NOT IN (:scannedIds)")
    void deleteOrphanSongsNotIn(List<Long> scannedIds);

    @Query("DELETE FROM songs WHERE id NOT IN (SELECT DISTINCT songId FROM playlist_songs)")
    void deleteOrphanSongs();

    @Query("DELETE FROM songs")
    void deleteAll();
}
