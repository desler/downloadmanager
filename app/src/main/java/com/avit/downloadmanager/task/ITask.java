package com.avit.downloadmanager.task;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.guard.IGuardListener;
import com.avit.downloadmanager.task.retry.RetryConfig;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.util.List;
import java.util.concurrent.Callable;

public interface ITask extends Callable<Boolean>, IGuardListener {

    DownloadItem getDownloadItem();

    TaskListener getTaskListener();

    List<VerifyConfig> getVerifyConfigs();

    void release();

    void start();

    void pause();

    void stop();

    State getState();

    enum State {
        NONE, START, LOADING, PAUSE, ERROR, COMPLETE, RELEASE
    }
}
