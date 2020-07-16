package com.avit.downloadmanager.executor;

import androidx.core.util.Pair;

import com.avit.downloadmanager.task.ITask;

import java.util.concurrent.FutureTask;

/**
 * 立即 执行 当前 任务
 */
public final class ImmediatelyExecutor extends AbsExecutor {

    public ImmediatelyExecutor() {
    }

    public ImmediatelyExecutor submit(ITask task) {
        FutureTask<Boolean> futureTask = new FutureTask<>(task);
        new Thread(futureTask).start();
        putIfAbsent(task.getDownloadItem().getKey(), Pair.create(task, futureTask));
        return this;
    }
}
