package com.miku.player.ui.playlists;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.databinding.ItemSongBinding;
import com.miku.player.ui.songs.SongsAdapter;
import com.miku.player.utils.CoverSignatureManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for playlist detail — owns a mutable list for drag-and-drop support.
 */
public class PlaylistSongsAdapter extends RecyclerView.Adapter<PlaylistSongsAdapter.ViewHolder> {

    public interface Listener {
        void onSongClick(Song song, int position);
        void onMoreClick(Song song, int position);
        void onFavoriteClick(Song song, boolean newFavoriteState);
        void onDragStart(RecyclerView.ViewHolder holder);
    }

    private final List<Song> songs = new ArrayList<>();
    private final Listener listener;
    private long currentlyPlayingId = -1;
    private boolean showDragHandles = false;

    public PlaylistSongsAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    public void setSongs(List<Song> newSongs) {
        songs.clear();
        if (newSongs != null) songs.addAll(newSongs);
        notifyDataSetChanged();
    }

    public void moveItem(int fromPos, int toPos) {
        if (fromPos < toPos) {
            for (int i = fromPos; i < toPos; i++) Collections.swap(songs, i, i + 1);
        } else {
            for (int i = fromPos; i > toPos; i--) Collections.swap(songs, i, i - 1);
        }
        notifyItemMoved(fromPos, toPos);
    }

    public List<Song> getSongs() { return songs; }

    public void setCurrentlyPlayingId(long newId) {
        long oldId = currentlyPlayingId;
        currentlyPlayingId = newId;
        for (int i = 0; i < songs.size(); i++) {
            long id = songs.get(i).getId();
            if (id == oldId || id == newId) notifyItemChanged(i, "highlight");
        }
    }

    public void setShowDragHandles(boolean show) {
        showDragHandles = show;
        notifyDataSetChanged();
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @Override public int getItemCount() { return songs.size(); }
    @Override public long getItemId(int position) { return songs.get(position).getId(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(songs.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            Song song = songs.get(position);
            for (Object p : payloads) {
                if ("highlight".equals(p)) holder.updateHighlight(song);
                else if ("cover".equals(p)) holder.loadCover(holder.b.getRoot().getContext(), song);
                // "favorite" payload not needed — icon updated directly in click handler
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class ViewHolder extends RecyclerView.ViewHolder {
        final ItemSongBinding b;

        ViewHolder(ItemSongBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Song song) {
            b.tvSongTitle.setText(song.getDisplayTitle());
            b.tvSongArtist.setText(song.getArtist());
            updateHighlight(song);
            loadCover(b.getRoot().getContext(), song);
            updateFavoriteIcon(song.isFavorite());

            b.btnFavorite.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;
                Song s = songs.get(pos);
                boolean newState = !s.isFavorite();
                // Update icon immediately on the mutable list item
                s.setFavorite(newState);
                updateFavoriteIcon(newState);
                listener.onFavoriteClick(s, newState);
            });

            if (showDragHandles) {
                b.ivDragHandle.setVisibility(View.VISIBLE);
                b.ivDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        listener.onDragStart(this);
                        return true;
                    }
                    return false;
                });
            } else {
                b.ivDragHandle.setVisibility(View.GONE);
                b.ivDragHandle.setOnTouchListener(null);
            }

            b.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_ID) listener.onSongClick(songs.get(pos), pos);
            });
            b.btnMore.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_ID) listener.onMoreClick(songs.get(pos), pos);
            });
        }

        void updateHighlight(Song song) {
            Context ctx = b.getRoot().getContext();
            boolean playing = song.getId() == currentlyPlayingId && currentlyPlayingId != -1;
            if (playing) {
                b.tvSongTitle.setTextColor(ctx.getColor(R.color.miku_teal));
                b.tvSongArtist.setTextColor(ctx.getColor(R.color.miku_teal_dark));
                b.getRoot().setBackgroundColor(ctx.getColor(R.color.miku_teal_container));
            } else {
                b.tvSongTitle.setTextColor(ctx.getColor(R.color.md_theme_onSurface));
                b.tvSongArtist.setTextColor(ctx.getColor(R.color.md_theme_onSurfaceVariant));
                b.getRoot().setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }

        void updateFavoriteIcon(boolean isFavorite) {
            b.btnFavorite.setImageResource(
                    isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        }

        void loadCover(Context ctx, Song song) {
            Object src = SongsAdapter.resolveCoverSource(song);
            Glide.with(ctx).load(src)
                    .signature(CoverSignatureManager.getInstance(ctx).signatureFor(song.getId()))
                    .placeholder(R.drawable.ic_default_album_art)
                    .error(R.drawable.ic_default_album_art)
                    .centerCrop()
                    .into(b.ivSongCover);
        }
    }
}
