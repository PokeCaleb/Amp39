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

    /** Invalida el caché de portada de una canción puntual y refresca solo esa fila. */
    public void invalidateCover(Context ctx, long songId) {
        CoverSignatureManager.getInstance(ctx).invalidateSong(songId);
        notifyCoverChanged(songId);
    }

    /** Solo refresca la fila en pantalla — ver nota equivalente en SongsAdapter. */
    public void notifyCoverChanged(long songId) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == songId) {
                notifyItemChanged(i, "cover");
                break;
            }
        }
    }

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
        // Payload "drag" evita recargar portadas al activar/desactivar el reorden
        int size = songs.size();
        if (size > 0) notifyItemRangeChanged(0, size, "drag");
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @Override public int getItemCount() { return songs.size(); }
    @Override public long getItemId(int position) { return songs.get(position).getId(); }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.b.ivSongCover.setTag(null);
        Glide.with(holder.b.getRoot().getContext()).clear(holder.b.ivSongCover);
    }

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
                else if ("drag".equals(p)) holder.updateDragHandle(showDragHandles);
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
            updateDragHandle(showDragHandles);
            updateFavoriteIcon(song.isFavorite());

            // Solo recargar portada si cambió el path respecto al último bind
            String newPath = song.getCustomCoverPath();
            if (!java.util.Objects.equals(newPath, (String) b.ivSongCover.getTag())) {
                b.ivSongCover.setTag(newPath);
                loadCover(b.getRoot().getContext(), song);
            }

            b.btnFavorite.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Song s = songs.get(pos);
                boolean newState = !s.isFavorite();
                // Update icon immediately on the mutable list item
                s.setFavorite(newState);
                updateFavoriteIcon(newState);
                listener.onFavoriteClick(s, newState);
            });

            b.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onSongClick(songs.get(pos), pos);
            });
            b.btnMore.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onMoreClick(songs.get(pos), pos);
            });
        }

        /**
         * Muestra/oculta el handle de arrastre Y (re)asigna su OnTouchListener.
         * IMPORTANTE: este método se llama tanto desde bind() (bind completo)
         * como desde el payload "drag" (cuando se activa/desactiva el modo
         * reordenar sobre una vista ya reciclada). Antes el listener solo se
         * asignaba en bind(), así que al activar el modo reordenar sobre filas
         * ya vinculadas, el handle se volvía visible pero sin listener — de
         * ahí que el arrastre no funcionara.
         */
        void updateDragHandle(boolean show) {
            if (b.ivDragHandle == null) return;
            b.ivDragHandle.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                b.ivDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        listener.onDragStart(this);
                        return true;
                    }
                    return false;
                });
            } else {
                b.ivDragHandle.setOnTouchListener(null);
            }
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
            Glide.with(ctx).clear(b.ivSongCover);
            b.ivSongCover.setImageResource(R.drawable.ic_default_album_art);
            Object src = SongsAdapter.resolveCoverSource(song);
            Glide.with(ctx).load(src)
                    .signature(CoverSignatureManager.getInstance(ctx).signatureFor(song.getId()))
                    .placeholder(R.drawable.ic_default_album_art)
                    .error(R.drawable.ic_default_album_art)
                    .centerCrop()
                    .dontAnimate()
                    .into(b.ivSongCover);
        }
    }
}
