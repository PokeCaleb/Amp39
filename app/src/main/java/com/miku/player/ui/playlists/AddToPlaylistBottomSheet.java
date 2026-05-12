package com.miku.player.ui.playlists;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.miku.player.R;
import com.miku.player.data.model.PlaylistWithSongs;
import com.miku.player.data.model.Song;
import com.miku.player.data.repository.MusicRepository;

public class AddToPlaylistBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_SONG_ID     = "song_id";
    private static final String ARG_SONG_TITLE  = "song_title";
    private static final String ARG_SONG_ARTIST = "song_artist";
    private static final String ARG_SONG_ALBUM  = "song_album";
    private static final String ARG_SONG_DUR    = "song_dur";
    private static final String ARG_SONG_PATH   = "song_path";

    /** Pass the full Song so the repository can guarantee it exists in songs table. */
    public static AddToPlaylistBottomSheet newInstance(Song song) {
        AddToPlaylistBottomSheet s = new AddToPlaylistBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_SONG_ID,     song.getId());
        args.putString(ARG_SONG_TITLE,  song.getTitle());
        args.putString(ARG_SONG_ARTIST, song.getArtist());
        args.putString(ARG_SONG_ALBUM,  song.getAlbum());
        args.putLong(ARG_SONG_DUR,    song.getDuration());
        args.putString(ARG_SONG_PATH,   song.getPath());
        s.setArguments(args);
        return s;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        Bundle args = getArguments();
        Song song = new Song(
                args != null ? args.getLong(ARG_SONG_ID) : -1,
                args != null ? args.getString(ARG_SONG_TITLE, "") : "",
                args != null ? args.getString(ARG_SONG_ARTIST, "") : "",
                args != null ? args.getString(ARG_SONG_ALBUM, "") : "",
                args != null ? args.getLong(ARG_SONG_DUR) : 0,
                args != null ? args.getString(ARG_SONG_PATH, "") : "",
                0
        );

        MusicRepository repo = MusicRepository.getInstance(requireContext());

        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(0, 24, 0, 32);

        com.google.android.material.textview.MaterialTextView title =
                new com.google.android.material.textview.MaterialTextView(requireContext());
        title.setText("Agregar a lista de reproducción");
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        title.setPadding(64, 16, 64, 8);
        root.addView(title);

        MaterialButton btnNew = new MaterialButton(requireContext());
        btnNew.setText("+ Crear nueva lista");
        btnNew.setIconResource(R.drawable.ic_add);
        final Song finalSong = song;
        btnNew.setOnClickListener(v -> showCreateDialog(repo, finalSong));
        android.widget.LinearLayout.LayoutParams btnParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(48, 0, 48, 8);
        btnNew.setLayoutParams(btnParams);
        root.addView(btnNew);

        View divider = new View(requireContext());
        divider.setBackgroundColor(0x20000000);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(divider);

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        root.addView(rv);

        repo.getAllPlaylistsWithSongs().observe(getViewLifecycleOwner(), playlists -> {
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                    com.google.android.material.textview.MaterialTextView tv =
                            new com.google.android.material.textview.MaterialTextView(p.getContext());
                    tv.setPadding(64, 36, 64, 36);
                    tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                    return new RecyclerView.ViewHolder(tv) {};
                }
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                    PlaylistWithSongs pws = playlists.get(pos);
                    ((com.google.android.material.textview.MaterialTextView) h.itemView)
                            .setText(pws.playlist.getName() + "  (" + pws.getSongCount() + ")");
                    h.itemView.setOnClickListener(v2 -> {
                        repo.addSongToPlaylist(pws.playlist.getId(), finalSong);
                        Toast.makeText(requireContext(), getString(R.string.song_added), Toast.LENGTH_SHORT).show();
                        dismiss();
                    });
                }
                public int getItemCount() { return playlists != null ? playlists.size() : 0; }
            });
        });

        return root;
    }

    private void showCreateDialog(MusicRepository repo, Song song) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint("Nombre de la nueva lista");
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        EditText et = new TextInputEditText(layout.getContext());
        layout.addView(et);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad/2, pad, 0);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Crear lista de reproducción")
                .setView(layout)
                .setPositiveButton("Crear", (d, w) -> {
                    String name = et.getText() != null ? et.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    repo.createPlaylist(name, playlistId -> {
                        repo.addSongToPlaylist(playlistId, song);
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), getString(R.string.playlist_created), Toast.LENGTH_SHORT).show());
                    });
                    dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
