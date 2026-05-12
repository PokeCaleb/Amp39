package com.miku.player.data.model;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;
import java.util.List;

public class PlaylistWithSongs {
    @Embedded
    public Playlist playlist;

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = @Junction(
            value = PlaylistSong.class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    public List<Song> songs;

    public int getSongCount() {
        return songs != null ? songs.size() : 0;
    }
}
