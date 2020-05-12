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

    /**
     * 下载任务 立即执行
     * @param task
     */
    public void submitNow(ITask task){
        immediatelyExecutor.submit(task);
    }

    /**
     * 下载 任务 按顺序 逐个 执行
     * @param task
     */
    public void submit(ITask task){
        sequentialExecutor.submit(task);
    }

}
