package com.avit.downloadmanager.task.retry;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.task.AbstactTask;
import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.TaskListener;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class RetryTask implements ITask {

    private final static String TAG = "RetryTask";

    private final ScheduledExecutorService retryService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("RetryExecutor#" + thread.getId());
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    Log.e(TAG, "uncaughtException: " + t.getName(), e);
                }
            });
            return thread;
        }
    });

    private final ITask task;
    private final TaskListener orgTaskListener;
    private final RetryConfig retryConfig;

    private int retryTimes;
    private long step;

    public RetryTask(ITask task){
        this(task, RetryConfig.create());
    }

    public RetryTask(ITask task, RetryConfig retryConfig) {
        this.task = task;
        this.retryConfig = retryConfig;

        AbstactTask ts = (AbstactTask) this.task;
        orgTaskListener = ts.getTaskListener();

        ts.withListener(new ProxyTaskListener());
    }

    @Override
    public Boolean call() {

        if (retryConfig == null || !retryConfig.isRetry() || retryConfig.getRetryCount() == 0) {

            long begin = System.currentTimeMillis();

            try {
                Boolean result = task.call();
                Log.d(TAG, "call: no retry, cost = " + (System.currentTimeMillis() - begin));
                return result == null ? Boolean.FALSE : result;
            } catch (Throwable e) {
                Log.e(TAG, "call: ", e);
            }

            return Boolean.FALSE;
        }

        this.retryTimes = this.retryConfig.getRetryCount();

        if (retryConfig.isStableDelayed()) {
            step = 0;
        } else {
            step = retryConfig.getStep();
        }

        Boolean result = Boolean.FALSE;

        for (int i = 0; i < retryConfig.getRetryCount(); ++i) {

            long delayed = retryConfig.getBaseDelayed() + (i % retryConfig.getRepeatStepTimes()) * step;
            String message = "";

            --retryTimes;

            Future<Boolean> futureTask = retryService.schedule(task, delayed, TimeUnit.MILLISECONDS);
            long begin = System.currentTimeMillis();

            /**
             * here, will be blocked.
             */
            try {
                result = futureTask.get();
                if (result != null && result.booleanValue()) {
                    break;
                }
            } catch (Throwable e) {
                Log.e(TAG, "call: ", e);
                message = e.getMessage();
            }
            Log.d(TAG, "call: cost = " + (System.currentTimeMillis() - begin));

            if (retryConfig.getRetryListener() != null) {
                retryConfig.getRetryListener().onRetry(task.getDownloadItem(), i + 1, -1, message);
            }
        }

        if (retryTimes <= 0) {
            Log.e(TAG, "call: retry too many times > " + retryConfig.getRetryCount());
        }

        return (result == null ? Boolean.FALSE : result);
    }

    @Override
    public DownloadItem getDownloadItem() {
        return task.getDownloadItem();
    }

    @Override
    public TaskListener getTaskListener() {
        return task.getTaskListener();
    }

    @Override
    public List<VerifyConfig> getVerifyConfigs() {
        return task.getVerifyConfigs();
    }

    private boolean isLastRetry() {
        return retryTimes <= 0;
    }

    private boolean isFirstRetry() {

        if (retryConfig == null || !retryConfig.isRetry() || retryConfig.getRetryCount() == 0) {
            return true;
        }

        return retryTimes == (retryConfig.getRetryCount() - 1);
    }


    public void release() {
        task.release();
        retryService.shutdown();
    }

    @Override
    public void start() {
        task.start();
    }

    @Override
    public void stop() {
        task.stop();
    }

    @Override
    public State getState() {
        return task.getState();
    }

    @Override
    public void pause() {
        task.pause();
    }

    @Override
    public boolean onGuardEvent(GuardEvent guardEvent) {
        return false;
    }


    private class ProxyTaskListener implements TaskListener {

        @Override
        public void onStart(DownloadItem item) {
            if (isFirstRetry()) {
                orgTaskListener.onStart(item);
            } else {
                Log.w(TAG, "onStart: intercept by ProxyTaskListener");
            }
        }

        @Override
        public void onCompleted(DownloadItem item) {
            orgTaskListener.onCompleted(item);
        }

        @Override
        public void onUpdateProgress(DownloadItem item, int percent) {
            orgTaskListener.onUpdateProgress(item, percent);
        }

        @Override
        public void onPause(DownloadItem item, int percent) {
            orgTaskListener.onPause(item, percent);
        }

        @Override
        public void onError(DownloadItem item, Error error) {
            if (isLastRetry()) {
                orgTaskListener.onError(item, error);
            } else {
                error.dump();
                Log.w(TAG, "onError: intercept by ProxyTaskListener " + error);
            }
        }

        @Override
        public void onStop(DownloadItem item, int reason, String message) {
            orgTaskListener.onStop(item, reason, message);
        }
    }
}
