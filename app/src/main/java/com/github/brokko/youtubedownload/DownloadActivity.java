package com.github.brokko.youtubedownload;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class DownloadActivity extends Activity {
    public static final String CHANNEL_ID = "Youtube Download";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();

        // https://developer.android.com/training/notify-user/build-notification#Priority
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress");
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        if (ContextCompat.checkSelfPermission(this.getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        } else {
            deliverToService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            deliverToService();
    }

    private void deliverToService() {
        if (Intent.ACTION_SEND.equals(getIntent().getAction()) && getIntent().getType().equals("text/plain")) {
            String link = getIntent().getStringExtra(Intent.EXTRA_TEXT);

            if (link != null && link.contains("://youtu.be/") || link.contains("youtube.com/watch?v=")) {
                Intent intent = new Intent(this, BackgroundService.class);
                intent.putExtra("URL", link);
                startService(intent);

            } else {
                Toast.makeText(this, R.string.wrong_intent, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.wrong_intent, Toast.LENGTH_LONG).show();
        }
    }
}