package com.miku.player.ui.playlists;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.miku.player.data.model.Playlist;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.data.model.Song;
import com.miku.player.data.repository.MusicRepository;
import java.util.List;

public class PlaylistDetailViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final LiveData<PlaylistWithSongs> playlist;
    private final LiveData<List<Song>> directSongs;

    public PlaylistDetailViewModel(Application app, long playlistId) {
        super(app);
        repository = MusicRepository.getInstance(app);
        playlist = repository.getPlaylistWithSongs(playlistId);
        directSongs = repository.getSongsInPlaylist(playlistId);
    }

    public LiveData<PlaylistWithSongs> getPlaylist() { return playlist; }
    public LiveData<List<Song>> getDirectSongs() { return directSongs; }

    public void renamePlaylist(Playlist playlist, String newName) { repository.renamePlaylist(playlist, newName); }
    public void deletePlaylist(Playlist playlist) { repository.deletePlaylist(playlist); }
    public void removeSong(long playlistId, long songId) { repository.removeSongFromPlaylist(playlistId, songId); }
    public void updatePlaylistCover(Playlist playlist, String coverPath) { repository.updatePlaylistCover(playlist, coverPath); }
    public void reorderSongs(long playlistId, List<Long> orderedIds) { repository.reorderPlaylistSongs(playlistId, orderedIds); }
    public void toggleFavorite(Song song) { repository.toggleFavorite(song); }
    public void setFavorite(Song song, boolean state) { repository.setFavorite(song, state); }

    public void updateSongCover(Song song, String coverPath,
                                MusicRepository.CoverUpdateCallback callback) {
        repository.updateSongCover(song, coverPath, callback);
    }

    public void updateSongCover(Song song, String coverPath, boolean writeToFile,
                                MusicRepository.CoverUpdateCallback callback) {
        repository.updateSongCover(song, coverPath, writeToFile, callback);
    }

    public void updateCustomTitle(Song song, String newTitle, android.app.Activity activity) {
        repository.updateCustomTitle(song, newTitle, activity,
            new com.miku.player.utils.TagWriter.Result() {
                public void onSuccess() {}
                public void onError(String msg) {
                    android.util.Log.w("PlaylistDetailVM", "Title tag write failed: " + msg);
                    android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_LONG).show();
                }
            });
    }

    public void deleteSong(Song song, boolean deleteFile,
                           MusicRepository.Callback<Boolean> cb) {
        repository.deleteSong(song, deleteFile, cb);
    }
}

// ─── Factory ────────────────────────────────────────────────────────────────
class PlaylistDetailViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final long playlistId;

    PlaylistDetailViewModelFactory(Application app, long playlistId) {
        this.app = app; this.playlistId = playlistId;
    }

    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PlaylistDetailViewModel.class))
            return (T) new PlaylistDetailViewModel(app, playlistId);
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
