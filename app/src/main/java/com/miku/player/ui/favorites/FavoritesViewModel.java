package com.miku.player.ui.favorites;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.miku.player.data.model.Song;
import com.miku.player.data.repository.MusicRepository;
import java.util.List;

public class FavoritesViewModel extends AndroidViewModel {
    private final MusicRepository repository;
    private final LiveData<List<Song>> favorites;

    public FavoritesViewModel(Application app) {
        super(app);
        repository = MusicRepository.getInstance(app);
        favorites = repository.getFavoriteSongs();
    }

    public LiveData<List<Song>> getFavorites() { return favorites; }

    /**
     * Removes a song from favorites. Sets isFavorite=false directly rather
     * than toggling, so the behavior is always predictable regardless of the
     * current in-memory state of the song object.
     */
    public void removeFavorite(Song song) {
        repository.setFavorite(song, false);
    }

    /** Generic toggle — used from SongsActivity/PlayerActivity heart button. */
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
        updateCustomTitle(song, newTitle, activity, true);
    }

    public void updateCustomTitle(Song song, String newTitle, android.app.Activity activity, boolean writeToFile) {
        repository.updateCustomTitle(song, newTitle, activity, writeToFile,
            new com.miku.player.utils.TagWriter.Result() {
                public void onSuccess() {}
                public void onError(String msg) {
                    android.util.Log.w("FavoritesVM", "Title tag write failed: " + msg);
                    android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_LONG).show();
                }
            });
    }

    public void deleteSong(Song song, boolean deleteFile,
                           MusicRepository.Callback<Boolean> cb) {
        repository.deleteSong(song, deleteFile, cb);
    }
}
