package com.avit.downloadmanager.executor;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.retry.RetryTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

public final class SequentialExecutor extends AbsExecutor {
    private final static String TAG = "SequentialExecutor";

    private ExecutorService seqExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("SequentialExecutor#" + thread.getId());
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    Log.e(TAG, "uncaughtException: " + t.getName(), e);
                }
            });
            return thread;
        }
    });

    private FutureTask<Boolean> futureTask;

    public SequentialExecutor() {
    }

    public SequentialExecutor submit(ITask task) {
        futureTask = (FutureTask<Boolean>) seqExecutor.submit(task);
        return this;
    }

    @Override
    public Boolean call() throws Exception {
        return null;
    }
}
