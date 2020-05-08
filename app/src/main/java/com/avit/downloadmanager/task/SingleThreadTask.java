package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class SingleThreadTask extends AbstactTask implements DownloadHelper.OnProgressListener {

    private final static String pathFormat = "%s/%";

    private DLTempConfig tempConfig;
    private DownloadHelper downloadHelper;
    private long fileLength;

    public SingleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
    }

    private DLTempConfig createDLTempConfig(long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey();

        dlTempConfig.start = 0;
        dlTempConfig.end = length;

        dlTempConfig.filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
        dlTempConfig.seq = 0;

        return dlTempConfig;
    }

    @Override
    protected boolean onStart() {

        try {
            downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath()).created();
            int responseCode = downloadHelper.getResponseCode();

            Log.d(TAG, "onStart: responseCode = " + responseCode);
            if (HttpURLConnection.HTTP_OK == responseCode || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                fileLength = downloadHelper.getContentLength();
            } else {
                return false;
            }

            if (tempConfig == null) {
                tempConfig = createDLTempConfig(fileLength);
            }

            taskListener.onStart(downloadItem);

            return true;

        } catch (IOException e) {
            Log.e(TAG, "onStart: ", e);
            taskListener.onError(downloadItem, null);
        }

        return false;
    }

    @Override
    protected boolean onDownload() {

        if (!spaceGuard.occupySize(fileLength)) {
            return false;
        }

        try {
            File file = downloadHelper.withProgressListener(this)
                    .retrieveFile(downloadItem.getSavePath() + File.pathSeparator + downloadItem.getFilename());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "onDownload: ", e);
            taskListener.onError(downloadItem, null);
        }

        return false;
    }


    @Override
    public void start() {
        super.start();
    }

    @Override
    public void release() {
        super.release();
        downloadHelper.release();
    }

    @Override
    public void onProgress(String dlPath, String filePath, int length) {
        taskListener.onUpdateProgress(downloadItem, (int) (length * 1.0f / fileLength * 100));
    }
}
