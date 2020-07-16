package com.avit.downloadmanager.executor;

import android.util.Log;

import androidx.core.util.Pair;

import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.PauseExecute;

import java.util.concurrent.FutureTask;

/**
 * 立即 执行 当前 任务
 */
public final class ImmediatelyExecutor extends AbsExecutor {

    private final static String TAG = "SequentialExecutor";

    @Override
    public ImmediatelyExecutor submit(ITask task) {
        final FutureTask<Boolean> futureTask = new FutureTask<>(task);

        task.setExecutor(this);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    futureTask.run();
                } catch (PauseExecute pauseExecute) {
                    Log.w(TAG, "call: " + pauseExecute.getMessage());
                } catch (Throwable e) {
                    Log.e(TAG, "run: ", e);
                }
            }
        }).start();
        putIfAbsent(task.getDownloadItem().getKey(), Pair.create(task, futureTask));
        return this;
    }
}
