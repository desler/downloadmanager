package com.avit.downloadmanager.task;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.download.DownloadHelper;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.IGuard;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.guard.SpaceGuardEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public final class MultipleThreadTask extends AbstactTask<MultipleThreadTask> implements SingleTask.LoadListener {

    /**
     * 最多 4 个线程
     */
    private final static int MAX_THREADS = 4;

    /**
     * 每个 线程的 最小下载单元  不小于 5M，小于 5M 则 为单线程下载
     */
    private final static int UNIT_SIZE = 5 * 1024 * 1024;

    private final static String partPathFormat = "%s/%s.part.%d";

    private int maxThreads;
    private long unitSize;
    private long fileLength;

    private SingleTask[] singleTasks;
    private Future<DLTempConfig>[] futures;
    private DLTempConfig[] dlTempConfigs;

    private boolean hasError;

    private DownloadHelper downloadHelper;

    private ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS + 1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("MultipleThreadTask#" + thread.getId());
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    Log.e(TAG, "uncaughtException: " + t.getName(), e);
                }
            });
            return thread;
        }
    });
    private final Object stateWait = new Object();

    public MultipleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
        TAG = "MultipleThreadTask";

        maxThreads = MAX_THREADS;
        unitSize = UNIT_SIZE;
    }

    public MultipleThreadTask withNumThreads(int max) {

        int num = max;
        if (max > MAX_THREADS) {
            num = MAX_THREADS;
        } else if (max <= 0) {
            num = 1;
        }
        this.maxThreads = num;

        return this;
    }

    @Override
    protected boolean onStart() {
        try {
            downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath()).created();
            int responseCode = downloadHelper.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                fileLength = downloadHelper.getContentLength();
            } else {
                Log.e(TAG, "onStart: responseCode = " + responseCode);
                return false;
            }

            Log.d(TAG, "onStart: fileLength = " + size2String(fileLength) + ", file = " + downloadItem.getFilename());

        } catch (IOException e) {
            Log.e(TAG, "onStart: ", e);
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_NETWORK.value(), e.getMessage(), e));
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "onStart: ", e);
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_DATA.value(), e.getMessage(), e));
            return false;
        } finally {
            Log.d(TAG, "onStart: downloadHelper release");
            if (downloadHelper != null)
                downloadHelper.release();
        }

        Log.d(TAG, "onStart: file size = " + fileLength);

        /**
         * 先用 默认 单元 size 计算
         */
        int count = (int) (fileLength / unitSize);

        /**
         * 如果 需要的 线程个数 大于 最大限制的线程 个数，则 重新 计算 单元size 大小。
         */
        if (count >= maxThreads) {
            unitSize = fileLength / maxThreads;
            count = (int) (fileLength / unitSize);
        } else {
            unitSize = UNIT_SIZE;
        }

        Log.d(TAG, "onStart: unit size = " + unitSize);

        long modSize = fileLength % unitSize;
        if (modSize != 0) {
            count = count + 1;
        }

        Log.d(TAG, "onStart: thread num = " + count);
        dlTempConfigs = new DLTempConfig[count];
        DLTempConfig tempConfig = createDLTempConfig(count - 1, modSize);
        if (tempConfig.end != fileLength) {
            Log.e(TAG, "onStart: calculate error");
            return false;
        }
        dlTempConfigs[count - 1] = tempConfig;

        for (int i = 0; i < count - 1; ++i) {
            dlTempConfigs[i] = createDLTempConfig(i, unitSize - 1);
        }

        clearFiles();

        taskListener.onStart(downloadItem);

        return true;
    }

    private DLTempConfig createDLTempConfig(int index, long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey();

        dlTempConfig.start = index * unitSize;
        dlTempConfig.end = dlTempConfig.start + length;

        dlTempConfig.filePath = String.format(partPathFormat, downloadItem.getSavePath(), downloadItem.getFilename(), index);
        dlTempConfig.seq = index;

        return dlTempConfig;
    }

    private SingleTask createSingleTask(DLTempConfig tempConfig) {
        SingleTask singleTask = new SingleTask(downloadItem).withLoadListener(this);
        /**
         * 是否支持断点续写
         */
        if (supportBreakpoint) {
            singleTask.supportBreakpoint();
        }
        return singleTask.withDLTempConfig(tempConfig);
    }

    private void clearFiles() {
        String path = downloadItem.getSavePath() + File.separator + downloadItem.getFilename();
        String tmpPath = path + ".tmp";

        File file = new File(path);
        if (file.exists()) {
            Log.w(TAG, "onDownload: file exists delete " + file.delete());
        }

        file = new File(tmpPath);
        if (file.exists()) {
            Log.w(TAG, "onDownload: tmp exists delete " + file.delete());
        }
    }

    @Override
    protected boolean onDownload() {

        int count = dlTempConfigs.length;

        singleTasks = new SingleTask[count];
        futures = new Future[count];

        long begin = System.currentTimeMillis();
        Log.d(TAG, "onDownload: begin download at " + begin);

        state = State.LOADING;

        for (int i = 0; i < count; ++i) {

            DLTempConfig tempConfig = dlTempConfigs[i];
            Log.d(TAG, String.format(Locale.ENGLISH, "onDownload: file span[%d, %d]", tempConfig.start, tempConfig.end));

            singleTasks[i] = createSingleTask(tempConfig).withSpaceGuard(spaceGuard);
            futures[i] = executorService.submit(singleTasks[i]);
        }

        /**
         * block here
         */
        for (int i = 0; i < futures.length && !hasError; ++i) {
            try {
                futures[i].get();
            } catch (TaskException e) {
                Log.e(TAG, "onDownload: ", e);
                hasError = true;
                taskListener.onStop(downloadItem, 0, e.getMessage());
                break;
            } catch (Throwable e) {
                Log.e(TAG, "onDownload: ", e);
                hasError = true;
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_SYSTEM.value(), e.getMessage(), e));
            }
        }

        if (hasError) {
            Log.e(TAG, "onDownload: error");
            return false;
        }

        Log.d(TAG, "onDownload: download finish, cost = " + (System.currentTimeMillis() - begin));

        /**
         * task all done.
         */
        File[] files = new File[count];
        for (int i = 0; i < count; ++i) {
            String partFile = dlTempConfigs[i].filePath;
//            Log.d(TAG, "onDownload: merge files " + partFile);
            files[i] = new File(partFile);
        }

        if (!mergeFiles(files)) {
            return false;
        }

        Log.d(TAG, "onDownload: merge success");

        return true;
    }

    @Override
    public boolean onGuardEvent(GuardEvent guardEvent) {
        super.onGuardEvent(guardEvent);

        if (guardEvent.type == IGuard.Type.SPACE) {
            if (guardEvent.reason == SpaceGuardEvent.EVENT_ENOUGH) {
                for (SingleTask task : singleTasks) {
                    task.notifySpace();
                }
            }
        }

        return true;
    }

    /**
     * 需要 严格的保证传入文件的 顺序，否则合并以后的文件也是错的，校验无法通过
     *
     * @param files
     * @return
     */
    private boolean mergeFiles(File... files) {

        if (files == null || files.length == 0) {
            Log.w(TAG, "mergeFiles: nothing need to merge");
            return true;
        }

        String filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
        if (files.length == 1) {
            Log.d(TAG, "mergeFiles: only one file, rename it, no need to merge");
            files[0].renameTo(new File(filePath));
            return true;
        }

        long begin = System.currentTimeMillis();
        Log.d(TAG, "mergeFiles: begin = " + begin);

        File file = new File(filePath + ".tmp");

        FileOutputStream fileOutputStream = null;
        FileInputStream fileInputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            /**
             * 本地 读写，速度快，使用 大缓冲方式。
             */
            byte buffer[] = new byte[1 * 1024 * 1024];
            for (File f : files) {
                Log.d(TAG, "mergeFiles: " + f.getName());

                int count = 0;
                fileInputStream = new FileInputStream(f);
                while ((count = fileInputStream.read(buffer, 0, buffer.length)) != -1) {
                    fileOutputStream.write(buffer, 0, count);
                }
                fileOutputStream.flush();
                fileInputStream.close();
                Log.d(TAG, "mergeFiles: finish ");
            }

            fileOutputStream.flush();
            fileOutputStream.close();

            boolean isRename = file.renameTo(new File(filePath));
            if (!isRename) {
                throw new IOException(file.getName() + " rename FAILED");
            } else {
                Log.d(TAG, "mergeFiles: rename to " + downloadItem.getFilename());
            }
            /**
             * 删除多线程下载时，各线程 生成的 part.x 文件
             */
            for (File f : files) {
                Log.d(TAG, "mergeFiles: delete part file " + f.getName() + " > " + f.delete());
            }

            Log.d(TAG, "mergeFiles: cost = " + (System.currentTimeMillis() - begin));

            return true;

        } catch (Throwable e) {
            Log.e(TAG, "mergeFiles: ", e);
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), e.getMessage(), e));
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {

                }
            }
        }

        return false;
    }

    private int prePercent;
    @Override
    public void onUpdate(DLTempConfig dlTempConfig, long size) {
//        Log.d(TAG, "onUpdate: " + dlTempConfig);

        int percent = (int)(calculateProgress() * 100);
        if (percent != prePercent) {
            taskListener.onUpdateProgress(getDownloadItem(), percent);
            prePercent = percent;
        }

        while (getState() == State.PAUSE) {
            Log.d(TAG, "onUpdate: state = " + getState().name());
            synchronized (stateWait) {
                try {
                    stateWait.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "onUpdate: ", e);
                }
            }
        }

        if (getState() == State.RELEASE) {
            throw new TaskException("task already release");
        }
    }

    private float calculateProgress() {

        long alSize = 0;

        for (DLTempConfig config : dlTempConfigs) {
            alSize += config.written;
        }

//        Log.d(TAG, String.format("calculateProgress: [%d, %d]", alSize, fileLength));

        return alSize * 1.0f / fileLength;
    }

    @Override
    public void onError(DLTempConfig dlTempConfig, Error error) {
        hasError = true;
        taskListener.onError(downloadItem, error);
    }


    @Override
    public void start() {
        notifyState();
        super.start();
    }

    @Override
    public void release() {
        super.release();
        executorService.shutdownNow();
    }

    private void notifyState() {
        stateWait.notifyAll();
    }

}

