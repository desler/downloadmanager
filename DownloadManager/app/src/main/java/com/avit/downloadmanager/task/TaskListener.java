package com.avit.downloadmanager.task;

import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.data.DownloadItem;

public interface TaskListener {
    void onStart(DownloadItem item);

    void onCompleted(DownloadItem item);

    void onUpdateProgress(DownloadItem item, long percent);

    void onPause(DownloadItem item, long percent);

    void onError(DownloadItem item, Error error);

    void onStop(DownloadItem item, int reason, String messsage);
}
