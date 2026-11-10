package com.miku.player.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "songs")
public class Song {
    @PrimaryKey
    private long id;
    private String title;       // original title from audio file tags
    private String customTitle; // user-set title (null = use original)
    private String artist;
    private String album;
    private long duration;
    private String path;
    private String customCoverPath;
    private long dateAdded;
    // Fecha real de última modificación del archivo — a diferencia de dateAdded
    // (que se preserva y nunca cambia por una edición), esta SÍ se actualiza en
    // cada escaneo para reflejar la fecha real del archivo, igual que muestra el
    // administrador de archivos del sistema. Sirve para el criterio de orden
    // "Modificadas" en Recientes.
    private long dateModified;
    private boolean isFavorite;
    private long favoriteOrder; // timestamp en que se marcó favorito; 0 = nunca

    public Song() {}

    @Ignore
    public Song(long id, String title, String artist, String album,
                long duration, String path, long dateAdded) {
        this.id = id; this.title = title; this.artist = artist;
        this.album = album; this.duration = duration;
        this.path = path; this.dateAdded = dateAdded;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    /** Raw title from the audio file tags. */
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    /** User-set custom title. Null means use getTitle(). */
    public String getCustomTitle() { return customTitle; }
    public void setCustomTitle(String t) { this.customTitle = t; }

    /**
     * Returns the title to display: custom title if set, otherwise original tag title.
     */
    public String getDisplayTitle() {
        if (customTitle != null && !customTitle.trim().isEmpty()) return customTitle;
        return title != null ? title : "";
    }

    public String getArtist() { return artist != null ? artist : "Artista desconocido"; }
    public void setArtist(String artist) { this.artist = artist; }
    public String getAlbum() { return album != null ? album : "Álbum desconocido"; }
    public void setAlbum(String album) { this.album = album; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getCustomCoverPath() { return customCoverPath; }
    public void setCustomCoverPath(String p) { this.customCoverPath = p; }
    public long getDateAdded() { return dateAdded; }
    public void setDateAdded(long d) { this.dateAdded = d; }
    public long getDateModified() { return dateModified; }
    public void setDateModified(long d) { this.dateModified = d; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public long getFavoriteOrder() { return favoriteOrder; }
    public void setFavoriteOrder(long favoriteOrder) { this.favoriteOrder = favoriteOrder; }

    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // Equality based on stable ID so indexOf(), contains() y DiffUtil funcionen
    // correctamente cuando las listas provienen de instancias distintas.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        return id == ((Song) o).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
