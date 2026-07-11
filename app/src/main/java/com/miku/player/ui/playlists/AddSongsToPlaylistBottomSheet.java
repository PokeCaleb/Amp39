package com.miku.player.ui.playlists;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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
    private SongsAdapter adapter;
    private List<Song> allSongs = new ArrayList<>();

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

        // Contenedor vertical: buscador encima + lista debajo
        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int dp16 = dp(16);

        // ── Buscador ──────────────────────────────────────────────────────────
        TextInputLayout searchLayout = new TextInputLayout(requireContext(),
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        searchLayout.setHint("Buscar canción…");
        searchLayout.setStartIconDrawable(R.drawable.ic_search);
        searchLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        android.widget.LinearLayout.LayoutParams searchParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dp16, dp16, dp16, dp(8));
        searchLayout.setLayoutParams(searchParams);

        TextInputEditText searchEdit = new TextInputEditText(searchLayout.getContext());
        searchLayout.addView(searchEdit);
        root.addView(searchLayout);

        // Botón "X": limpia el texto (vuelve al hint "Buscar canción…"), quita el foco
        // y oculta el teclado.
        searchLayout.setEndIconOnClickListener(v -> {
            searchEdit.setText("");
            searchEdit.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
        });

        // ── Lista de canciones ────────────────────────────────────────────────
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 0, 0, dp(32));
        rv.setClipToPadding(false);
        android.widget.LinearLayout.LayoutParams rvParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvParams);

        adapter = new SongsAdapter(this);
        rv.setAdapter(adapter);
        root.addView(rv);

        // ── Datos ─────────────────────────────────────────────────────────────
        MusicRepository.getInstance(requireContext())
                .getAllSongs()
                .observe(getViewLifecycleOwner(), songs -> {
                    allSongs = songs != null ? songs : new ArrayList<>();
                    // Aplicar el filtro actual si ya hay texto escrito
                    String q = searchEdit.getText() != null
                            ? searchEdit.getText().toString().trim() : "";
                    filter(q);
                });

        // ── Filtrado en tiempo real ───────────────────────────────────────────
        searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                filter(s != null ? s.toString().trim() : "");
            }
        });

        return root;
    }

    private void filter(String query) {
        if (adapter == null) return;
        if (query.isEmpty()) {
            adapter.submitList(new ArrayList<>(allSongs));
            return;
        }
        String lower = query.toLowerCase();
        List<Song> filtered = new ArrayList<>();
        for (Song s : allSongs) {
            String title = s.getDisplayTitle() != null ? s.getDisplayTitle().toLowerCase() : "";
            String artist = s.getArtist() != null ? s.getArtist().toLowerCase() : "";
            if (title.contains(lower) || artist.contains(lower)) filtered.add(s);
        }
        adapter.submitList(filtered);
    }

    private int dp(int dp) {
        return (int)(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onSongClick(Song song, int position) {
        if (playlistId == -1) return;
        MusicRepository.getInstance(requireContext()).addSongToPlaylist(playlistId, song);
        Toast.makeText(requireContext(),
                "\"" + song.getDisplayTitle() + "\" agregada", Toast.LENGTH_SHORT).show();
        dismiss();
    }

    @Override
    public void onMoreClick(Song song, int position) { /* no-op */ }
}
