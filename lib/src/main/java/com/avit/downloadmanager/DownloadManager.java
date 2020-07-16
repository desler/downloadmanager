package com.avit.downloadmanager;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.executor.ImmediatelyExecutor;
import com.avit.downloadmanager.executor.SequentialExecutor;
import com.avit.downloadmanager.task.ITask;
import com.avit.downloadmanager.utils.Assertions;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class DownloadManager {

    private final static String TAG = "DownloadManager";

    private final static DownloadManager sInstance = new DownloadManager();
    public static DownloadManager getInstance() {
        return sInstance;
    }

    public static DownloadManager newInstance() {
        return new DownloadManager();
    }

    private ImmediatelyExecutor immediatelyExecutor;
    private SequentialExecutor sequentialExecutor;

    private TaskManager taskManager;

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

    public DownloadManager enableTaskManager() {
        if (taskManager == null) {
            taskManager = new DownloadManager.TaskManager(this);
        }
        taskManager.enable();

        return this;
    }

    public DownloadManager disableTaskManager() {

        if (taskManager != null) {
            taskManager.release();
            taskManager = null;
        }

        return this;
    }

    public boolean isFinish(ITask task) {
        Assertions.checkState(taskManager != null, " taskManager is null, MUST enable taskManager first.");
        return taskManager.isFinish(task);
    }

    public ITask.State getTaskState(ITask task){
        Assertions.checkState(taskManager != null, " taskManager is null, MUST enable taskManager first.");
        return taskManager.getTaskState(task);
    }

    public final boolean isExist(ITask task){
        Assertions.checkState(taskManager != null, " taskManager is null, MUST enable taskManager first.");
        return taskManager.isExist(task);
    }

    public void release() {
        disableTaskManager();
        immediatelyExecutor.release();
        sequentialExecutor.release();
    }

    static final class TaskManager {

        private DownloadManager downloadManager;
        private ScheduledExecutorService scheduledExecutorService;
        private boolean isStartCheck = false;

        public TaskManager(DownloadManager downloadManager) {
            this.downloadManager = downloadManager;
        }

        public void enable() {
            if (isStartCheck)
                return ;

            scheduledExecutorService = createScheduledCheck();
            checkTask();
        }

        public void disable() {
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
                scheduledExecutorService = null;
            }
            isStartCheck = false;
        }

        private ScheduledExecutorService createScheduledCheck(){
            return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("DWM_CHECK#" + thread.getId());
                    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                            Log.e(TAG, "uncaughtException: " + t.getName(), e);
                        }
                    });
                    return thread;
                }
            });
        }

        private void checkTask() {
            if (!isStartCheck) {
                isStartCheck = true;
                scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            downloadManager.sequentialExecutor.call();
                        } catch (Exception e) {
                            Log.e(TAG, "run: ", e);
                        }
                        try {
                            downloadManager.immediatelyExecutor.call();
                        } catch (Exception e) {
                            Log.e(TAG, "run: ", e);
                        }
                    }
                }, 100, 1000, TimeUnit.MILLISECONDS);
            } else {
                Log.w(TAG, "checkTask: already start");
            }
        }

        public boolean isFinish(ITask task) {

            if (downloadManager.immediatelyExecutor.isExist(task)) {
                return downloadManager.immediatelyExecutor.isFinish(task);
            }

            if (downloadManager.sequentialExecutor.isExist(task)) {
                return downloadManager.sequentialExecutor.isFinish(task);
            }

            return task.getState() == ITask.State.COMPLETE
                    || task.getState() == ITask.State.ERROR
                    || task.getState() == ITask.State.RELEASE;
        }

        public ITask.State getTaskState(ITask task){

            if (downloadManager.immediatelyExecutor.isExist(task)){
                return downloadManager.immediatelyExecutor.getTaskState(task);
            }

            if (downloadManager.sequentialExecutor.isExist(task)){
                return downloadManager.sequentialExecutor.getTaskState(task);
            }

            return task.getState();
        }

        public final boolean isExist(ITask task){
            return downloadManager.immediatelyExecutor.isExist(task) || downloadManager.sequentialExecutor.isExist(task);
        }

        public void release() {
            disable();
        }
    }
}
