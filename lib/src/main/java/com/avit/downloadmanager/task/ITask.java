package com.avit.downloadmanager.task;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.executor.AbsExecutor;
import com.avit.downloadmanager.guard.IGuardListener;
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

    void resume();

    void stop();

    State getState();

    ITask fallback();

    boolean hasParent();

    ITask getParent();

    enum State {
        NONE, START, LOADING, PAUSE, ERROR, COMPLETE, RELEASE
    }

    void setExecutor(AbsExecutor executor);
}
