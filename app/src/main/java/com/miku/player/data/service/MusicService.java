package com.miku.player.data.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.CommandButton;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
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

    /**
     * Apaga la app por completo: detiene la música, quita la notificación y cierra el proceso.
     * Se puede pedir con un Intent a este servicio (botón de la app) o con el botón "Apagar"
     * de la notificación (comando personalizado de la sesión).
     */
    public static final String ACTION_SHUTDOWN = "com.miku.player.action.SHUTDOWN";
    private static final SessionCommand SHUTDOWN_COMMAND =
            new SessionCommand(ACTION_SHUTDOWN, Bundle.EMPTY);

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

        // Botón "Apagar" en la notificación: es la forma de apagar la app cuando ya no está abierta.
        CommandButton shutdownButton = new CommandButton.Builder()
                .setDisplayName(getString(R.string.shutdown_action))
                .setIconResId(R.drawable.ic_power)
                .setSessionCommand(SHUTDOWN_COMMAND)
                .build();

        mediaSession = new MediaSession.Builder(this, player)
                .setId("MikuPlayerSession")
                .setCustomLayout(ImmutableList.of(shutdownButton))
                .setCallback(new MediaSession.Callback() {
                    /**
                     * El servicio está exportado (el sistema necesita controlar la sesión),
                     * así que cada controlador se valida: solo esta app y controladores de
                     * confianza del sistema (notificación, bloqueo, Bluetooth, etc.).
                     * Cualquier otra app es rechazada.
                     */
                    @Override
                    public MediaSession.ConnectionResult onConnect(
                            MediaSession session, MediaSession.ControllerInfo controller) {
                        if (MusicService.this.getPackageName().equals(controller.getPackageName())
                                || controller.isTrusted()) {
                            // Se habilita además el comando personalizado "Apagar".
                            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                    .setAvailableSessionCommands(
                                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                                    .buildUpon().add(SHUTDOWN_COMMAND).build())
                                    .build();
                        }
                        return MediaSession.ConnectionResult.reject();
                    }

                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(
                            MediaSession session, MediaSession.ControllerInfo controller,
                            SessionCommand customCommand, Bundle args) {
                        if (ACTION_SHUTDOWN.equals(customCommand.customAction)) {
                            shutdown();
                            return Futures.immediateFuture(
                                    new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        return Futures.immediateFuture(
                                new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
                    }
                })
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
        if (intent != null && ACTION_SHUTDOWN.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    private void shutdown() {
        try {
            player.stop();
            player.clearMediaItems();
        } catch (Exception ignored) {}
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        stopSelf();
        // Cierra el proceso un instante después (ya sin notificación ni reproducción), para
        // que la app quede realmente cerrada, incluidas las pantallas que siguieran abiertas.
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> Process.killProcess(Process.myPid()), 500);
    }
}
