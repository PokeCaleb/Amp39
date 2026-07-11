package com.miku.player.ui.playlists;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.miku.player.data.model.Playlist;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.data.repository.MusicRepository;
import java.util.List;

public class PlaylistsViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final LiveData<List<PlaylistWithSongs>> playlists;

    public PlaylistsViewModel(Application app) {
        super(app);
        repository = MusicRepository.getInstance(app);
        playlists = repository.getAllPlaylistsWithSongs();
    }

    public LiveData<List<PlaylistWithSongs>> getPlaylists() { return playlists; }

    public void createPlaylist(String name) {
        repository.createPlaylist(name, null);
    }

    public void deletePlaylist(Playlist playlist) {
        repository.deletePlaylist(playlist);
    }

    public void renamePlaylist(Playlist playlist, String newName) {
        repository.renamePlaylist(playlist, newName);
    }

    public void updatePlaylistCover(Playlist playlist, String coverPath) {
        repository.updatePlaylistCover(playlist, coverPath);
    }
}
