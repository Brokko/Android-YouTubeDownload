package com.github.brokko.youtubedownload.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.github.brokko.youtubedownload.DownloadActivity;
import com.github.brokko.youtubedownload.R;

public class ProgressNotification extends NotificationCompat.Builder {

    public static final String ACTION_CLOSE = "ACTION_CLOSE";
    public static final String ACTION_TOUCH = "ACTION_TOUCH";

    private final NotificationManagerCompat notiManagerCompat;
    private final PendingIntent pendingIntentClose;
    private final PendingIntent pendingIntentTouch;

    public ProgressNotification(@NonNull Context context) {
        super(context, DownloadActivity.CHANNEL_ID);

        notiManagerCompat = NotificationManagerCompat.from(context);
        pendingIntentClose = PendingIntent.getBroadcast(context, 1, new Intent(ACTION_CLOSE), 0);
        pendingIntentTouch = PendingIntent.getBroadcast(context, 1, new Intent(ACTION_TOUCH), 0);

        this.setAutoCancel(false);
        this.setSmallIcon(R.drawable.ic_launcher_foreground);
        this.setPriority(NotificationManager.IMPORTANCE_DEFAULT);
        this.setSubText("Download progress");
        this.setDeleteIntent(pendingIntentClose);
    }

    public void error() {
        this.setContentText("Download Fehler! Wiederholen?");
        this.setProgress(0, 0, false);
        this.setContentIntent(pendingIntentTouch);
        this.setContentTitle(null);

        notiManagerCompat.notify(2, this.build());
    }

    public void updateProgress(int current, String task, int inQueue, boolean indeterminate) {
        this.setProgress(3, current, indeterminate);
        this.setContentTitle("In queue: "+inQueue);
        this.setContentIntent(null);
        this.setContentText(task);

        notiManagerCompat.notify(2, this.build());
    }

    public void updateQueue(int inQueue) {
        this.setContentTitle("In queue: "+inQueue);

        notiManagerCompat.notify(2, this.build());
    }
}