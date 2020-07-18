package com.avit.downloadmanager.task.retry;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.executor.AbsExecutor;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.SystemGuard;
import com.avit.downloadmanager.task.AbstactTask;
import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.TaskListener;
import com.avit.downloadmanager.task.exception.FallbackException;
import com.avit.downloadmanager.task.exception.PauseExecute;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    private AbsExecutor absExecutor;
    private Future<Boolean> futureTask;

    private ITask orgTask;
    private ITask shadowTask;
    private final TaskListener orgTaskListener;
    private final RetryConfig retryConfig;

    private int retryTimes;
    private long step;

    public RetryTask(ITask task){
        this(task, RetryConfig.create());
    }

    public RetryTask(ITask task, RetryConfig retryConfig) {
        this.orgTask = task;
        this.retryConfig = retryConfig;

        AbstactTask ts = (AbstactTask) this.orgTask;
        this.orgTaskListener = ts.getTaskListener();

        ts.withListener(new ProxyTaskListener());
        ts.setParent(this);

        this.shadowTask = orgTask;
    }

    @Override
    public Boolean call() {

        if (retryConfig == null || !retryConfig.isRetry() || retryConfig.getRetryCount() == 0) {

            long begin = System.currentTimeMillis();

            try {
                Boolean result = shadowTask.call();
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

        /**
         * 暂停状态下 这里的 shadow task 并未被释放，
         * resume 重新提交以后，shadow task 需要 释放资源
         */
        if (shadowTask != null && shadowTask != orgTask ) {
            shadowTask.release();
        }

        Boolean result = Boolean.FALSE;

        for (int i = 0; i < retryConfig.getRetryCount(); ++i) {

            long delayed = retryConfig.getBaseDelayed() + (i % retryConfig.getRepeatStepTimes()) * step;
            String message = "";

            --retryTimes;

            this.shadowTask = this.orgTask.clone();
            this.futureTask = retryService.schedule(shadowTask, delayed, TimeUnit.MILLISECONDS);
            long begin = System.currentTimeMillis();

            /**
             * here, will be blocked.
             */
            try {
                result = this.futureTask.get();
                if (result != null && result.booleanValue()) {
                    break;
                }
            } catch (ExecutionException ex) {
                if (!executionExceptionParse(ex)) {
                    Log.e(TAG, "call ExecutionException: ", ex);
                    message = ex.getMessage();
                }
            } catch (Throwable e) {
                Log.w(TAG, "call Throwable: ", e);
                message = e.getMessage();
            }
            Log.d(TAG, "call: cost = " + (System.currentTimeMillis() - begin));

            if (retryConfig.getRetryListener() != null) {
                retryConfig.getRetryListener().onRetry(shadowTask.getDownloadItem(), i + 1, -1, message);
            }
        }

        if (retryTimes <= 0) {
            Log.e(TAG, "call: retry too many times > " + retryConfig.getRetryCount());
        } else {
            Log.w(TAG, "call: retry success");
        }
        release();

        return (result == null ? Boolean.FALSE : result);
    }

    private boolean executionExceptionParse(ExecutionException ex) {

        Throwable throwable = ex.getCause();
        if (throwable == null)
            return false;

        if (throwable instanceof PauseExecute) {
            Log.w(TAG, "call: " + throwable.getMessage());
            throw (PauseExecute) throwable;
        } else if (throwable instanceof FallbackException) {
            Log.w(TAG, "call: " + throwable.getMessage());
            ITask ft = shadowTask.fallback();
            if (ft != null && ft != shadowTask) {

                shadowTask.release();

                if (ft instanceof AbstactTask) {
                    AbstactTask at = (AbstactTask) ft;
                    at.withGuard(orgTask.getSystemGuards().toArray(new SystemGuard[0]));
                }
                orgTask.release();
                shadowTask = orgTask = ft;

                Log.w(TAG, "call: will fall back to singleTask = " + ft);
                retryTimes = retryConfig.getRetryCount();
                return true;
            } else {
                release();
                throw (FallbackException) throwable;
            }
        }

        return false;
    }

    @Override
    public DownloadItem getDownloadItem() {
        return orgTask.getDownloadItem();
    }

    @Override
    public TaskListener getTaskListener() {
        return orgTaskListener;
    }

    @Override
    public List<VerifyConfig> getVerifyConfigs() {
        return orgTask.getVerifyConfigs();
    }

    @Override
    public List<SystemGuard> getSystemGuards() {
        return orgTask.getSystemGuards();
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

        if (futureTask != null){
            futureTask.cancel(true);
        }

        orgTask.release();
        if (shadowTask != null) {
            shadowTask.release();
        }

        retryService.shutdownNow();
        Log.w(TAG, "release: " + this);
    }

    @Override
    public void start() {
        shadowTask.start();
    }

    @Override
    public void stop() {
        shadowTask.stop();
    }

    @Override
    public State getState() {
        return shadowTask.getState();
    }

    @Override
    public ITask fallback() {
        throw new IllegalStateException("DO NOT support this call!");
    }

    @Override
    public boolean hasParent() {
        throw new IllegalStateException("DO NOT support this call!");
    }

    @Override
    public ITask getParent() {
        throw new IllegalStateException("DO NOT support this call!");
    }

    @Override
    public ITask clone() {
        throw new IllegalStateException("DO NOT support this call!");
    }

    @Override
    public void setExecutor(AbsExecutor executor) {
        this.absExecutor = executor;
    }

    @Override
    public void pause() {
        shadowTask.pause();
    }

    @Override
    public void resume() {

        State state = shadowTask.getState();
        if (!isValidState(state)) {
            return;
        }

        if (state == State.PAUSE) {
            this.absExecutor.submit(this);
            return;
        } else {
            Log.w(TAG, "resume: state = " + state.name());
        }
    }

    private boolean isValidState(State state) {
        if (state == State.RELEASE || state == State.ERROR)
            throw new IllegalStateException("" + this + " already " + state.name());

        return true;
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
                Log.w(TAG, "onError: intercept by ProxyTaskListener " + error);
                error.dump();
            }
        }

        @Override
        public void onStop(DownloadItem item, int reason, String message) {
            orgTaskListener.onStop(item, reason, message);
        }

        @Override
        public void onFallback(DownloadItem item, ITask old, ITask fallback) {
            Log.w(TAG, "onFallback: old = " + old +", fallback = " + fallback);
        }
    }
}
