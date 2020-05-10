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

import com.github.brokko.youtubedownload.notification.ErrorNotification;
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
    private ProgressNotification progressNotification;
    private ErrorNotification errorNotification;

    private String title;
    private String artist;
    private String imgURL;
    private long currentDownload;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                    progressNotification.setProgress(3, "Write metadata...", downloadQueue.size(), false);

                    DownloadManager.Query query = new DownloadManager.Query();
                    //    query.setFilterById(intent.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID));
                    query.setFilterById(currentDownload);
                    Cursor c = manager.query(query);

                    if (c.moveToFirst()) {
                        if (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                            new MetaDataInsert((File file) -> {
                                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
                                downloadQueue.remove(0);

                                if (downloadQueue.size() != 0) { download(); } else { onDestroy(); }
                            },  new File(Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))).getPath()), title, artist, imgURL).start();
                            c.close();

                        } else {
                            errorNotification.send();
                            progressNotification.destroy();
                        }
                    } else {
                        errorNotification.send();
                        progressNotification.destroy();
                    }

                    break;
                case ErrorNotification.ACTION_CLOSE:
                    onDestroy();
                    break;
                case ErrorNotification.ACTION_TOUCH:
                    download();
                    errorNotification.destroy();
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        progressNotification = new ProgressNotification(this);
        errorNotification = new ErrorNotification(this);

        intentFilter.addAction(ErrorNotification.ACTION_TOUCH);
        intentFilter.addAction(ErrorNotification.ACTION_CLOSE);
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        downloadQueue.add(intent.getStringExtra("URL"));
        progressNotification.updateQueue(downloadQueue.size());

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

        manager.remove(currentDownload);
        downloadQueue.clear();
        progressNotification.destroy();
        errorNotification.destroy();

        unregisterReceiver(receiver);
    }

    @SuppressLint("StaticFieldLeak")
    private void download() {
        progressNotification.setProgress(0, "Fetch stream", downloadQueue.size(), false);

        new YouTubeExtractor(this) {
            @Override
            protected void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                progressNotification.setProgress(1, "Prepare download", downloadQueue.size(), false);

                registerReceiver(receiver, intentFilter);

                if (ytFiles == null) {
                    errorNotification.send();
                    progressNotification.destroy();
                    return;
                }

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

                String[] titart = vMeta.getTitle().split("-");
                imgURL = vMeta.getThumbUrl();
                if (titart.length == 2) {
                    title = titart[1];
                    artist = titart[0];
                } else {
                    title = vMeta.getTitle();
                    artist = "null";
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(ytFile.getUrl()));
                request.setTitle(videoTitle);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, videoTitle + ".m4a");
                currentDownload = manager.enqueue(request);

                progressNotification.setProgress(2, "Download file", downloadQueue.size(), true);
            }
        }.extract(downloadQueue.get(0), false, false);
    }
}
