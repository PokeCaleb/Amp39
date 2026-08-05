package com.miku.player.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private String coverPath;   // custom cover image path
    private long createdAt;
    // Orden manual definido por el usuario al arrastrar (mantener presionado) una
    // lista en la pantalla de Playlists. Se inicializa igual a createdAt para que
    // una playlist recién creada aparezca primero, igual que antes de este campo.
    private long sortOrder;

    public Playlist() {}

    @Ignore
    public Playlist(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.sortOrder = this.createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCoverPath() { return coverPath; }
    public void setCoverPath(String coverPath) { this.coverPath = coverPath; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getSortOrder() { return sortOrder; }
    public void setSortOrder(long sortOrder) { this.sortOrder = sortOrder; }
}
