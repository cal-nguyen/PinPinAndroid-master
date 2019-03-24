package com.example.android.pinpin;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class NotificationSysInit extends Application {
    public static final String CHANNEL_1 = "nearbyPin";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel1 = new NotificationChannel(
                    CHANNEL_1,
                    "Nearby Pin",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel1.setDescription("Pins are nearby");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel1);
            }
        }
    }

}
