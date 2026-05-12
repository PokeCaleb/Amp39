package com.miku.player.data.repository;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.miku.player.data.model.*;

@Database(
    entities = {Song.class, Playlist.class, PlaylistSong.class},
    version = 7,
    exportSchema = false
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
                            // v6→v7: add customTitle column
                            new androidx.room.migration.Migration(6, 7) {
                                @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("ALTER TABLE songs ADD COLUMN customTitle TEXT");
                                }
                            }
                        )
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
