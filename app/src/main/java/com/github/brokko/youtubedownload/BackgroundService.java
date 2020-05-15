package com.github.brokko.youtubedownload;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.util.SparseArray;

import androidx.core.app.NotificationManagerCompat;

import com.github.brokko.youtubedownload.notification.ForegroundNotification;
import com.github.brokko.youtubedownload.notification.ProgressNotification;

import java.io.File;
import java.util.ArrayList;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;

public class BackgroundService extends Service {

    private final ArrayList<String> downloadQueue = new ArrayList<>();
    private final IntentFilter intentFilter = new IntentFilter();

    private DownloadManager manager;
    private ProgressNotification progressNoti;

    private String title;
    private String artist;
    private String imgURL;
    private long currentDownload;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                    progressNoti.updateProgress(3, "Write metadata...", downloadQueue.size(), false);

                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(currentDownload);
                    Cursor c = manager.query(query);

                    if (c.moveToFirst()) {
                        if (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {

                            File file = new File(Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))).getPath());
                            c.close();

                            new MetaDataInsert(() -> {
                                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
                                downloadQueue.remove(0);

                                if (downloadQueue.size() != 0) { download(); } else { stopSelf(); }
                            }, file, title, artist, imgURL).start();

                        } else {
                            progressNoti.error();
                        }
                    } else {
                        progressNoti.error();
                    }

                    break;
                case ProgressNotification.ACTION_CLOSE:
                    stopSelf();
                    break;
                case ProgressNotification.ACTION_TOUCH:
                    download();
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        progressNoti = new ProgressNotification(this);

        intentFilter.addAction(ProgressNotification.ACTION_TOUCH);
        intentFilter.addAction(ProgressNotification.ACTION_CLOSE);
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

        startForeground(1, new ForegroundNotification(this).build());
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        downloadQueue.add(intent.getStringExtra("URL"));
        progressNoti.updateQueue(downloadQueue.size());

        if (downloadQueue.size() == 1)
            download();

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        downloadQueue.clear();

        unregisterReceiver(receiver);
        stopForeground(true);
        NotificationManagerCompat.from(this).cancel(2);
    }

    @SuppressLint("StaticFieldLeak")
    private void download() {
        progressNoti.updateProgress(0, "Fetch stream", downloadQueue.size(), false);

        new YouTubeExtractor(this) {
            @Override
            protected void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                progressNoti.updateProgress(1, "Prepare download", downloadQueue.size(), false);

                if (ytFiles == null) {
                    progressNoti.error();
                    return;
                }

                YtFile ytFile = null;
                if (ytFiles.get(258) != null) {
                    ytFile = ytFiles.get(258);
                } else if (ytFiles.get(141) != null) {
                    ytFile = ytFiles.get(141);
                } else if (ytFiles.get(256) != null) {
                    ytFile = ytFiles.get(256);
                } else if (ytFiles.get(140) != null) {
                    ytFile = ytFiles.get(140);
                }

                String videoTitle = vMeta.getTitle().replaceAll("\"", "");
                videoTitle = videoTitle.replace("(Lyrics)", "");
                videoTitle = videoTitle.replace("[Official Video]", "");
                videoTitle = videoTitle.replace("(Official Video)", "");
                videoTitle = videoTitle.replace("[Official Music Video]", "");
                videoTitle = videoTitle.replace("(Official Music Video)", "");
                videoTitle = videoTitle.replace("(Official Video HD)", "");
                videoTitle = videoTitle.replace("(OFFICIAL MUSIC VIDEO)", "");
                imgURL = vMeta.getMaxResImageUrl();

                if(videoTitle.contains("–")) {
                    String[] splitTitle = videoTitle.split("–");
                    title = splitTitle[1];
                    artist = splitTitle[0];

                } else if(videoTitle.contains("-")) {
                    String[] splitTitle = videoTitle.split("-");
                    title = splitTitle[1];
                    artist = splitTitle[0];

                } else {
                    title = videoTitle;
                    artist = null;
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(ytFile.getUrl()));
                request.setTitle(videoTitle);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, videoTitle + ".m4a");
                currentDownload = manager.enqueue(request);

                progressNoti.updateProgress(2, "Download file", downloadQueue.size(), true);
            }
        }.extract(downloadQueue.get(0), false, false);
    }
}
