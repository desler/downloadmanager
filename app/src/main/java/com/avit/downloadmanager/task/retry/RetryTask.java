package com.avit.downloadmanager.task.retry;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.TaskListener;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class RetryTask implements ITask {

    private final static String TAG = "RetryTask";

    private ScheduledExecutorService retryService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
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

    private ITask task;
    private RetryConfig retryConfig;

    private int retryTimes;
    private long step;

    public RetryTask(ITask task) {
        this.task = task;
        this.retryConfig = task.getRetryConfig();

        this.retryTimes = this.retryConfig.getRetryCount();
    }

    @Override
    public Boolean call() {

        if (retryConfig == null || !retryConfig.isRetry()) {

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

        if (retryConfig.isStableDelayed()) {
            step = 0;
        } else {
            step = retryConfig.getStep();
        }

        Boolean result = Boolean.FALSE;

        for (int i = 0; i < retryConfig.getRetryCount(); ++i) {

            long delayed = retryConfig.getBaseDelayed() + (i % retryConfig.getRepeatStepTimes()) * step;
            String message = "";

            FutureTask<Boolean> futureTask = (FutureTask<Boolean>) retryService.schedule(task, delayed, TimeUnit.MILLISECONDS);
            long begin = System.currentTimeMillis();
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

            --retryTimes;
        }

        if (retryTimes <= 0) {
            Log.e(TAG, "call: retry too many times > " + retryConfig.getRetryCount());
        }

        return (result == null ? Boolean.FALSE : result);
    }

    @Override
    public RetryConfig getRetryConfig() {
        return task.getRetryConfig();
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
    public void pause() {
        task.pause();
    }

    @Override
    public boolean onGuardEvent(GuardEvent guardEvent) {
        return false;
    }
}
