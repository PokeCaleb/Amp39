package com.miku.player.ui.songs;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.miku.player.R;
import com.miku.player.data.model.Song;
import com.miku.player.databinding.ItemSongBinding;
import com.miku.player.utils.CoverSignatureManager;
import java.io.File;
import java.util.List;

public class SongsAdapter extends ListAdapter<Song, SongsAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
        void onMoreClick(Song song, int position);
    }

    public interface OnFavoriteClickListener {
        // isFavorite is the NEW desired state (already determined by the adapter)
        void onFavoriteClick(Song song, boolean newFavoriteState);
    }

    public interface OnDragStartListener {
        void onDragStart(RecyclerView.ViewHolder holder);
    }

    private final OnSongClickListener listener;
    private OnFavoriteClickListener favoriteListener;
    private OnDragStartListener dragStartListener;
    private long currentlyPlayingId = -1;
    private boolean showDragHandles = false;

    public SongsAdapter(OnSongClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setFavoriteListener(OnFavoriteClickListener l) { this.favoriteListener = l; }
    public void setDragStartListener(OnDragStartListener l) { this.dragStartListener = l; }

    public void setShowDragHandles(boolean show) {
        this.showDragHandles = show;
        notifyDataSetChanged();
    }

    public void setCurrentlyPlayingId(long newId) {
        long oldId = currentlyPlayingId;
        currentlyPlayingId = newId;
        List<Song> list = getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            long itemId = list.get(i).getId();
            if (itemId == oldId || itemId == newId) notifyItemChanged(i, "highlight");
        }
    }

    /** Forces a cache-busted Glide reload for this song in the list. */
    public void invalidateCover(Context ctx, long songId) {
        CoverSignatureManager.getInstance(ctx).invalidateSong(songId);
        List<Song> list = getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == songId) {
                notifyItemChanged(i, "cover");
                break;
            }
        }
    }

    private static final DiffUtil.ItemCallback<Song> DIFF = new DiffUtil.ItemCallback<Song>() {
        @Override
        public boolean areItemsTheSame(@NonNull Song a, @NonNull Song b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song a, @NonNull Song b) {
            boolean coversMatch = (a.getCustomCoverPath() == null && b.getCustomCoverPath() == null)
                    || (a.getCustomCoverPath() != null
                    && a.getCustomCoverPath().equals(b.getCustomCoverPath()));
            boolean titleMatch = a.getDisplayTitle().equals(b.getDisplayTitle());
            return titleMatch
                    && a.getArtist().equals(b.getArtist())
                    && coversMatch
                    && a.isFavorite() == b.isFavorite();
        }
    };

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SongViewHolder(ItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(getItem(position), currentlyPlayingId);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            Song song = getItem(position);
            for (Object payload : payloads) {
                if ("highlight".equals(payload)) holder.updateHighlight(song, currentlyPlayingId);
                else if ("cover".equals(payload))
                    holder.loadCover(holder.b.getRoot().getContext(), song);
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        final ItemSongBinding b;

        SongViewHolder(ItemSongBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Song song, long playingId) {
            b.tvSongTitle.setText(song.getDisplayTitle());
            b.tvSongArtist.setText(song.getArtist());
            updateHighlight(song, playingId);
            loadCover(b.getRoot().getContext(), song);
            updateFavoriteIcon(song.isFavorite());

            b.btnFavorite.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;
                Song s = getItem(pos);

                // Determine the new state we want
                boolean newState = !s.isFavorite();

                // Update icon immediately without touching the immutable list
                updateFavoriteIcon(newState);

                // Tell the listener the new desired state
                if (favoriteListener != null) favoriteListener.onFavoriteClick(s, newState);
            });

            if (showDragHandles) {
                b.ivDragHandle.setVisibility(View.VISIBLE);
                b.ivDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (dragStartListener != null) dragStartListener.onDragStart(this);
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
                if (listener != null && pos != RecyclerView.NO_ID)
                    listener.onSongClick(song, pos);
            });
            b.btnMore.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (listener != null && pos != RecyclerView.NO_ID)
                    listener.onMoreClick(song, pos);
            });
        }

        void updateHighlight(Song song, long playingId) {
            Context ctx = b.getRoot().getContext();
            boolean playing = song.getId() == playingId && playingId != -1;
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
            Object src = resolveCoverSource(song);
            Glide.with(ctx)
                    .load(src)
                    .signature(CoverSignatureManager.getInstance(ctx).signatureFor(song.getId()))
                    .placeholder(R.drawable.ic_default_album_art)
                    .error(R.drawable.ic_default_album_art)
                    .centerCrop()
                    .into(b.ivSongCover);
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    public static Object resolveCoverSource(Song song) {
        String cp = song.getCustomCoverPath();
        if (cp != null && !cp.isEmpty()) {
            if (cp.startsWith("/")) {
                File f = new File(cp);
                if (f.exists()) return f;
            }
            return Uri.parse(cp);
        }
        return Uri.parse("content://media/external/audio/albumart/" + song.getId());
    }
}
