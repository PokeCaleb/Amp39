package com.miku.player.data.service;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.miku.player.R;
import java.io.File;
import java.util.concurrent.Executor;

/**
 * Foreground service that holds the MediaSession and ExoPlayer instance.
 *
 * Notification artwork: Media3's DefaultMediaNotificationProvider reads
 * MediaMetadata.artworkUri. When the user sets a custom cover we update
 * the current MediaItem's metadata so the notification reflects it instantly.
 */
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

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
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
