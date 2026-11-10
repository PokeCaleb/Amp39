package com.miku.player.data.repository;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.miku.player.data.model.*;
import java.util.List;

@Dao
interface PlaylistDao {

    @Insert
    long insertPlaylist(Playlist playlist);

    @Update
    void updatePlaylist(Playlist playlist);

    @Delete
    void deletePlaylist(Playlist playlist);

    @Query("SELECT * FROM playlists ORDER BY sortOrder DESC")
    LiveData<List<Playlist>> getAllPlaylists();

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY sortOrder DESC")
    LiveData<List<PlaylistWithSongs>> getAllPlaylistsWithSongs();

    @Query("UPDATE playlists SET sortOrder = :sortOrder WHERE id = :playlistId")
    void updatePlaylistSortOrder(long playlistId, long sortOrder);

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    LiveData<PlaylistWithSongs> getPlaylistWithSongs(long playlistId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long addSongToPlaylist(PlaylistSong playlistSong);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    void removeSongFromPlaylistById(long playlistId, long songId);

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    int getSongCountInPlaylist(long playlistId);

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    int getSongCountInPlaylistBySong(long playlistId, long songId);

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    List<PlaylistSong> getPlaylistSongEntries(long playlistId);

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId " +
           "WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC")
    LiveData<List<Song>> getSongsInPlaylist(long playlistId);

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId " +
           "WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC")
    List<Song> getSongsInPlaylistSync(long playlistId);

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    void removeSongFromAllPlaylists(long songId);

    @Query("SELECT DISTINCT playlistId FROM playlist_songs WHERE songId = :songId")
    List<Long> getPlaylistIdsForSong(long songId);

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    int getMaxPosition(long playlistId);

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    void updateSongPosition(long playlistId, long songId, int position);
}
