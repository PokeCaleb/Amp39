package com.miku.player.data.repository;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.miku.player.data.model.*;

@Database(
    entities = {Song.class, Playlist.class, PlaylistSong.class},
    version = 11,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract SongDao songDao();
    public abstract PlaylistDao playlistDao();

    public static synchronized void resetInstance() {
        if (INSTANCE != null) {
            try { INSTANCE.close(); } catch (Exception ignored) {}
            INSTANCE = null;
        }
    }

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "miku_player.db"
                        )
                        .addMigrations(
                            new androidx.room.migration.Migration(3, 4) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {}
                            },
                            new androidx.room.migration.Migration(4, 5) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("CREATE TABLE IF NOT EXISTS playlist_songs_new (" +
                                        "playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, " +
                                        "position INTEGER NOT NULL, PRIMARY KEY(playlistId, songId), " +
                                        "FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE)");
                                    db.execSQL("INSERT OR IGNORE INTO playlist_songs_new SELECT * FROM playlist_songs");
                                    db.execSQL("DROP TABLE playlist_songs");
                                    db.execSQL("ALTER TABLE playlist_songs_new RENAME TO playlist_songs");
                                    db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs(playlistId)");
                                    db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)");
                                }
                            },
                            new androidx.room.migration.Migration(5, 6) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0");
                                }
                            },
                            new androidx.room.migration.Migration(6, 7) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("ALTER TABLE songs ADD COLUMN customTitle TEXT");
                                }
                            },
                            // v7→v8: sin cambios de esquema; versión subida al eliminar
                            // fallbackToDestructiveMigration() para proteger los datos del usuario.
                            new androidx.room.migration.Migration(7, 8) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {}
                            },
                            // v8→v9: columna favoriteOrder para preservar el orden de selección
                            new androidx.room.migration.Migration(8, 9) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("ALTER TABLE songs ADD COLUMN favoriteOrder INTEGER NOT NULL DEFAULT 0");
                                    // Migrar favoritos existentes: asignar orden basado en rowid
                                    // para que no queden todos en 0 (lo que haría el orden arbitrario)
                                    db.execSQL("UPDATE songs SET favoriteOrder = id WHERE isFavorite = 1");
                                }
                            },
                            // v9→v10: columna sortOrder para permitir reordenar playlists
                            // arrastrándolas (antes solo se ordenaban por fecha de creación).
                            new androidx.room.migration.Migration(9, 10) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("ALTER TABLE playlists ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0");
                                    // Semilla: igualar sortOrder a createdAt para que el orden
                                    // existente (más reciente primero) no cambie tras la migración.
                                    db.execSQL("UPDATE playlists SET sortOrder = createdAt");
                                }
                            },
                            // v10→v11: separa "fecha agregada" (dateAdded, ya existía y se
                            // preserva entre escaneos) de "fecha modificada" (dateModified,
                            // nueva — se actualiza en cada escaneo con la fecha real del
                            // archivo). Antes solo existía dateAdded, y antes de eso mismo
                            // se comportaba como dateModified (se sobreescribía en cada
                            // escaneo) — separarlas permite ofrecer ambos criterios de orden.
                            new androidx.room.migration.Migration(10, 11) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("ALTER TABLE songs ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0");
                                    // Semilla: como no sabemos la fecha real de modificación
                                    // todavía (se llenará en el próximo escaneo), arrancar
                                    // igual a dateAdded es la aproximación más razonable.
                                    db.execSQL("UPDATE songs SET dateModified = dateAdded");
                                }
                            }
                        )
                        // fallbackToDestructiveMigration() eliminado: borraba todos los datos
                        // silenciosamente ante cualquier migración no registrada.
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
