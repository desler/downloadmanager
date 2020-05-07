package com.avit.downloadmanager.executor;

import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.task.retry.RetryTask;

public final class ImmediatelyExecutor extends AbsExecutor {

    public ImmediatelyExecutor(ITask... tasks){

    }

    public ImmediatelyExecutor(RetryTask... executors){

    }

    public ImmediatelyExecutor submit(ITask task){
        submit(new RetryTask(task));
        return this;
    }

    public ImmediatelyExecutor submit(RetryTask executor){
        return this;
    }

    @Override
    public Boolean call() throws Exception {
        return null;
    }
}
