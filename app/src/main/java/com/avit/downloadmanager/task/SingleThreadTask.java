package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.download.DownloadHelper;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.IGuard;
import com.avit.downloadmanager.guard.SpaceGuardEvent;

import java.io.IOException;
import java.net.HttpURLConnection;

public class SingleThreadTask extends AbstactTask<SingleThreadTask> implements DownloadHelper.OnProgressListener {

    private DLTempConfig dlConfig;
    private DownloadHelper downloadHelper;
    private long fileLength;

    private final Object spaceWait = new Object();
    private final Object stateWait = new Object();
    private long startPosition;

    public SingleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
    }

    private DLTempConfig createDLTempConfig(long start, long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey();

        dlTempConfig.start = start;
        dlTempConfig.end = length;

        dlTempConfig.filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
        dlTempConfig.seq = 0;

        return dlTempConfig;
    }

    @Override
    protected boolean onStart() {

        try {
            downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath());

            String fileFullPath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
            /**
             * 是否需要断点续传
             */
            long writtenLength = supportBreakpoint ? downloadHelper.resumeBreakPoint(fileFullPath) : 0;
            if (writtenLength > 0) {
                Log.w(TAG, "onStart: resume break point written length = " + writtenLength);
                downloadHelper.withRange(writtenLength, -1);
                startPosition = writtenLength;
            }
            downloadHelper.created();

            int responseCode = downloadHelper.getResponseCode();
            if (HttpURLConnection.HTTP_OK == responseCode || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                fileLength = downloadHelper.getContentLength();
            } else {
                Log.e(TAG, "onStart: responseCode = " + responseCode);
                return false;
            }
            Log.d(TAG, "onStart: remain fileLength = " + size2String(fileLength) + ", file = " + downloadItem.getFilename());

            if (dlConfig == null) {
                dlConfig = createDLTempConfig(startPosition, fileLength);
            }

            taskListener.onStart(downloadItem);

            return true;

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "onStart: ", e);

            if (downloadHelper != null)
                downloadHelper.release();

            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_DATA.value(), e.getMessage(), e));
        } catch (Throwable e) {
            Log.e(TAG, "onStart: ", e);

            if (downloadHelper != null)
                downloadHelper.release();

            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_NETWORK.value(), e.getMessage(), e));
        }

        return false;
    }

    @Override
    protected boolean onDownload() {

        long begin = System.currentTimeMillis();

        while (!spaceGuard.occupySize(fileLength - startPosition)) {
            Log.w(TAG, "onDownload: wait ");
            synchronized (spaceWait) {
                try {
                    spaceWait.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "onDownload: ", e);
                }
            }
        }

        state = State.LOADING;

        try {
            downloadHelper.withProgressListener(this).retrieveFile(dlConfig.filePath);
            Log.d(TAG, "onDownload: cost = " + (System.currentTimeMillis() - begin));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "onDownload: ", e);
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), e.getMessage(), e));
        } catch (TaskException e) {
            taskListener.onStop(downloadItem, 0, e.getMessage());
        } finally {
            Log.d(TAG, "onDownload: always release");
            downloadHelper.release();
        }

        return false;
    }


    @Override
    public void start() {
        notifyState();
        super.start();
    }

    @Override
    public void release() {
        super.release();
        downloadHelper.release();
    }

    @Override
    public void onProgress(String dlPath, String filePath, int length) {
        dlConfig.written = length;
        taskListener.onUpdateProgress(downloadItem, (int) (length * 1.0f / fileLength * 100));

        while (getState() == State.PAUSE) {
            Log.d(TAG, "onProgress: state = " + getState().name());
            synchronized (stateWait) {
                try {
                    stateWait.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "onProgress: ", e);
                }
            }
        }

        if (getState() == State.RELEASE) {
            Log.w(TAG, "onProgress: state = " + getState().name());
            throw new TaskException("task already release");
        }
    }

    private void notifyState() {
        stateWait.notifyAll();
    }

    private void notifySpace() {
        spaceWait.notifyAll();
    }

    @Override
    public boolean onGuardEvent(GuardEvent guardEvent) {
        super.onGuardEvent(guardEvent);

        if (guardEvent.type == IGuard.Type.SPACE) {
            if (guardEvent.reason == SpaceGuardEvent.EVENT_ENOUGH) {
                notifySpace();
            }
        }
        return true;
    }

}
