package com.miku.player.ui.playlists;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.data.repository.MusicRepository;
import com.miku.player.ui.songs.SongsAdapter;
import java.util.ArrayList;
import java.util.List;

public class AddSongsToPlaylistBottomSheet extends BottomSheetDialogFragment
        implements SongsAdapter.OnSongClickListener {

    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private long playlistId;

    public static AddSongsToPlaylistBottomSheet newInstance(long playlistId) {
        AddSongsToPlaylistBottomSheet sheet = new AddSongsToPlaylistBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlistId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        playlistId = getArguments() != null ? getArguments().getLong(ARG_PLAYLIST_ID) : -1;

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 16, 0, 32);

        SongsAdapter adapter = new SongsAdapter(this);
        rv.setAdapter(adapter);

        MusicRepository.getInstance(requireContext())
                .getAllSongs()
                .observe(getViewLifecycleOwner(), adapter::submitList);

        return rv;
    }

    @Override
    public void onSongClick(Song song, int position) {
        if (playlistId == -1) return;
        MusicRepository.getInstance(requireContext()).addSongToPlaylist(playlistId, song);
        Toast.makeText(requireContext(),
                "\"" + song.getTitle() + "\" agregada", Toast.LENGTH_SHORT).show();
        dismiss();
    }

    @Override
    public void onMoreClick(Song song, int position) { /* no-op */ }
}
