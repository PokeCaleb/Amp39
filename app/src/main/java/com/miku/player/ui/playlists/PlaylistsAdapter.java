package com.miku.player.ui.playlists;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.miku.player.R;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.databinding.ItemPlaylistBinding;

public class PlaylistsAdapter extends ListAdapter<PlaylistWithSongs, PlaylistsAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistWithSongs playlistWithSongs);
        void onMoreClick(PlaylistWithSongs playlistWithSongs);
    }

    private final OnPlaylistClickListener listener;

    public PlaylistsAdapter(OnPlaylistClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<PlaylistWithSongs> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<PlaylistWithSongs>() {
                @Override
                public boolean areItemsTheSame(@NonNull PlaylistWithSongs a, @NonNull PlaylistWithSongs b) {
                    return a.playlist.getId() == b.playlist.getId();
                }
                @Override
                public boolean areContentsTheSame(@NonNull PlaylistWithSongs a, @NonNull PlaylistWithSongs b) {
                    return a.playlist.getName().equals(b.playlist.getName())
                            && a.getSongCount() == b.getSongCount();
                }
            };

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistBinding binding = ItemPlaylistBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlaylistBinding b;

        PlaylistViewHolder(ItemPlaylistBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(PlaylistWithSongs pws) {
            b.tvPlaylistName.setText(pws.playlist.getName());

            int count = pws.getSongCount();
            b.tvSongCount.setText(count == 1 ? "1 canción" : count + " canciones");

            // Load cover: custom > first song embedded art > placeholder
            Object coverSource = null;
            if (pws.playlist.getCoverPath() != null) {
                coverSource = android.net.Uri.parse(pws.playlist.getCoverPath());
            } else if (pws.songs != null && !pws.songs.isEmpty()) {
                long songId = pws.songs.get(0).getId();
                coverSource = android.net.Uri.parse(
                        "content://media/external/audio/albumart/" + songId);
            }

            Glide.with(b.getRoot().getContext())
                    .load(coverSource)
                    .placeholder(R.drawable.ic_default_album_art)
                    .error(R.drawable.ic_default_album_art)
                    .centerCrop()
                    .into(b.ivPlaylistCover);

            b.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onPlaylistClick(pws);
            });
            b.btnMore.setOnClickListener(v -> {
                if (listener != null) listener.onMoreClick(pws);
            });
        }
    }
}
