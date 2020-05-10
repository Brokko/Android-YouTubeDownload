package com.github.brokko.youtubedownload.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.github.brokko.youtubedownload.DownloadActivity;
import com.github.brokko.youtubedownload.R;

public class ErrorNotification extends NotificationCompat.Builder {

    public static final String ACTION_CLOSE = "ACTION_CLOSE";
    public static final String ACTION_TOUCH = "ACTION_TOUCH";

    private final NotificationManagerCompat notiManagerCompat;

    private final PendingIntent pendingIntentClose;
    private final PendingIntent pendingIntentTouch;

    public ErrorNotification(@NonNull Context context) {
        super(context, DownloadActivity.CHANNEL_ID);

        notiManagerCompat = NotificationManagerCompat.from(context);
        pendingIntentClose = PendingIntent.getBroadcast(context, 1, new Intent(ACTION_CLOSE), 0);
        pendingIntentTouch = PendingIntent.getBroadcast(context, 1, new Intent(ACTION_TOUCH), 0);

        this.setSmallIcon(R.drawable.ic_launcher_foreground);
        this.setDeleteIntent(pendingIntentClose);
        this.setAutoCancel(true);
        this.setSubText("Download");
        this.setContentText("Download Fehler! Wiederholen?");
        this.setContentIntent(pendingIntentTouch);
        this.setColor(Color.BLACK);
        this.setPriority(NotificationManager.IMPORTANCE_DEFAULT);
    }

    public void send() {
        Notification notification = this.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        notiManagerCompat.notify(1, notification);
    }

    public void destroy() {
        notiManagerCompat.cancel(1);
    }
}
