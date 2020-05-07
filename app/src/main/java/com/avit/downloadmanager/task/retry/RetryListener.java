package com.avit.downloadmanager.task.retry;

import com.avit.downloadmanager.data.DownloadItem;

public interface RetryListener {
    void onRetry(DownloadItem item, int retryCount, int reason, String message);
}
