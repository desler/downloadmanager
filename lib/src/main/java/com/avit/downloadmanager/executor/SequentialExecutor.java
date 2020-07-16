package com.avit.downloadmanager.executor;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.PauseExecute;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

/**
 * 排队 将 任务 一个个 执行
 */
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

    public SequentialExecutor() {
    }

    @Override
    public SequentialExecutor submit(final ITask task) {

        task.setExecutor(this);

        FutureTask futureTask = (FutureTask<Boolean>) seqExecutor.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    return task.call();
                } catch (PauseExecute pauseExecute) {
                    Log.w(TAG, "call: " + pauseExecute.getMessage());
                } catch (Throwable e) {
                    Log.e(TAG, "call: ", e);
                }
                return Boolean.FALSE;
            }
        });

        putIfAbsent(task.getDownloadItem().getKey(), Pair.<ITask, FutureTask<Boolean>>create(task, futureTask));
        return this;
    }

}
