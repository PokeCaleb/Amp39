package com.miku.player.ui.playlists;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.miku.player.R;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.databinding.ItemPlaylistBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FIX: antes era un ListAdapter con DiffUtil. Se cambió a una lista mutable propia
 * (mismo patrón que PlaylistSongsAdapter) porque el arrastrar-y-soltar necesita
 * reordenar visualmente en tiempo real durante el gesto (notifyItemMoved), y eso
 * no combina bien con ListAdapter — su lista interna solo se actualiza vía
 * submitList(), así que un notifyItemMoved manual la desincroniza.
 */
public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistWithSongs playlistWithSongs);
        void onMoreClick(PlaylistWithSongs playlistWithSongs);
    }

    private final OnPlaylistClickListener listener;
    private final List<PlaylistWithSongs> items = new ArrayList<>();

    public PlaylistsAdapter(OnPlaylistClickListener listener) {
        this.listener = listener;
    }

    /** Reemplaza la lista completa (llamar desde el observer de LiveData). */
    public void setItems(List<PlaylistWithSongs> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<PlaylistWithSongs> getItems() { return items; }

    /**
     * Resuelve la fuente de portada de una playlist para Glide, y calcula una firma
     * de caché que SÍ cambia cuando el archivo cambia.
     *
     * FIX: antes se hacía Uri.parse(coverPath) siempre, pero desde que las portadas
     * de playlist se copian a un archivo local (ruta absoluta tipo "/data/.../covers/
     * playlist_cover_5.jpg", sin esquema), Uri.parse() no lo resuelve correctamente
     * como Glide espera — hay que envolverlo en un File. Y como el archivo se
     * sobreescribe con el MISMO nombre cada vez que cambias la portada, usar la
     * ruta como firma de Glide nunca detectaba el cambio (Glide seguía mostrando el
     * bitmap cacheado viejo). Ahora la firma incluye el lastModified() real del
     * archivo, que sí cambia en cada sobreescritura.
     */
    static Object[] resolvePlaylistCoverSourceAndSignature(String coverPath) {
        if (coverPath == null) return new Object[]{null, "none"};
        if (coverPath.startsWith("/")) {
            java.io.File f = new java.io.File(coverPath);
            if (f.exists()) return new Object[]{f, coverPath + ":" + f.lastModified()};
            return new Object[]{null, "missing"};
        }
        // Compatibilidad con datos viejos (URIs content:// de antes de este fix)
        return new Object[]{android.net.Uri.parse(coverPath), coverPath};
    }

    /** Mueve un ítem durante el arrastre — mutación en vivo + notificación de movimiento. */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0
                || fromPosition >= items.size() || toPosition >= items.size()) return;
        Collections.swap(items, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public int getItemCount() { return items.size(); }

    private PlaylistWithSongs getItem(int position) { return items.get(position); }

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
                Object[] resolved = resolvePlaylistCoverSourceAndSignature(pws.playlist.getCoverPath());
                coverSource = resolved[0];
                sig = (String) resolved[1];
            } else if (pws.songs != null && !pws.songs.isEmpty()) {
                // FIX: usar la primera canción REAL según su posición en la lista
                // (ver PlaylistWithSongs#getFirstSongByPosition) — antes se usaba
                // songs.get(0), que no tenía garantía de reflejar el orden real,
                // así que la portada automática podía no actualizarse al reordenar.
                com.miku.player.data.model.Song first = pws.getFirstSongByPosition();
                coverSource = com.miku.player.ui.songs.SongsAdapter.resolveCoverSource(first);
                // La firma incluye el id de la canción (cambia si el orden cambia
                // la "primera" canción) y su customCoverPath (cambia si a esa
                // canción le cambian la carátula), para que Glide recargue en
                // cualquiera de esos dos casos.
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
