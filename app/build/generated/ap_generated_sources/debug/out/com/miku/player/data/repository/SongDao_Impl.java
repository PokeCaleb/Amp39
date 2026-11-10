package com.miku.player.data.repository;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SongDao_Impl implements SongDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Song> __insertionAdapterOfSong;

  private final EntityDeletionOrUpdateAdapter<Song> __deletionAdapterOfSong;

  private final EntityDeletionOrUpdateAdapter<Song> __updateAdapterOfSong;

  private final SharedSQLiteStatement __preparedStmtOfSetFavoriteWithOrder;

  private final SharedSQLiteStatement __preparedStmtOfSetFavorite;

  private final SharedSQLiteStatement __preparedStmtOfSetCustomTitle;

  private final SharedSQLiteStatement __preparedStmtOfSetCustomCoverPath;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOrphanSongs;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public SongDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSong = new EntityInsertionAdapter<Song>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `songs` (`id`,`title`,`customTitle`,`artist`,`album`,`duration`,`path`,`customCoverPath`,`dateAdded`,`dateModified`,`isFavorite`,`favoriteOrder`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Song entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getTitle() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getTitle());
        }
        if (entity.getCustomTitle() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCustomTitle());
        }
        if (entity.getArtist() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getArtist());
        }
        if (entity.getAlbum() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAlbum());
        }
        statement.bindLong(6, entity.getDuration());
        if (entity.getPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getPath());
        }
        if (entity.getCustomCoverPath() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getCustomCoverPath());
        }
        statement.bindLong(9, entity.getDateAdded());
        statement.bindLong(10, entity.getDateModified());
        final int _tmp = entity.isFavorite() ? 1 : 0;
        statement.bindLong(11, _tmp);
        statement.bindLong(12, entity.getFavoriteOrder());
      }
    };
    this.__deletionAdapterOfSong = new EntityDeletionOrUpdateAdapter<Song>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `songs` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Song entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfSong = new EntityDeletionOrUpdateAdapter<Song>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `songs` SET `id` = ?,`title` = ?,`customTitle` = ?,`artist` = ?,`album` = ?,`duration` = ?,`path` = ?,`customCoverPath` = ?,`dateAdded` = ?,`dateModified` = ?,`isFavorite` = ?,`favoriteOrder` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement, final Song entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getTitle() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getTitle());
        }
        if (entity.getCustomTitle() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCustomTitle());
        }
        if (entity.getArtist() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getArtist());
        }
        if (entity.getAlbum() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAlbum());
        }
        statement.bindLong(6, entity.getDuration());
        if (entity.getPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getPath());
        }
        if (entity.getCustomCoverPath() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getCustomCoverPath());
        }
        statement.bindLong(9, entity.getDateAdded());
        statement.bindLong(10, entity.getDateModified());
        final int _tmp = entity.isFavorite() ? 1 : 0;
        statement.bindLong(11, _tmp);
        statement.bindLong(12, entity.getFavoriteOrder());
        statement.bindLong(13, entity.getId());
      }
    };
    this.__preparedStmtOfSetFavoriteWithOrder = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET isFavorite = ?, favoriteOrder = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetFavorite = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET isFavorite = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetCustomTitle = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET customTitle = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetCustomCoverPath = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE songs SET customCoverPath = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOrphanSongs = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM songs WHERE id NOT IN (SELECT DISTINCT songId FROM playlist_songs)";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM songs";
        return _query;
      }
    };
  }

  @Override
  public void insertAll(final List<Song> songs) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfSong.insert(songs);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insert(final Song song) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfSong.insert(song);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final Song song) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfSong.handle(song);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final Song song) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfSong.handle(song);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void setFavoriteWithOrder(final long id, final boolean fav, final long order) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetFavoriteWithOrder.acquire();
    int _argIndex = 1;
    final int _tmp = fav ? 1 : 0;
    _stmt.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, order);
    _argIndex = 3;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetFavoriteWithOrder.release(_stmt);
    }
  }

  @Override
  public void setFavorite(final long id, final boolean fav) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetFavorite.acquire();
    int _argIndex = 1;
    final int _tmp = fav ? 1 : 0;
    _stmt.bindLong(_argIndex, _tmp);
    _argIndex = 2;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetFavorite.release(_stmt);
    }
  }

  @Override
  public void setCustomTitle(final long id, final String title) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetCustomTitle.acquire();
    int _argIndex = 1;
    if (title == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, title);
    }
    _argIndex = 2;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetCustomTitle.release(_stmt);
    }
  }

  @Override
  public void setCustomCoverPath(final long id, final String path) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfSetCustomCoverPath.acquire();
    int _argIndex = 1;
    if (path == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, path);
    }
    _argIndex = 2;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfSetCustomCoverPath.release(_stmt);
    }
  }

  @Override
  public void deleteOrphanSongs() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOrphanSongs.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteOrphanSongs.release(_stmt);
    }
  }

  @Override
  public void deleteAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteAll.release(_stmt);
    }
  }

  @Override
  public LiveData<List<Song>> getAllSongs() {
    final String _sql = "SELECT * FROM songs ORDER BY title ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"songs"}, false, new Callable<List<Song>>() {
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
  public LiveData<List<Song>> search(final String query) {
    final String _sql = "SELECT * FROM songs WHERE title LIKE ? ESCAPE '\\' OR artist LIKE ? ESCAPE '\\' OR (customTitle IS NOT NULL AND customTitle LIKE ? ESCAPE '\\') ORDER BY title ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 2;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 3;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"songs"}, false, new Callable<List<Song>>() {
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
  public Song getSongById(final long id) {
    final String _sql = "SELECT * FROM songs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
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
      final Song _result;
      if (_cursor.moveToFirst()) {
        _result = new Song();
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        _result.setId(_tmpId);
        final String _tmpTitle;
        if (_cursor.isNull(_cursorIndexOfTitle)) {
          _tmpTitle = null;
        } else {
          _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        }
        _result.setTitle(_tmpTitle);
        final String _tmpCustomTitle;
        if (_cursor.isNull(_cursorIndexOfCustomTitle)) {
          _tmpCustomTitle = null;
        } else {
          _tmpCustomTitle = _cursor.getString(_cursorIndexOfCustomTitle);
        }
        _result.setCustomTitle(_tmpCustomTitle);
        final String _tmpArtist;
        if (_cursor.isNull(_cursorIndexOfArtist)) {
          _tmpArtist = null;
        } else {
          _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
        }
        _result.setArtist(_tmpArtist);
        final String _tmpAlbum;
        if (_cursor.isNull(_cursorIndexOfAlbum)) {
          _tmpAlbum = null;
        } else {
          _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
        }
        _result.setAlbum(_tmpAlbum);
        final long _tmpDuration;
        _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
        _result.setDuration(_tmpDuration);
        final String _tmpPath;
        if (_cursor.isNull(_cursorIndexOfPath)) {
          _tmpPath = null;
        } else {
          _tmpPath = _cursor.getString(_cursorIndexOfPath);
        }
        _result.setPath(_tmpPath);
        final String _tmpCustomCoverPath;
        if (_cursor.isNull(_cursorIndexOfCustomCoverPath)) {
          _tmpCustomCoverPath = null;
        } else {
          _tmpCustomCoverPath = _cursor.getString(_cursorIndexOfCustomCoverPath);
        }
        _result.setCustomCoverPath(_tmpCustomCoverPath);
        final long _tmpDateAdded;
        _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
        _result.setDateAdded(_tmpDateAdded);
        final long _tmpDateModified;
        _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
        _result.setDateModified(_tmpDateModified);
        final boolean _tmpIsFavorite;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
        _tmpIsFavorite = _tmp != 0;
        _result.setFavorite(_tmpIsFavorite);
        final long _tmpFavoriteOrder;
        _tmpFavoriteOrder = _cursor.getLong(_cursorIndexOfFavoriteOrder);
        _result.setFavoriteOrder(_tmpFavoriteOrder);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<Song> getSongByIdLive(final long id) {
    final String _sql = "SELECT * FROM songs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return __db.getInvalidationTracker().createLiveData(new String[] {"songs"}, false, new Callable<Song>() {
      @Override
      @Nullable
      public Song call() throws Exception {
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
          final Song _result;
          if (_cursor.moveToFirst()) {
            _result = new Song();
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            _result.setId(_tmpId);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            _result.setTitle(_tmpTitle);
            final String _tmpCustomTitle;
            if (_cursor.isNull(_cursorIndexOfCustomTitle)) {
              _tmpCustomTitle = null;
            } else {
              _tmpCustomTitle = _cursor.getString(_cursorIndexOfCustomTitle);
            }
            _result.setCustomTitle(_tmpCustomTitle);
            final String _tmpArtist;
            if (_cursor.isNull(_cursorIndexOfArtist)) {
              _tmpArtist = null;
            } else {
              _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            }
            _result.setArtist(_tmpArtist);
            final String _tmpAlbum;
            if (_cursor.isNull(_cursorIndexOfAlbum)) {
              _tmpAlbum = null;
            } else {
              _tmpAlbum = _cursor.getString(_cursorIndexOfAlbum);
            }
            _result.setAlbum(_tmpAlbum);
            final long _tmpDuration;
            _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            _result.setDuration(_tmpDuration);
            final String _tmpPath;
            if (_cursor.isNull(_cursorIndexOfPath)) {
              _tmpPath = null;
            } else {
              _tmpPath = _cursor.getString(_cursorIndexOfPath);
            }
            _result.setPath(_tmpPath);
            final String _tmpCustomCoverPath;
            if (_cursor.isNull(_cursorIndexOfCustomCoverPath)) {
              _tmpCustomCoverPath = null;
            } else {
              _tmpCustomCoverPath = _cursor.getString(_cursorIndexOfCustomCoverPath);
            }
            _result.setCustomCoverPath(_tmpCustomCoverPath);
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            _result.setDateAdded(_tmpDateAdded);
            final long _tmpDateModified;
            _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
            _result.setDateModified(_tmpDateModified);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            _result.setFavorite(_tmpIsFavorite);
            final long _tmpFavoriteOrder;
            _tmpFavoriteOrder = _cursor.getLong(_cursorIndexOfFavoriteOrder);
            _result.setFavoriteOrder(_tmpFavoriteOrder);
          } else {
            _result = null;
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
  public LiveData<List<Song>> getFavoriteSongs() {
    final String _sql = "SELECT * FROM songs WHERE isFavorite = 1 ORDER BY favoriteOrder ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"songs"}, false, new Callable<List<Song>>() {
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
  public List<Long> getRemovableSongIds() {
    final String _sql = "SELECT id FROM songs WHERE isFavorite = 0 AND (customTitle IS NULL OR customTitle = '') AND id NOT IN (SELECT DISTINCT songId FROM playlist_songs)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Long _item;
        if (_cursor.isNull(0)) {
          _item = null;
        } else {
          _item = _cursor.getLong(0);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public void deleteByIds(final List<Long> ids) {
    __db.assertNotSuspendingTransaction();
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("DELETE FROM songs WHERE id IN (");
    final int _inputSize = ids == null ? 1 : ids.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
    int _argIndex = 1;
    if (ids == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (Long _item : ids) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    __db.beginTransaction();
    try {
      _stmt.executeUpdateDelete();
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
