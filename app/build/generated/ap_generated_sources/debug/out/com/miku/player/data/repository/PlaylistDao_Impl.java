package com.miku.player.data.repository;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.miku.player.data.model.Playlist;
import com.miku.player.data.model.PlaylistSong;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.data.model.Song;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PlaylistDao_Impl implements PlaylistDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Playlist> __insertionAdapterOfPlaylist;

  private final EntityInsertionAdapter<PlaylistSong> __insertionAdapterOfPlaylistSong;

  private final EntityDeletionOrUpdateAdapter<Playlist> __deletionAdapterOfPlaylist;

  private final EntityDeletionOrUpdateAdapter<Playlist> __updateAdapterOfPlaylist;

  private final SharedSQLiteStatement __preparedStmtOfUpdatePlaylistSortOrder;

  private final SharedSQLiteStatement __preparedStmtOfRemoveSongFromPlaylistById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateSongPosition;

  public PlaylistDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPlaylist = new EntityInsertionAdapter<Playlist>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `playlists` (`id`,`name`,`coverPath`,`createdAt`,`sortOrder`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Playlist entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        if (entity.getCoverPath() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCoverPath());
        }
        statement.bindLong(4, entity.getCreatedAt());
        statement.bindLong(5, entity.getSortOrder());
      }
    };
    this.__insertionAdapterOfPlaylistSong = new EntityInsertionAdapter<PlaylistSong>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `playlist_songs` (`playlistId`,`songId`,`position`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final PlaylistSong entity) {
        statement.bindLong(1, entity.playlistId);
        statement.bindLong(2, entity.songId);
        statement.bindLong(3, entity.position);
      }
    };
    this.__deletionAdapterOfPlaylist = new EntityDeletionOrUpdateAdapter<Playlist>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `playlists` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Playlist entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfPlaylist = new EntityDeletionOrUpdateAdapter<Playlist>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `playlists` SET `id` = ?,`name` = ?,`coverPath` = ?,`createdAt` = ?,`sortOrder` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Playlist entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        if (entity.getCoverPath() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCoverPath());
        }
        statement.bindLong(4, entity.getCreatedAt());
        statement.bindLong(5, entity.getSortOrder());
        statement.bindLong(6, entity.getId());
      }
    };
    this.__preparedStmtOfUpdatePlaylistSortOrder = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE playlists SET sortOrder = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRemoveSongFromPlaylistById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM playlist_songs WHERE playlistId = ? AND songId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateSongPosition = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE playlist_songs SET position = ? WHERE playlistId = ? AND songId = ?";
        return _query;
      }
    };
  }

  @Override
  public long insertPlaylist(final Playlist playlist) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfPlaylist.insertAndReturnId(playlist);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public long addSongToPlaylist(final PlaylistSong playlistSong) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfPlaylistSong.insertAndReturnId(playlistSong);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deletePlaylist(final Playlist playlist) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfPlaylist.handle(playlist);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updatePlaylist(final Playlist playlist) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfPlaylist.handle(playlist);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updatePlaylistSortOrder(final long playlistId, final long sortOrder) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfUpdatePlaylistSortOrder.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, sortOrder);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, playlistId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfUpdatePlaylistSortOrder.release(_stmt);
    }
  }

  @Override
  public void removeSongFromPlaylistById(final long playlistId, final long songId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfRemoveSongFromPlaylistById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, playlistId);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, songId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfRemoveSongFromPlaylistById.release(_stmt);
    }
  }

  @Override
  public void updateSongPosition(final long playlistId, final long songId, final int position) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSongPosition.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, position);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, playlistId);
    _argIndex = 3;
    _stmt.bindLong(_argIndex, songId);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfUpdateSongPosition.release(_stmt);
    }
  }

  @Override
  public LiveData<List<Playlist>> getAllPlaylists() {
    final String _sql = "SELECT * FROM playlists ORDER BY sortOrder DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"playlists"}, false, new Callable<List<Playlist>>() {
      @Override
      @Nullable
      public List<Playlist> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCoverPath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverPath");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
          final List<Playlist> _result = new ArrayList<Playlist>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Playlist _item;
            _item = new Playlist();
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            _item.setId(_tmpId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            _item.setName(_tmpName);
            final String _tmpCoverPath;
            if (_cursor.isNull(_cursorIndexOfCoverPath)) {
              _tmpCoverPath = null;
            } else {
              _tmpCoverPath = _cursor.getString(_cursorIndexOfCoverPath);
            }
            _item.setCoverPath(_tmpCoverPath);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item.setCreatedAt(_tmpCreatedAt);
            final long _tmpSortOrder;
            _tmpSortOrder = _cursor.getLong(_cursorIndexOfSortOrder);
            _item.setSortOrder(_tmpSortOrder);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<PlaylistWithSongs>> getAllPlaylistsWithSongs() {
    final String _sql = "SELECT * FROM playlists ORDER BY sortOrder DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"playlist_songs", "songs",
        "playlists"}, true, new Callable<List<PlaylistWithSongs>>() {
      @Override
      @Nullable
      public List<PlaylistWithSongs> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
            final int _cursorIndexOfCoverPath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverPath");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
            final LongSparseArray<ArrayList<Song>> _collectionSongs = new LongSparseArray<ArrayList<Song>>();
            final LongSparseArray<ArrayList<PlaylistSong>> _collectionPlaylistSongs = new LongSparseArray<ArrayList<PlaylistSong>>();
            while (_cursor.moveToNext()) {
              final Long _tmpKey;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey = null;
              } else {
                _tmpKey = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey != null) {
                if (!_collectionSongs.containsKey(_tmpKey)) {
                  _collectionSongs.put(_tmpKey, new ArrayList<Song>());
                }
              }
              final Long _tmpKey_1;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey_1 = null;
              } else {
                _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey_1 != null) {
                if (!_collectionPlaylistSongs.containsKey(_tmpKey_1)) {
                  _collectionPlaylistSongs.put(_tmpKey_1, new ArrayList<PlaylistSong>());
                }
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsongsAscomMikuPlayerDataModelSong(_collectionSongs);
            __fetchRelationshipplaylistSongsAscomMikuPlayerDataModelPlaylistSong(_collectionPlaylistSongs);
            final List<PlaylistWithSongs> _result = new ArrayList<PlaylistWithSongs>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final PlaylistWithSongs _item;
              final Playlist _tmpPlaylist;
              if (!(_cursor.isNull(_cursorIndexOfId) && _cursor.isNull(_cursorIndexOfName) && _cursor.isNull(_cursorIndexOfCoverPath) && _cursor.isNull(_cursorIndexOfCreatedAt) && _cursor.isNull(_cursorIndexOfSortOrder))) {
                _tmpPlaylist = new Playlist();
                final long _tmpId;
                _tmpId = _cursor.getLong(_cursorIndexOfId);
                _tmpPlaylist.setId(_tmpId);
                final String _tmpName;
                if (_cursor.isNull(_cursorIndexOfName)) {
                  _tmpName = null;
                } else {
                  _tmpName = _cursor.getString(_cursorIndexOfName);
                }
                _tmpPlaylist.setName(_tmpName);
                final String _tmpCoverPath;
                if (_cursor.isNull(_cursorIndexOfCoverPath)) {
                  _tmpCoverPath = null;
                } else {
                  _tmpCoverPath = _cursor.getString(_cursorIndexOfCoverPath);
                }
                _tmpPlaylist.setCoverPath(_tmpCoverPath);
                final long _tmpCreatedAt;
                _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
                _tmpPlaylist.setCreatedAt(_tmpCreatedAt);
                final long _tmpSortOrder;
                _tmpSortOrder = _cursor.getLong(_cursorIndexOfSortOrder);
                _tmpPlaylist.setSortOrder(_tmpSortOrder);
              } else {
                _tmpPlaylist = null;
              }
              final ArrayList<Song> _tmpSongsCollection;
              final Long _tmpKey_2;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey_2 = null;
              } else {
                _tmpKey_2 = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey_2 != null) {
                _tmpSongsCollection = _collectionSongs.get(_tmpKey_2);
              } else {
                _tmpSongsCollection = new ArrayList<Song>();
              }
              final ArrayList<PlaylistSong> _tmpPlaylistSongsCollection;
              final Long _tmpKey_3;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey_3 = null;
              } else {
                _tmpKey_3 = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey_3 != null) {
                _tmpPlaylistSongsCollection = _collectionPlaylistSongs.get(_tmpKey_3);
              } else {
                _tmpPlaylistSongsCollection = new ArrayList<PlaylistSong>();
              }
              _item = new PlaylistWithSongs();
              _item.playlist = _tmpPlaylist;
              _item.songs = _tmpSongsCollection;
              _item.playlistSongs = _tmpPlaylistSongsCollection;
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<PlaylistWithSongs> getPlaylistWithSongs(final long playlistId) {
    final String _sql = "SELECT * FROM playlists WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    return __db.getInvalidationTracker().createLiveData(new String[] {"playlist_songs", "songs",
        "playlists"}, true, new Callable<PlaylistWithSongs>() {
      @Override
      @Nullable
      public PlaylistWithSongs call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
            final int _cursorIndexOfCoverPath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverPath");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
            final LongSparseArray<ArrayList<Song>> _collectionSongs = new LongSparseArray<ArrayList<Song>>();
            final LongSparseArray<ArrayList<PlaylistSong>> _collectionPlaylistSongs = new LongSparseArray<ArrayList<PlaylistSong>>();
            while (_cursor.moveToNext()) {
              final Long _tmpKey;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey = null;
              } else {
                _tmpKey = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey != null) {
                if (!_collectionSongs.containsKey(_tmpKey)) {
                  _collectionSongs.put(_tmpKey, new ArrayList<Song>());
                }
              }
              final Long _tmpKey_1;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey_1 = null;
              } else {
                _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey_1 != null) {
                if (!_collectionPlaylistSongs.containsKey(_tmpKey_1)) {
                  _collectionPlaylistSongs.put(_tmpKey_1, new ArrayList<PlaylistSong>());
                }
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsongsAscomMikuPlayerDataModelSong(_collectionSongs);
            __fetchRelationshipplaylistSongsAscomMikuPlayerDataModelPlaylistSong(_collectionPlaylistSongs);
            final PlaylistWithSongs _result;
            if (_cursor.moveToFirst()) {
              final Playlist _tmpPlaylist;
              if (!(_cursor.isNull(_cursorIndexOfId) && _cursor.isNull(_cursorIndexOfName) && _cursor.isNull(_cursorIndexOfCoverPath) && _cursor.isNull(_cursorIndexOfCreatedAt) && _cursor.isNull(_cursorIndexOfSortOrder))) {
                _tmpPlaylist = new Playlist();
                final long _tmpId;
                _tmpId = _cursor.getLong(_cursorIndexOfId);
                _tmpPlaylist.setId(_tmpId);
                final String _tmpName;
                if (_cursor.isNull(_cursorIndexOfName)) {
                  _tmpName = null;
                } else {
                  _tmpName = _cursor.getString(_cursorIndexOfName);
                }
                _tmpPlaylist.setName(_tmpName);
                final String _tmpCoverPath;
                if (_cursor.isNull(_cursorIndexOfCoverPath)) {
                  _tmpCoverPath = null;
                } else {
                  _tmpCoverPath = _cursor.getString(_cursorIndexOfCoverPath);
                }
                _tmpPlaylist.setCoverPath(_tmpCoverPath);
                final long _tmpCreatedAt;
                _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
                _tmpPlaylist.setCreatedAt(_tmpCreatedAt);
                final long _tmpSortOrder;
                _tmpSortOrder = _cursor.getLong(_cursorIndexOfSortOrder);
                _tmpPlaylist.setSortOrder(_tmpSortOrder);
              } else {
                _tmpPlaylist = null;
              }
              final ArrayList<Song> _tmpSongsCollection;
              final Long _tmpKey_2;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey_2 = null;
              } else {
                _tmpKey_2 = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey_2 != null) {
                _tmpSongsCollection = _collectionSongs.get(_tmpKey_2);
              } else {
                _tmpSongsCollection = new ArrayList<Song>();
              }
              final ArrayList<PlaylistSong> _tmpPlaylistSongsCollection;
              final Long _tmpKey_3;
              if (_cursor.isNull(_cursorIndexOfId)) {
                _tmpKey_3 = null;
              } else {
                _tmpKey_3 = _cursor.getLong(_cursorIndexOfId);
              }
              if (_tmpKey_3 != null) {
                _tmpPlaylistSongsCollection = _collectionPlaylistSongs.get(_tmpKey_3);
              } else {
                _tmpPlaylistSongsCollection = new ArrayList<PlaylistSong>();
              }
              _result = new PlaylistWithSongs();
              _result.playlist = _tmpPlaylist;
              _result.songs = _tmpSongsCollection;
              _result.playlistSongs = _tmpPlaylistSongsCollection;
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public int getSongCountInPlaylist(final long playlistId) {
    final String _sql = "SELECT COUNT(*) FROM playlist_songs WHERE playlistId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getSongCountInPlaylistBySong(final long playlistId, final long songId) {
    final String _sql = "SELECT COUNT(*) FROM playlist_songs WHERE playlistId = ? AND songId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, songId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<PlaylistSong> getPlaylistSongEntries(final long playlistId) {
    final String _sql = "SELECT * FROM playlist_songs WHERE playlistId = ? ORDER BY position ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
      final int _cursorIndexOfSongId = CursorUtil.getColumnIndexOrThrow(_cursor, "songId");
      final int _cursorIndexOfPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "position");
      final List<PlaylistSong> _result = new ArrayList<PlaylistSong>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final PlaylistSong _item;
        _item = new PlaylistSong();
        _item.playlistId = _cursor.getLong(_cursorIndexOfPlaylistId);
        _item.songId = _cursor.getLong(_cursorIndexOfSongId);
        _item.position = _cursor.getInt(_cursorIndexOfPosition);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<Song>> getSongsInPlaylist(final long playlistId) {
    final String _sql = "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = ? ORDER BY ps.position ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    return __db.getInvalidationTracker().createLiveData(new String[] {"songs",
        "playlist_songs"}, false, new Callable<List<Song>>() {
      @Override
      @Nullable
      public List<Song> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCustomTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "customTitle");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfCustomCoverPath = CursorUtil.getColumnIndexOrThrow(_cursor, "customCoverPath");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfDateModified = CursorUtil.getColumnIndexOrThrow(_cursor, "dateModified");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfFavoriteOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "favoriteOrder");
          final List<Song> _result = new ArrayList<Song>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Song _item;
            _item = new Song();
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            _item.setId(_tmpId);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            _item.setTitle(_tmpTitle);
            final String _tmpCustomTitle;
            if (_cursor.isNull(_cursorIndexOfCustomTitle)) {
              _tmpCustomTitle = null;
            } else {
              _tmpCustomTitle = _cursor.getString(_cursorIndexOfCustomTitle);
            }
            _item.setCustomTitle(_tmpCustomTitle);
            final String _tmpArtist;
            if (_cursor.isNull(_cursorIndexOfArtist)) {
              _tmpArtist = null;
            } else {
              _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            }
            _item.setArtist(_tmpArtist);
            final String _tmpAlbum;
            if (_cursor.isNull(_cursorIndexOfAlbum)) {
              _tmpAlbum = null;
            } else {
              _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            }
            _item.setAlbum(_tmpAlbum);
            final long _tmpDuration;
            _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            _item.setDuration(_tmpDuration);
            final String _tmpPath;
            if (_cursor.isNull(_cursorIndexOfPath)) {
              _tmpPath = null;
            } else {
              _tmpPath = _cursor.getString(_cursorIndexOfPath);
            }
            _item.setPath(_tmpPath);
            final String _tmpCustomCoverPath;
            if (_cursor.isNull(_cursorIndexOfCustomCoverPath)) {
              _tmpCustomCoverPath = null;
            } else {
              _tmpCustomCoverPath = _cursor.getString(_cursorIndexOfCustomCoverPath);
            }
            _item.setCustomCoverPath(_tmpCustomCoverPath);
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            _item.setDateAdded(_tmpDateAdded);
            final long _tmpDateModified;
            _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
            _item.setDateModified(_tmpDateModified);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            _item.setFavorite(_tmpIsFavorite);
            final long _tmpFavoriteOrder;
            _tmpFavoriteOrder = _cursor.getLong(_cursorIndexOfFavoriteOrder);
            _item.setFavoriteOrder(_tmpFavoriteOrder);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<Song> getSongsInPlaylistSync(final long playlistId) {
    final String _sql = "SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = ? ORDER BY ps.position ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, playlistId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfCustomTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "customTitle");
      final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
      final int _cursorIndexOfAlbum = CursorUtil.getColumnIndexOrThrow(_cursor, "album");
      final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
      final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
      final int _cursorIndexOfCustomCoverPath = CursorUtil.getColumnIndexOrThrow(_cursor, "customCoverPath");
      final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
      final int _cursorIndexOfDateModified = CursorUtil.getColumnIndexOrThrow(_cursor, "dateModified");
      final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
      final int _cursorIndexOfFavoriteOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "favoriteOrder");
      final List<Song> _result = new ArrayList<Song>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Song _item;
        _item = new Song();
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        _item.setId(_tmpId);
        final String _tmpTitle;
        if (_cursor.isNull(_cursorIndexOfTitle)) {
          _tmpTitle = null;
        } else {
          _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        }
        _item.setTitle(_tmpTitle);
        final String _tmpCustomTitle;
        if (_cursor.isNull(_cursorIndexOfCustomTitle)) {
          _tmpCustomTitle = null;
        } else {
          _tmpCustomTitle = _cursor.getString(_cursorIndexOfCustomTitle);
        }
        _item.setCustomTitle(_tmpCustomTitle);
        final String _tmpArtist;
        if (_cursor.isNull(_cursorIndexOfArtist)) {
          _tmpArtist = null;
        } else {
          _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
        }
        _item.setArtist(_tmpArtist);
        final String _tmpAlbum;
        if (_cursor.isNull(_cursorIndexOfAlbum)) {
          _tmpAlbum = null;
        } else {
          _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
        }
        _item.setAlbum(_tmpAlbum);
        final long _tmpDuration;
        _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
        _item.setDuration(_tmpDuration);
        final String _tmpPath;
        if (_cursor.isNull(_cursorIndexOfPath)) {
          _tmpPath = null;
        } else {
          _tmpPath = _cursor.getString(_cursorIndexOfPath);
        }
        _item.setPath(_tmpPath);
        final String _tmpCustomCoverPath;
        if (_cursor.isNull(_cursorIndexOfCustomCoverPath)) {
          _tmpCustomCoverPath = null;
        } else {
          _tmpCustomCoverPath = _cursor.getString(_cursorIndexOfCustomCoverPath);
        }
        _item.setCustomCoverPath(_tmpCustomCoverPath);
        final long _tmpDateAdded;
        _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
        _item.setDateAdded(_tmpDateAdded);
        final long _tmpDateModified;
        _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
        _item.setDateModified(_tmpDateModified);
        final boolean _tmpIsFavorite;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
        _tmpIsFavorite = _tmp != 0;
        _item.setFavorite(_tmpIsFavorite);
        final long _tmpFavoriteOrder;
        _tmpFavoriteOrder = _cursor.getLong(_cursorIndexOfFavoriteOrder);
        _item.setFavoriteOrder(_tmpFavoriteOrder);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private void __fetchRelationshipsongsAscomMikuPlayerDataModelSong(
      @NonNull final LongSparseArray<ArrayList<Song>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipsongsAscomMikuPlayerDataModelSong(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `songs`.`id` AS `id`,`songs`.`title` AS `title`,`songs`.`customTitle` AS `customTitle`,`songs`.`artist` AS `artist`,`songs`.`album` AS `album`,`songs`.`duration` AS `duration`,`songs`.`path` AS `path`,`songs`.`customCoverPath` AS `customCoverPath`,`songs`.`dateAdded` AS `dateAdded`,`songs`.`dateModified` AS `dateModified`,`songs`.`isFavorite` AS `isFavorite`,`songs`.`favoriteOrder` AS `favoriteOrder`,_junction.`playlistId` FROM `playlist_songs` AS _junction INNER JOIN `songs` ON (_junction.`songId` = `songs`.`id`) WHERE _junction.`playlistId` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      // _junction.playlistId;
      final int _itemKeyIndex = 12;
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfTitle = 1;
      final int _cursorIndexOfCustomTitle = 2;
      final int _cursorIndexOfArtist = 3;
      final int _cursorIndexOfAlbum = 4;
      final int _cursorIndexOfDuration = 5;
      final int _cursorIndexOfPath = 6;
      final int _cursorIndexOfCustomCoverPath = 7;
      final int _cursorIndexOfDateAdded = 8;
      final int _cursorIndexOfDateModified = 9;
      final int _cursorIndexOfIsFavorite = 10;
      final int _cursorIndexOfFavoriteOrder = 11;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<Song> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final Song _item_1;
          _item_1 = new Song();
          final long _tmpId;
          _tmpId = _cursor.getLong(_cursorIndexOfId);
          _item_1.setId(_tmpId);
          final String _tmpTitle;
          if (_cursor.isNull(_cursorIndexOfTitle)) {
            _tmpTitle = null;
          } else {
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
          }
          _item_1.setTitle(_tmpTitle);
          final String _tmpCustomTitle;
          if (_cursor.isNull(_cursorIndexOfCustomTitle)) {
            _tmpCustomTitle = null;
          } else {
            _tmpCustomTitle = _cursor.getString(_cursorIndexOfCustomTitle);
          }
          _item_1.setCustomTitle(_tmpCustomTitle);
          final String _tmpArtist;
          if (_cursor.isNull(_cursorIndexOfArtist)) {
            _tmpArtist = null;
          } else {
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
          }
          _item_1.setArtist(_tmpArtist);
          final String _tmpAlbum;
          if (_cursor.isNull(_cursorIndexOfAlbum)) {
            _tmpAlbum = null;
          } else {
            _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
          }
          _item_1.setAlbum(_tmpAlbum);
          final long _tmpDuration;
          _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
          _item_1.setDuration(_tmpDuration);
          final String _tmpPath;
          if (_cursor.isNull(_cursorIndexOfPath)) {
            _tmpPath = null;
          } else {
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
          }
          _item_1.setPath(_tmpPath);
          final String _tmpCustomCoverPath;
          if (_cursor.isNull(_cursorIndexOfCustomCoverPath)) {
            _tmpCustomCoverPath = null;
          } else {
            _tmpCustomCoverPath = _cursor.getString(_cursorIndexOfCustomCoverPath);
          }
          _item_1.setCustomCoverPath(_tmpCustomCoverPath);
          final long _tmpDateAdded;
          _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
          _item_1.setDateAdded(_tmpDateAdded);
          final long _tmpDateModified;
          _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
          _item_1.setDateModified(_tmpDateModified);
          final boolean _tmpIsFavorite;
          final int _tmp;
          _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
          _tmpIsFavorite = _tmp != 0;
          _item_1.setFavorite(_tmpIsFavorite);
          final long _tmpFavoriteOrder;
          _tmpFavoriteOrder = _cursor.getLong(_cursorIndexOfFavoriteOrder);
          _item_1.setFavoriteOrder(_tmpFavoriteOrder);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }

  private void __fetchRelationshipplaylistSongsAscomMikuPlayerDataModelPlaylistSong(
      @NonNull final LongSparseArray<ArrayList<PlaylistSong>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipplaylistSongsAscomMikuPlayerDataModelPlaylistSong(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `playlistId`,`songId`,`position` FROM `playlist_songs` WHERE `playlistId` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "playlistId");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfPlaylistId = 0;
      final int _cursorIndexOfSongId = 1;
      final int _cursorIndexOfPosition = 2;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<PlaylistSong> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final PlaylistSong _item_1;
          _item_1 = new PlaylistSong();
          _item_1.playlistId = _cursor.getLong(_cursorIndexOfPlaylistId);
          _item_1.songId = _cursor.getLong(_cursorIndexOfSongId);
          _item_1.position = _cursor.getInt(_cursorIndexOfPosition);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
