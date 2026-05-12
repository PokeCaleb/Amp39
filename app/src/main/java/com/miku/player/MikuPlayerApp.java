package com.miku.player;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;

public class MikuPlayerApp extends Application {

    public static final String CHANNEL_ID = "amp39_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        // Error 11: Force dark mode always
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Amp39", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controles de reproducción de música");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
