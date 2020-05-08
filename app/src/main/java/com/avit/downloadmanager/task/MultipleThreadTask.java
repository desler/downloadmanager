package com.avit.downloadmanager.task;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public final class MultipleThreadTask extends AbstactTask implements SingleTask.LoadListener {

    /**
     * 最多 4 个线程
     */
    private final static int MAX_THREADS = 4;

    /**
     * 每个 线程的 最小下载单元  不小于 5M，小于 5M 则 为单线程下载
     */
    private final static int UNIT_SIZE = 5 * 1024 * 1024;

    private final static String pathFormat = "%s/%s.part.%d";

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

    protected MultipleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
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
            downloadHelper.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return true;
    }

    private DLTempConfig createDLTempConfig(int index, long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey();

        dlTempConfig.start = index * unitSize + index * 1;
        dlTempConfig.end = dlTempConfig.start + length;

        dlTempConfig.filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename(), index);

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

    @Override
    protected boolean onDownload() {

        Log.d(TAG, "onDownload: file size = " + fileLength);

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

        Log.d(TAG, "onDownload: unit size = " + unitSize);

        long modSize = fileLength % unitSize;
        if (modSize != 0) {
            count = count + 1;
        }

        Log.d(TAG, "onDownload: thread num = " + count);
        singleTasks = new SingleTask[count];
        dlTempConfigs = new DLTempConfig[count];
        futures = new Future[count];

        long begin = System.currentTimeMillis();
        Log.d(TAG, "onDownload: begin download at " + begin);

        DLTempConfig tempConfig = createDLTempConfig(count - 1, modSize);
        if (tempConfig.end != fileLength) {
            Log.e(TAG, "onDownload: calculate error");
            return Boolean.FALSE;
        }
        dlTempConfigs[count - 1] = tempConfig;
        singleTasks[count - 1] = createSingleTask(tempConfig);
        futures[count - 1] = executorService.submit(singleTasks[count - 1]);

        for (int i = 0; i < count - 1; ++i) {
            tempConfig = createDLTempConfig(i, unitSize);
            dlTempConfigs[i] = tempConfig;
            singleTasks[i] = createSingleTask(tempConfig);
            futures[i] = executorService.submit(singleTasks[i]);
        }

        /**
         * block here
         */
        for (int i = 0; i < futures.length && !hasError; ++i) {
            try {
                futures[i].get();
            } catch (Throwable e) {
                Log.e(TAG, "onDownload: ", e);
                hasError = true;
                taskListener.onError(downloadItem, null);
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
            Log.d(TAG, "onDownload: merge files " + partFile);
            files[i] = new File(partFile);
        }

        if (!mergeFiles(files)) {
            return false;
        }

        Log.d(TAG, "onDownload: merge success");

        return true;
    }


    @Override
    public void release() {

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

        String filePath = downloadItem.getSavePath() + "/" + downloadItem.getFilename();
        if (files.length == 1) {
            files[0].renameTo(new File(filePath));
            Log.d(TAG, "mergeFiles: only one file, rename it, no need to merge");
            return true;
        }

        long begin = System.currentTimeMillis();
        Log.d(TAG, "mergeFiles: begin = " + begin);

        File file = new File(filePath + ".tmp");

        FileOutputStream fileOutputStream = null;
        FileInputStream fileInputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            byte buffer[] = new byte[1 * 1024 * 1024];
            for (File f : files) {
                Log.d(TAG, "mergeFiles: " + f.getPath());

                int count = 0;
                fileInputStream = new FileInputStream(f);
                while ((count = fileInputStream.read(buffer, 0, buffer.length)) != -1) {
                    fileOutputStream.write(buffer, 0, count);
                }
                fileOutputStream.flush();
//                fileOutputStream.getFD().sync();
                fileInputStream.close();
                Log.d(TAG, "mergeFiles: finish > " + f.getPath());
            }

            fileOutputStream.flush();
            fileOutputStream.close();

            file.renameTo(new File(filePath));

            /**
             * 删除多线程下载时，各线程 生成的 part.x 文件
             */
            for (File f : files) {
                Log.d(TAG, "mergeFiles: delete part file " + f.getPath() + " > " + f.delete());
            }

            Log.d(TAG, "mergeFiles: cost = " + (System.currentTimeMillis() - begin));

            return true;

        } catch (Throwable e) {
            Log.e(TAG, "mergeFiles: ", e);
            taskListener.onError(downloadItem, null);
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

    @Override
    public void onUpdate(DLTempConfig dlTempConfig, long size) {
        Log.d(TAG, "onUpdate: " + dlTempConfig);
        taskListener.onUpdateProgress(getDownloadItem(), (int) (calculateProgress() * 100));
    }

    private float calculateProgress() {

        long alSize = 0;

        for (DLTempConfig config : dlTempConfigs) {
            alSize += config.written;
        }

        Log.d(TAG, String.format("calculateProgress: [%ld, %ld]", alSize, fileLength));

        return alSize * 1.0f / fileLength;
    }

    @Override
    public void onError(DLTempConfig dlTempConfig, Error error) {
        hasError = true;
        taskListener.onError(downloadItem, null);
    }

}

class SingleTask implements Callable<DLTempConfig> {

    private DownloadItem downloadItem;

    private DLTempConfig dlTempConfig;
    private boolean supportBreakpoint;

    private LoadListener loadListener;

    private DownloadHelper downloadHelper;

    protected SingleTask(DownloadItem downloadItem) {
        this.downloadItem = downloadItem;
    }

    SingleTask withDLTempConfig(DLTempConfig config) {
        this.dlTempConfig = config;
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

    @Override
    public DLTempConfig call() throws Exception {


        return dlTempConfig;
    }

    interface LoadListener {
        void onUpdate(DLTempConfig dlTempConfig, long size);

        void onError(DLTempConfig dlTempConfig, Error error);
    }
}
