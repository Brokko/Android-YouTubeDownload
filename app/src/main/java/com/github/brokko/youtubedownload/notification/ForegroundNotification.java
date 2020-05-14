package com.github.brokko.youtubedownload.notification;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.github.brokko.youtubedownload.DownloadActivity;
import com.github.brokko.youtubedownload.R;

public class ForegroundNotification extends NotificationCompat.Builder {

    public ForegroundNotification(@NonNull Context context) {
        super(context, DownloadActivity.CHANNEL_ID);

        this.setSmallIcon(R.drawable.ic_launcher_foreground);
        this.setAutoCancel(true);
        this.setOngoing(true);
        this.setColor(Color.BLACK);
        this.setSubText("Download service running");
        this.setPriority(NotificationManager.IMPORTANCE_LOW);

    }
}
