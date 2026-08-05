package com.miku.player.data.service;

import android.content.Intent;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.miku.player.R;

/**
 * Foreground service that holds the MediaSession and ExoPlayer instance.
 *
 * Notification artwork: Media3's DefaultMediaNotificationProvider reads
 * MediaMetadata.artworkUri. When the user sets a custom cover we update
 * the current MediaItem's metadata so the notification reflects it instantly.
 */
@UnstableApi
public class MusicService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        // FIX: los archivos .opus (contenedor Ogg) no traen una tabla de búsqueda
        // indexada como MP3/M4A — por defecto, ExoPlayer calcula su duración y
        // puntos de búsqueda mediante "bisección interpolada" (escaneando el
        // archivo), lo cual es notablemente más lento para preparar la pista que
        // formatos con índice. setConstantBitrateSeekingEnabled(true) habilita una
        // búsqueda aproximada asumiendo bitrate constante para los extractores que
        // lo soportan (incluido OggExtractor) — no viene activado por defecto.
        // La mayoría de los .opus están codificados a un bitrate objetivo estable,
        // así que esto debería acelerar notablemente el inicio de esas canciones
        // sin afectar a los demás formatos.
        androidx.media3.extractor.DefaultExtractorsFactory extractorsFactory =
                new androidx.media3.extractor.DefaultExtractorsFactory()
                        .setConstantBitrateSeekingEnabled(true);

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        this, extractorsFactory))
                .build();

        mediaSession = new MediaSession.Builder(this, player)
                .setId("MikuPlayerSession")
                .build();

        // Use Media3's default provider — it reads artworkUri from MediaMetadata
        // and displays it as the notification large icon automatically.
        DefaultMediaNotificationProvider notifProvider =
                new DefaultMediaNotificationProvider.Builder(this)
                        .setNotificationId(1001)
                        .setChannelId("miku_player_channel")
                        .setChannelName(R.string.app_name)
                        .build();
        notifProvider.setSmallIcon(R.drawable.ic_music_note);
        setMediaNotificationProvider(notifProvider);
    }

    @Override
    public void onDestroy() {
        mediaSession.release();
        player.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }
}
