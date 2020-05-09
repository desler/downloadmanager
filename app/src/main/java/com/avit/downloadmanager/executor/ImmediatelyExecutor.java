package com.avit.downloadmanager.executor;

import com.avit.downloadmanager.task.ITask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

/**
 * 立即 执行 当前 任务
 */
public final class ImmediatelyExecutor extends AbsExecutor {

    private List<FutureTask<Boolean>> futureTasks;

    public ImmediatelyExecutor() {
        futureTasks = new ArrayList<>();
    }

    public ImmediatelyExecutor submit(ITask task) {
        FutureTask<Boolean> futureTask = new FutureTask<>(task);
        new Thread(futureTask).start();
        futureTasks.add(futureTask);
        return this;
    }

    @Override
    public Boolean call() throws Exception {
        return false;
    }
}
