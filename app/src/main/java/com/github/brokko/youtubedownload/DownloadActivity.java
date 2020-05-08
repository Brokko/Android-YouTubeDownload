package com.github.brokko.youtubedownload;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.brokko.youtubedownload.notification.ErrorNotification;
import com.github.brokko.youtubedownload.notification.ProgressNotification;

import java.io.File;
import java.util.ArrayList;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;

public class DownloadActivity extends Activity {
    public static final String CHANNEL_ID = "Youtube Download";

    private final ArrayList<String> downloads = new ArrayList<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(intent.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID));
                    Cursor c = manager.query(q);

                    if (c.moveToFirst()) {
                        if (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                            progressNotification.setProgress(3, "Notify MediaStore", downloads.size(), false);

                            File file = new File(Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))).getPath());
                            c.close();

                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
                            downloads.remove(0);
                        } else {
                            progressNotification.destroy();
                            errorNotification.send();
                        }
                    }

                    if (downloads.size() != 0) {
                        download(downloads.get(0));
                    } else {
                        progressNotification.destroy();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                    break;
                case ErrorNotification.ACTION_TOUCH:
                    errorNotification.destroy();
                    download(downloads.get(0));
                    break;
                case ErrorNotification.ACTION_CLOSE:
                    android.os.Process.killProcess(android.os.Process.myPid());
                    break;
            }
        }
    };

    private DownloadManager manager;
    private ProgressNotification progressNotification;
    private ErrorNotification errorNotification;

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
                downloads.add(link);

                if (downloads.size() == 1) {
                    manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    progressNotification = new ProgressNotification(this);
                    errorNotification = new ErrorNotification(DownloadActivity.this);
                    errorNotification.getIntentFilter().addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

                    download(link);
                }
            } else {
                Toast.makeText(this, R.string.wrong_intent, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.wrong_intent, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void download(String link) {
        progressNotification.setProgress(0, "Fetch stream", downloads.size(), false);

        new YouTubeExtractor(this) {
            @Override
            protected void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                progressNotification.setProgress(1, "Prepare download", downloads.size(), false);
                registerReceiver(receiver, errorNotification.getIntentFilter());

                if (ytFiles != null) {
                    YtFile ytFile = null;
                    String videoTitle = vMeta.getTitle();

                    if (ytFiles.get(258) != null) {
                        ytFile = ytFiles.get(258);
                    } else if (ytFiles.get(141) != null) {
                        ytFile = ytFiles.get(141);
                    } else if (ytFiles.get(256) != null) {
                        ytFile = ytFiles.get(256);
                    } else if (ytFiles.get(140) != null) {
                        ytFile = ytFiles.get(140);
                    }

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(ytFile.getUrl()));
                    request.setTitle(videoTitle);
                    request.allowScanningByMediaScanner();
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, videoTitle + ".m4a");
                    manager.enqueue(request);

                    progressNotification.setProgress(2, "Download", downloads.size(), true);
                } else {
                    progressNotification.destroy();
                    errorNotification.send();
                }
            }
        }.extract(link, true, true);
    }
}