class SingleTask implements Callable<DLTempConfig>, DownloadHelper.OnProgressListener {

    private final static String TAG = "Multiple::SingleTask";

    private DownloadItem downloadItem;
    private DLTempConfig dlConfig;
    private boolean supportBreakpoint;

    private LoadListener loadListener;
    private DownloadHelper downloadHelper;

    private SpaceGuard spaceGuard;
    private final Object waitSpace = new Object();

    protected SingleTask(DownloadItem downloadItem) {
        this.downloadItem = downloadItem;
    }

    SingleTask withDLTempConfig(DLTempConfig config) {
        this.dlConfig = config;
        return this;
    }

    SingleTask supportBreakpoint() {
        supportBreakpoint = true;
        return this;
    }

    SingleTask withLoadListener(LoadListener loadListener) {
        this.loadListener = loadListener;
        return this;
    }

    SingleTask withSpaceGuard(SpaceGuard guard) {
        this.spaceGuard = guard;
        return this;
    }

    void notifySpace() {
        waitSpace.notifyAll();
    }

    @Override
    public DLTempConfig call() throws Exception {

        String tn = Thread.currentThread().getName();

        this.downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath());

        long written = supportBreakpoint ? downloadHelper.resumeBreakPoint(dlConfig.filePath) : 0;
        long start = dlConfig.start;
        if (written > 0) {
            Log.w(TAG, tn + " call: resume break point written length = " + written);
            start += written;
            dlConfig.written = written;
        }
        downloadHelper.withRange(start, dlConfig.end).created();

        Log.d(TAG, tn + " call: remain file length = " + downloadHelper.getContentLength());

        long fileLength = dlConfig.end - start;
        /**
         * 如果空间不够，则 等待
         */
        while (!spaceGuard.occupySize(fileLength - written)) {
            synchronized (waitSpace) {
                try {
                    waitSpace.wait();
                } catch (Throwable e) {
                    Log.e(TAG, tn + " call: ", e);
                }
            }
        }

        try {
            downloadHelper.withProgressListener(this).retrieveFile(dlConfig.filePath);
        } catch (IOException e) {
            Log.e(TAG, tn + " call: ", e);
            loadListener.onError(dlConfig, new Error(Error.Type.ERROR_FILE.value(), e.getMessage(), e));
        } finally {
            Log.d(TAG, tn + " call: always release");
            downloadHelper.release();
        }

        return dlConfig;
    }

    @Override
    public void onProgress(String dlPath, String filePath, int length) {
        dlConfig.written = length;
        if (loadListener != null) {
            loadListener.onUpdate(dlConfig, length);
        }
    }

    interface LoadListener {
        void onUpdate(DLTempConfig dlTempConfig, long size);

        void onError(DLTempConfig dlTempConfig, Error error);
    }
}
