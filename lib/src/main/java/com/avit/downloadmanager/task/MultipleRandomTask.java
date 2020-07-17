package com.avit.downloadmanager.task;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.download.DownloadHelper;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.executor.AbsExecutor;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.IGuard;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.guard.SpaceGuardEvent;
import com.avit.downloadmanager.guard.SystemGuard;
import com.avit.downloadmanager.task.exception.PauseExecute;
import com.avit.downloadmanager.task.exception.FallbackException;
import com.avit.downloadmanager.task.exception.TaskException;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

public final class MultipleRandomTask extends AbstactTask<MultipleRandomTask> implements RandomTask.LoadListener {

    /**
     * 最多 4 个线程
     */
    private final static int MAX_THREADS = 4;

    /**
     * 每个 线程的 最小下载单元  不小于 5M，小于 5M 则 为单线程下载
     */
    private final static int UNIT_SIZE = 3 * 1024 * 1024;
    private final static String KEY_SUFFIX = ".multi";
    private final static String KEY_TMP = ".tmp";

    private int maxThreads;
    private long unitSize;
    private long fileLength;

    private RandomTask[] singleTasks;
    private Future<DLTempConfig>[] futures;
    private DLTempConfig[] dlTempConfigs;

    private boolean hasError;

    private DownloadHelper downloadHelper;

    private ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS + 1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("MultipleTask#" + thread.getId());
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    Log.e(TAG, "uncaughtException: " + t.getName(), e);
                }
            });
            return thread;
        }
    });
