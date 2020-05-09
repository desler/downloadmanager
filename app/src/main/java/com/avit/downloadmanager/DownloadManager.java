package com.avit.downloadmanager;

import com.avit.downloadmanager.executor.ImmediatelyExecutor;
import com.avit.downloadmanager.executor.SequentialExecutor;
import com.avit.downloadmanager.task.ITask;

public final class DownloadManager {

    private final static DownloadManager sInstance = new DownloadManager();

    public static DownloadManager getInstance() {
        return sInstance;
    }

    private ImmediatelyExecutor immediatelyExecutor;
    private SequentialExecutor sequentialExecutor;

    private DownloadManager() {
        immediatelyExecutor = new ImmediatelyExecutor();
        sequentialExecutor = new SequentialExecutor();
    }

    public void submitNow(ITask task){
        immediatelyExecutor.submit(task);
    }

    public void submit(ITask task){
        sequentialExecutor.submit(task);
    }

}
