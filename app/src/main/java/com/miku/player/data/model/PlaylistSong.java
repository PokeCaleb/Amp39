package com.miku.player.data.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

@Entity(
    tableName = "playlist_songs",
    primaryKeys = {"playlistId", "songId"},
    foreignKeys = {
        // Only keep FK to Playlist so deleting a playlist cascades to its songs
        // FK to Song is intentionally removed to allow SAF hash-ID songs
        // that might not yet be in the songs table
        @ForeignKey(entity = Playlist.class, parentColumns = "id",
                    childColumns = "playlistId", onDelete = ForeignKey.CASCADE)
    },
    indices = {
        @Index("playlistId"),
        @Index("songId")
    }
)
public class PlaylistSong {
    public long playlistId;
    public long songId;
    public int position;

    // Required no-arg constructor for Room
    public PlaylistSong() {}

    @Ignore
    public PlaylistSong(long playlistId, long songId, int position) {
        this.playlistId = playlistId;
        this.songId = songId;
        this.position = position;
    }
}
