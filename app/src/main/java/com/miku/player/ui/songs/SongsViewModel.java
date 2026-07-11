package com.miku.player.ui.songs;

import android.app.Activity;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.miku.player.data.model.Song;
import com.miku.player.data.repository.MusicRepository;
import java.util.List;

public class SongsViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private LiveData<List<Song>> songs;

    public SongsViewModel(Application app) {
        super(app);
        repository = MusicRepository.getInstance(app);
        songs = repository.getAllSongs();
    }

    public LiveData<List<Song>> getSongs() { return songs; }

    public void scanFolder(String folderUri) {
        repository.scanFolder(folderUri);
    }

    public void updateSongCover(Song song, String coverPath) {
        repository.updateSongCover(song, coverPath);
    }

    public void updateSongCover(Song song, String coverPath,
                                MusicRepository.CoverUpdateCallback callback) {
        repository.updateSongCover(song, coverPath, callback);
    }

    public androidx.lifecycle.LiveData<com.miku.player.data.model.Song> getSongByIdLive(long id) {
        return repository.getSongByIdLive(id);
    }

    public String getExpectedCoverPath(long songId) {
        return repository.getExpectedCoverPath(songId);
    }

    public void toggleFavorite(Song song) {
        repository.toggleFavorite(song);
    }

    public void setFavorite(Song song, boolean state) {
        repository.setFavorite(song, state);
    }

    public void updateCustomTitle(Song song, String newTitle, Activity activity) {
        repository.updateCustomTitle(song, newTitle, activity,
            new com.miku.player.utils.TagWriter.Result() {
                public void onSuccess() {}
                public void onError(String msg) {
                    android.util.Log.w("SongsVM", "Title tag write failed: " + msg);
                }
            });
    }

    public void deleteSong(Song song, boolean deleteFile,
                           com.miku.player.data.repository.MusicRepository.Callback<Boolean> cb) {
        repository.deleteSong(song, deleteFile, cb);
    }
}
