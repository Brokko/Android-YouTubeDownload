package com.github.brokko.youtubedownload.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.github.brokko.youtubedownload.DownloadActivity;
import com.github.brokko.youtubedownload.R;

public class ProgressNotification extends NotificationCompat.Builder {

    private final NotificationManagerCompat notiManagerCompat;

    public ProgressNotification(@NonNull Context context) {
        super(context, DownloadActivity.CHANNEL_ID);

        notiManagerCompat = NotificationManagerCompat.from(context);

        this.setSmallIcon(R.drawable.ic_launcher_foreground);
        this.setAutoCancel(true);
        this.setSubText("Download progress");
        this.setPriority(NotificationManager.IMPORTANCE_DEFAULT);
    }

    private void send() {
        Notification notification = this.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.flags |= Notification.FLAG_NO_CLEAR;

        notiManagerCompat.notify(0, notification);
    }

    public void destroy() {
        notiManagerCompat.cancel(0);
    }

    public void setProgress(int current, String task, int inQueue, boolean indeterminate) {
        this.setContentTitle("In queue: "+inQueue);
        this.setProgress(3, current, indeterminate);
        this.setContentText(task);

        send();
    }

    public void updateQueue(int inQueue) {
        this.setContentTitle("In queue: "+inQueue);

        send();
    }
}