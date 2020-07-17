package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.DownloadManager;
import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.download.DownloadHelper;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.IGuard;
import com.avit.downloadmanager.guard.SpaceGuardEvent;
import com.avit.downloadmanager.task.exception.PauseExecute;
import com.avit.downloadmanager.task.exception.TaskException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.List;

public class SingleRandomTask extends AbstactTask<SingleRandomTask> implements DownloadHelper.OnProgressListener {

    private final static String KEY_SUFFIX = ".single";
    private final static String KEY_TMP = ".tmp";

    private DLTempConfig dlConfig;
    private DownloadHelper downloadHelper;
    private long fileLength;

    private final Object spaceWait = new Object();
//    private final Object stateWait = new Object();

    private long breakPoint;

    public SingleRandomTask(DownloadItem downloadItem) {
        super(downloadItem);
        TAG = "SingleRandomTask";
    }

    private DLTempConfig createDLTempConfig(long start, long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey() + KEY_SUFFIX;

        dlTempConfig.start = start;
        dlTempConfig.end = length;

        dlTempConfig.filePath = String.format(pathFormat + KEY_SUFFIX, downloadItem.getSavePath(), downloadItem.getFilename());
        dlTempConfig.seq = 0;

        return dlTempConfig;
    }

    @Override
    protected boolean onStart() {

        RandomAccessFile accessFile = null;

        try {
            downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath());
            String fileFullPath = String.format(pathFormat + KEY_SUFFIX, downloadItem.getSavePath(), downloadItem.getFilename());

            /**
             * 是否需要断点续传, 且如果为了下载创建的临时文件存在，则 断点续传的数据才是真正有效的。
             */
            long realLength = 0;
            File fileTemp = new File(fileFullPath + KEY_TMP);
            if (supportBreakpoint && fileTemp.exists() && fileTemp.isFile()) {
                long writtenLength = 0;
                List<DLTempConfig> list = breakPointHelper.findByKey(downloadItem.getKey() + KEY_SUFFIX);
                if (!list.isEmpty()) {
                    DLTempConfig temp = list.get(0);
                    writtenLength = temp.written;
                    realLength = temp.contentLength;
                    Log.d(TAG, "onStart: break point real length = " + realLength);
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
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_NETWORK.value(), "http response code = " + responseCode));
                return false;
            }
            fileLength = contentLength + breakPoint;

            if (dlConfig == null) {
                dlConfig = createDLTempConfig(breakPoint, fileLength - 1);
                dlConfig.written = breakPoint;
            }

            /**
             * 首次下载，文件总长度 未设置，此时 下载断点肯定不存在，因此 breakPoint == 0, real length 尚未设置
             * fileLength == contentLength == 文件总长度
             */
            if (realLength == 0){
                realLength = contentLength;
            }
            dlConfig.contentLength = realLength;
            Log.d(TAG, "onStart: real length = " + realLength);

            /**
             * 文件总长度 与 range的 end 数值 无法做数学相等，证明 当前http资源请求 不支持 range
             */
            if (realLength != dlConfig.end + 1) {
                Log.e(TAG, "onStart: DONOT support breakpoint");
                /**
                 * 清楚 基于断点续传的 状态值
                 */
                breakPoint = 0;
                fileLength = 0;

                fileTemp.delete();

                DownloadHelper downloadHelper = this.downloadHelper.clone();
                downloadHelper.withRange(0, -1);
                this.downloadHelper.release();
                this.downloadHelper = downloadHelper;
                this.downloadHelper.created();

                contentLength = this.downloadHelper.getContentLength();
                fileLength = dlConfig.contentLength = realLength = contentLength;
                dlConfig.clearBreakpoint();
            }

            /**
             * 创建固定大小文件作为占位
             */
            accessFile = new RandomAccessFile(fileTemp, "rw");
            accessFile.setLength(realLength);

            Log.d(TAG, "onStart: remain fileLength = " + contentLength + ", file = " + downloadItem.getFilename());

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
        } catch (PauseExecute pauseExecute){
            Log.w(TAG, "onDownload: " + pauseExecute.getMessage());
            taskListener.onPause(downloadItem, prePercent);
            throw pauseExecute;
        } finally {
            Log.d(TAG, "onDownload: always release");
            downloadHelper.release();
            notifySpace();
        }

        return false;
    }

    @Override
    public void release() {
        super.release();
        if (downloadHelper != null) {
            downloadHelper.release();
        }
    }

    private int prePercent;

    @Override
    public void onProgress(String dlPath, String filePath, long length) {
        dlConfig.written = breakPoint + length;

        int percent = (int) (dlConfig.written * 1.0f / fileLength * 100);
        if (percent != prePercent) {
            breakPointHelper.save(dlConfig);
            taskListener.onUpdateProgress(downloadItem, percent);
            prePercent = percent;
        }

        if (getState() == State.PAUSE) {
            Log.d(TAG, "onProgress: state = " + getState().name());
//            taskListener.onPause(downloadItem, percent);
//            synchronized (stateWait) {
//                try {
//                    stateWait.wait();
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "onProgress: ", e);
//                }
//            }
            throw new PauseExecute("oops, task.key = " + getDownloadItem().getKey() + " is paused!");
        }

        if (!isValidState()) {
            Log.w(TAG, "onProgress: state = " + getState().name());
        }
    }

    private void notifySpace() {
        synchronized (spaceWait) {
            spaceWait.notifyAll();
        }
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