//    private final Object stateWait = new Object();

    public MultipleRandomTask(DownloadItem downloadItem) {
        super(downloadItem);
        TAG = "MultipleRandomTask";

        maxThreads = MAX_THREADS;
        unitSize = UNIT_SIZE;
    }

    public MultipleRandomTask withNumThreads(int max) {

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
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_NETWORK.value(), "http response code = " + responseCode));
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
        long endSize;
        long modSize = fileLength % unitSize;
        if (modSize != 0 && count == 0) {
            count = count + 1;
            endSize = modSize - 1;
        } else {
            endSize = unitSize + modSize - 1;
        }

        Log.d(TAG, "onStart: thread num = " + count);
        dlTempConfigs = new DLTempConfig[count];
        /**
         * http range 的范围是 从 0 开始的 即  0-1 表示两个字节，
         * ①，因此range中，计算范围时，需要在长度基础上 -1，
         * ②，最后一个 下载单元，不 减1，之所以不会出错，是因为文件只有这么长，因此返回了实际的长度，
         * 但实际上之前的计算方式是错的
         */
        DLTempConfig tempConfig = createDLTempConfig(count - 1, endSize);
        if (tempConfig.end + 1 != fileLength) {
            Log.e(TAG, "onStart: calculate error " + (tempConfig.end + 1));
            return false;
        }
        dlTempConfigs[count - 1] = tempConfig;

        for (int i = 0; i < count - 1; ++i) {
            dlTempConfigs[i] = createDLTempConfig(i, unitSize - 1);
        }

        File fileTemp = new File(dlTempConfigs[0].filePath + KEY_TMP);
        if (supportBreakpoint && fileTemp.exists() && fileTemp.isFile()) {
            List<DLTempConfig> configs = breakPointHelper.findByKey(downloadItem.getKey() + KEY_SUFFIX);
            Log.d(TAG, "onStart: break point configs size = " + configs.size());
            for (int i = 0; i < configs.size(); ++i) {
                DLTempConfig tmp = configs.get(i);
                Log.d(TAG, "onStart: break point set, seq = " + tmp.seq);
                dlTempConfigs[tmp.seq].written = tmp.written;
            }
        }

        taskListener.onStart(downloadItem);

        return true;
    }

    private DLTempConfig createDLTempConfig(int index, long span) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey() + KEY_SUFFIX;

        dlTempConfig.start = index * unitSize;
        dlTempConfig.end = dlTempConfig.start + span;

        dlTempConfig.filePath = String.format(pathFormat + KEY_SUFFIX, downloadItem.getSavePath(), downloadItem.getFilename());
        dlTempConfig.seq = index;

        return dlTempConfig;
    }

    private RandomTask createSingleTask(DLTempConfig tempConfig) {
        RandomTask singleTask = new RandomTask(downloadItem).withLoadListener(this);
        return singleTask.withDLTempConfig(tempConfig);
    }

    @Override
    protected boolean onDownload() {

        int count = dlTempConfigs.length;

        singleTasks = new RandomTask[count];
        futures = new Future[count];

        long begin = System.currentTimeMillis();
        Log.d(TAG, "onDownload: begin download at " + begin);

        state = State.LOADING;

        /**
         * 创建 固定大小的文件
         */
        File file = new File(dlTempConfigs[0].filePath + KEY_TMP);
        RandomAccessFile accessFile = null;
        try {
            accessFile = new RandomAccessFile(file, "rw");
            accessFile.setLength(fileLength);
        } catch (IOException e) {
            Log.e(TAG, "onDownload: ", e);
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), e.toString()));
            return false;
        } finally {
            if (accessFile != null) {
                try {
                    accessFile.close();
                } catch (IOException e) {
                }
            }
        }

        for (int i = 0; i < count; ++i) {

            DLTempConfig tempConfig = dlTempConfigs[i];
            Log.d(TAG, String.format(Locale.ENGLISH, "onDownload: file span[%d, %d]", tempConfig.start, tempConfig.end));

            singleTasks[i] = createSingleTask(tempConfig).withSpaceGuard(spaceGuard);
            futures[i] = executorService.submit(singleTasks[i]);
        }

        /**
         * block here
         */
        try {
            for (int i = 0; i < futures.length && !hasError; ++i) {
                futures[i].get();
            }
        } catch (ExecutionException ex) {
            if (!executionExceptionParse(ex)) {
                Log.e(TAG, "onDownload: ", ex);
                hasError = true;
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_UNKNOWN.value(), ex.getMessage(), ex));
            }
        } catch (Throwable e) {
            Log.e(TAG, "onDownload: ", e);
            hasError = true;
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_SYSTEM.value(), e.getMessage(), e));
        } finally {
            Log.w(TAG, "onDownload: always release");
            releaseTask();
        }

        if (hasError) {
            Log.e(TAG, "onDownload: error " + downloadItem.getFilename());
            return false;
        }

        Log.d(TAG, "onDownload: download finish, cost = " + (System.currentTimeMillis() - begin));
        /**
         * 下载完成，删除断点记录
         */
        Log.d(TAG, "onDownload: break point delete, " + breakPointHelper.deleteByKey(downloadItem.getKey() + KEY_SUFFIX));
        /**
         * task all done.
         */
        if (file.exists() && file.isFile()) {
            String fullPath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
            boolean res = file.renameTo(new File(fullPath));
            if (!res) {
                Log.e(TAG, "onDownload: rename FAILED");
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), "rename FAILED"));
                return false;
            } else {
                Log.d(TAG, "onDownload: rename to " + fullPath);
            }
        }

        return true;
    }

    private boolean executionExceptionParse(ExecutionException ex) {
        Throwable throwable = ex.getCause();
        if (throwable == null)
            return false;

        if (throwable instanceof TaskException) {

            Log.e(TAG, "executionExceptionParse: ", throwable);
            hasError = true;
            taskListener.onStop(downloadItem, 0, throwable.getMessage());
            return true;

        } else if (throwable instanceof PauseExecute) {

            Log.w(TAG, "executionExceptionParse: " + throwable.getMessage());
            taskListener.onPause(downloadItem, prePercent);
            throw (PauseExecute) throwable;

        } else if (throwable instanceof FallbackException) {

            Log.w(TAG, "executionExceptionParse: " +  throwable.getMessage());
            if (!hasParent()) {
                ITask fallbackTask = fallback();
                Log.w(TAG, "executionExceptionParse: will fall back to singleTask" + fallbackTask);
                submit(fallbackTask);
            }
            throw (FallbackException) throwable;

        } else if (throwable instanceof IOException) {
            taskListener.onError(downloadItem, new Error(Error.Type.ERROR_FILE.value(), throwable.getMessage(), throwable));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public ITask fallback() {

        /**
         * 不支持多线程下载，则删除之前存在的 临时文件 及 数据库表中用于 断点续传的数据
         */
        if (dlTempConfigs != null && dlTempConfigs.length >0){
            Log.w(TAG, "fallback: delete multi config -> " + downloadItem.getDlPath() + " " + new File(dlTempConfigs[0].filePath + KEY_TMP).delete());
            Log.w(TAG, "fallback: break point delete, " + breakPointHelper.deleteByKey(downloadItem.getKey() + KEY_SUFFIX));
        }

        AbstactTask<SingleRandomTask>  single = new SingleRandomTask(downloadItem)
                .withGuard(systemGuards.toArray(new SystemGuard[0]))
                .withListener(taskListener)
                .withVerifyConfig(verifyConfigs.toArray(new VerifyConfig[0]));

        single.setParent(getParent());
        single.setExecutor(getAbsExecutor());

        if (callbackOnMainThread){
            single.callbackOnMainThread();
        }

        if (supportBreakpoint){
            single.supportBreakpoint();
        }

        return single;
    }

    @Override
    public boolean onGuardEvent(GuardEvent guardEvent) {
        super.onGuardEvent(guardEvent);

        if (guardEvent.type == IGuard.Type.SPACE) {
            if (guardEvent.reason == SpaceGuardEvent.EVENT_ENOUGH) {
                for (RandomTask task : singleTasks) {
                    task.notifySpace();
                }
            }
        }

        return true;
    }

    private volatile int prePercent;

    @Override
    public void onUpdate(DLTempConfig dlTempConfig, long size) {


        int percent = (int) (calculateProgress() * 100);
        if (percent != prePercent) {
            breakPointHelper.save(dlTempConfig);
            taskListener.onUpdateProgress(getDownloadItem(), percent);
            prePercent = percent;
        }

        if (getState() == State.PAUSE) {
            Log.d(TAG, "onUpdate: state = " + getState().name());
//            taskListener.onPause(getDownloadItem(), percent);
//            synchronized (stateWait) {
//                try {
//                    stateWait.wait();
//                } catch (InterruptedException e) {
//                    Log.e(TAG, "onUpdate: ", e);
//                }
//            }
            throw new PauseExecute("oops, task.key = " + getDownloadItem().getKey() + " is paused!");
        }

        if (!isValidState()) {
            Log.w(TAG, "onProgress: state = " + getState().name());
        }
    }

    private float calculateProgress() {

        long alSize = 0;

        for (DLTempConfig config : dlTempConfigs) {
            alSize += config.written;
        }

        return alSize * 1.0f / fileLength;
    }

    @Override
    public void onError(DLTempConfig dlTempConfig, Error error) {
        hasError = true;
        taskListener.onError(downloadItem, error);
    }

    @Override
    public void release() {
        super.release();
        releaseTask();
        executorService.shutdownNow();
    }

    private void releaseTask(){
        if (this.futures == null || this.futures.length <= 0)
            return ;

        Future<DLTempConfig>[] fs = this.futures;
        for (Future<DLTempConfig> f : fs){
            if (f == null || f.isCancelled() || f.isDone())
                continue;
            f.cancel(true);
        }
    }
}

class RandomTask implements Callable<DLTempConfig>, DownloadHelper.OnProgressListener {

    private final static String TAG = "MultipleRandom::Single";

    private DownloadItem downloadItem;
    private DLTempConfig dlConfig;

    private LoadListener loadListener;
    private DownloadHelper downloadHelper;

    private SpaceGuard spaceGuard;
    private final Object waitSpace = new Object();

    private long breakPoint;

    protected RandomTask(DownloadItem downloadItem) {
        this.downloadItem = downloadItem;
    }

    RandomTask withDLTempConfig(DLTempConfig config) {
        this.dlConfig = config;
        return this;
    }

    RandomTask withLoadListener(LoadListener loadListener) {
        this.loadListener = loadListener;
        return this;
    }

    RandomTask withSpaceGuard(SpaceGuard guard) {
        this.spaceGuard = guard;
        return this;
    }

    void notifySpace() {
        synchronized (waitSpace) {
            waitSpace.notifyAll();
        }
    }

    @Override
    public DLTempConfig call() throws Exception {

        String tn = Thread.currentThread().getName();
        this.downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath());

        if (dlConfig.written > 0) {
            breakPoint = dlConfig.written;
            Log.w(TAG, tn + " call: resume break point written length = " + breakPoint);
        }

        long start = dlConfig.start + breakPoint;
        downloadHelper.withRange(start, dlConfig.end).created();

        long contentLength = downloadHelper.getContentLength();
        Log.d(TAG, tn + " call: remain file length = " + contentLength);

        long range = dlConfig.end - start;
        Log.d(TAG, tn + " call: range = " + range);

        try {
            /**
             * 为什么要加 +1，可以查看 创建 config 的注释，range 的范围 是从 0 开始的，且头尾包含。
             */
            if (contentLength != range + 1) {
                throw new FallbackException("do not support http Rang.");
            }

            /**
             * 如果空间不够，则 等待
             */
            while (!spaceGuard.occupySize(contentLength)) {
                synchronized (waitSpace) {
                    try {
                        waitSpace.wait();
                    } catch (Throwable e) {
                        Log.e(TAG, tn + " call: ", e);
                    }
                }
            }

            downloadHelper.withProgressListener(this).noRename().retrieveFileByRandom(dlConfig.filePath);
        } catch (Throwable throwable) {
            throw throwable;
        } finally {
            Log.d(TAG, tn + " call: always release");
            downloadHelper.release();
            notifySpace();
        }

        return dlConfig;
    }

    @Override
    public void onProgress(String dlPath, String filePath, long length) {
        dlConfig.written = breakPoint + length;

        if (loadListener != null) {
            loadListener.onUpdate(dlConfig, dlConfig.written);
        }
    }

    interface LoadListener {
        void onUpdate(DLTempConfig dlTempConfig, long size);

        void onError(DLTempConfig dlTempConfig, Error error);
    }
}
