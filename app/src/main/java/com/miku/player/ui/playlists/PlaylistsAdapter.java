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
                    // Detectar cambio de nombre, cantidad de canciones y portada (custom o primera canción)
                    if (!a.playlist.getName().equals(b.playlist.getName())) return false;
                    if (a.getSongCount() != b.getSongCount()) return false;
                    // Portada custom
                    String coverA = a.playlist.getCoverPath();
                    String coverB = b.playlist.getCoverPath();
                    if (coverA == null ? coverB != null : !coverA.equals(coverB)) return false;
                    // Portada de la primera canción (por si cambió su customCoverPath)
                    String firstA = (a.songs != null && !a.songs.isEmpty())
                            ? a.songs.get(0).getCustomCoverPath() : null;
                    String firstB = (b.songs != null && !b.songs.isEmpty())
                            ? b.songs.get(0).getCustomCoverPath() : null;
                    return firstA == null ? firstB == null : firstA.equals(firstB);
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

            // Portada: custom > portada real de la primera canción > placeholder
            // Se usa resolveCoverSource para obtener el customCoverPath si existe,
            // igual que en la lista de canciones, así los cambios de caratula se reflejan aquí.
            Object coverSource = null;
            String sig = "default";
            if (pws.playlist.getCoverPath() != null) {
                coverSource = android.net.Uri.parse(pws.playlist.getCoverPath());
                sig = pws.playlist.getCoverPath();
            } else if (pws.songs != null && !pws.songs.isEmpty()) {
                com.miku.player.data.model.Song first = pws.songs.get(0);
                coverSource = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(first);
                // La firma incluye customCoverPath para que Glide recargue cuando cambie
                sig = first.getId() + ":" + (first.getCustomCoverPath() != null
                        ? first.getCustomCoverPath() : "");
            }

            Glide.with(b.getRoot().getContext())
                    .load(coverSource)
                    .signature(new com.bumptech.glide.signature.ObjectKey(sig))
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
