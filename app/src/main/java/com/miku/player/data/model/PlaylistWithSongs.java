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

    // FIX: Room NO garantiza que "songs" venga ordenado según la posición real
    // de cada canción en la lista — @Relation + @Junction devuelve los
    // resultados en el orden que le resulte más eficiente a la consulta
    // interna, no necesariamente position ASC. Por eso la portada automática
    // (mostrar la primera canción) podía no coincidir con la primera canción
    // real después de reordenar. Esta relación adicional trae las filas de la
    // tabla de unión CON su "position", para poder determinar la primera
    // canción real con certeza en getFirstSongByPosition().
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    public List<PlaylistSong> playlistSongs;

    public int getSongCount() {
        return songs != null ? songs.size() : 0;
    }

    /**
     * Devuelve la canción que realmente está primera en la lista, según su
     * "position" en playlist_songs — a diferencia de songs.get(0), que no
     * tiene garantía de reflejar el orden real. Se actualiza automáticamente
     * cuando el usuario reordena la lista, porque reordenar actualiza
     * "position" y esta consulta siempre recalcula en base a eso.
     */
    public Song getFirstSongByPosition() {
        if (songs == null || songs.isEmpty()) return null;
        if (playlistSongs == null || playlistSongs.isEmpty()) {
            return songs.get(0); // resguardo si por algún motivo no llegaron las filas de unión
        }
        long firstSongId = -1;
        int minPosition = Integer.MAX_VALUE;
        for (PlaylistSong ps : playlistSongs) {
            if (ps.position < minPosition) {
                minPosition = ps.position;
                firstSongId = ps.songId;
            }
        }
        for (Song s : songs) {
            if (s.getId() == firstSongId) return s;
        }
        return songs.get(0); // resguardo — no debería llegar aquí en condiciones normales
    }
}
