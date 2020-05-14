package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.download.DownloadHelper;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.IGuard;
import com.avit.downloadmanager.guard.SpaceGuardEvent;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.List;

public class SingleRandomTask extends AbstactTask<SingleRandomTask> implements DownloadHelper.OnProgressListener {

    private DLTempConfig dlConfig;
    private DownloadHelper downloadHelper;
    private long fileLength;

    private final Object spaceWait = new Object();
    private final Object stateWait = new Object();

    private long breakPoint;

    public SingleRandomTask(DownloadItem downloadItem) {
        super(downloadItem);
        TAG = "SingleRandomTask";
    }

    private DLTempConfig createDLTempConfig(long start, long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey() + "#single";

        dlTempConfig.start = start;
        dlTempConfig.end = length;

        dlTempConfig.filePath = String.format(pathFormat + ".single", downloadItem.getSavePath(), downloadItem.getFilename());
        dlTempConfig.seq = 0;

        return dlTempConfig;
    }

    @Override
    protected boolean onStart() {

        RandomAccessFile accessFile = null;

        try {
            downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath());
            String fileFullPath = String.format(pathFormat + ".single", downloadItem.getSavePath(), downloadItem.getFilename());

            /**
             * 是否需要断点续传, 且如果为了下载创建的临时文件存在，则 断点续传的数据才是真正有效的。
             */
            File fileTemp = new File(fileFullPath + ".tmp");
            if (supportBreakpoint && fileTemp.exists() && fileTemp.isFile()) {
                long writtenLength = 0;
                List<DLTempConfig> list = breakPointHelper.findByKey(downloadItem.getKey());
                if (!list.isEmpty()) {
                    DLTempConfig temp = list.get(0);
                    writtenLength = temp.written;
                }
                if (writtenLength > 0) {
                    Log.w(TAG, "onStart: resume break point written length = " + writtenLength);
                    downloadHelper.withRange(writtenLength, -1);
                    breakPoint = writtenLength;
                }
            }

            downloadHelper.created();
            int responseCode = downloadHelper.getResponseCode();
            long contentLength;
            if (HttpURLConnection.HTTP_OK == responseCode || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                contentLength = downloadHelper.getContentLength();
            } else {
                Log.e(TAG, "onStart: responseCode = " + responseCode);
                return false;
            }
            fileLength = contentLength + breakPoint;

            /**
             * 创建固定大小文件作为占位
             */
            accessFile = new RandomAccessFile(fileTemp, "rw");
            accessFile.setLength(fileLength);

            Log.d(TAG, "onStart: remain fileLength = " + contentLength + ", file = " + downloadItem.getFilename());

            if (dlConfig == null) {
                dlConfig = createDLTempConfig(breakPoint, fileLength);
                dlConfig.written = breakPoint;
            }

            taskListener.onStart(downloadItem);

            return true;

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "onStart: ", e);

            if (downloadHelper != null)
                downloadHelper.release();

            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_DATA.value(), e.toString(), e));
        } catch (IOException e) {
            Log.e(TAG, "onStart: ", e);

            if (downloadHelper != null)
                downloadHelper.release();

            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), e.toString()));

        } catch (Throwable e) {
            Log.e(TAG, "onStart: ", e);

            if (downloadHelper != null)
                downloadHelper.release();

            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_NETWORK.value(), e.toString(), e));
        } finally {
            if (accessFile != null) {
                try {
                    accessFile.close();
                } catch (IOException e) {
                }
            }
        }

        return false;
    }

    @Override
    protected boolean onDownload() {

        long begin = System.currentTimeMillis();

        while (!spaceGuard.occupySize(fileLength - breakPoint)) {
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
            downloadHelper.withProgressListener(this).retrieveFileByRandom(dlConfig.filePath);
            Log.d(TAG, "onDownload: cost = " + (System.currentTimeMillis() - begin));

            /**
             * 下载完成，删除断点记录
             */
            Log.d(TAG, "onDownload: break point delete, " + breakPointHelper.delete(dlConfig));

            File single = new File(dlConfig.filePath);
            boolean isRename = single.renameTo(new File(String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename())));

            if (!isRename) {
                Log.e(TAG, "onDownload: rename FAILED");
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), "rename failed"));
                return false;
            }

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

    private int prePercent;

    @Override
    public void onProgress(String dlPath, String filePath, long length) {
        dlConfig.written = breakPoint + length;

        breakPointHelper.save(dlConfig);

        int percent = (int) (dlConfig.written * 1.0f / fileLength * 100);
        if (percent != prePercent) {
            taskListener.onUpdateProgress(downloadItem, percent);
            prePercent = percent;
        }

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
