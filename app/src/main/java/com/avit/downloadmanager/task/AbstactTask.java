package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.task.retry.RetryConfig;
import com.avit.downloadmanager.verify.IVerify;
import com.avit.downloadmanager.verify.VerifyCheck;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstactTask implements ITask {

    protected String TAG = "AbstactTask";

    protected DownloadItem downloadItem;
    protected TaskListener taskListener;
    protected RetryConfig retryConfig;
    protected List<VerifyConfig> verifyConfigs;
    protected boolean supportBreakpoint;

    protected SpaceGuard spaceGuard;

    protected State state;

    public AbstactTask(DownloadItem downloadItem) {
        TAG = getClass().getSimpleName();

        this.retryConfig = RetryConfig.create();
        this.verifyConfigs = new ArrayList<>(1);

        this.downloadItem = downloadItem;
    }

    public AbstactTask withListener(TaskListener listener) {
        taskListener = listener;
        return this;
    }

    public AbstactTask withRetryConfig(RetryConfig config) {
        retryConfig = config;
        return this;
    }

    public AbstactTask withVerifyConfig(VerifyConfig... verifyConfigs) {
        if (verifyConfigs == null || verifyConfigs.length <= 0)
            return this;

        for (VerifyConfig verifyConfig : verifyConfigs) {
            this.verifyConfigs.add(verifyConfig);
        }
        return this;
    }

    public AbstactTask withSpaceGuard(SpaceGuard spaceGuard) {
        this.spaceGuard = spaceGuard;
        return this;
    }

    public AbstactTask supportBreakpoint() {
        supportBreakpoint = true;
        return this;
    }


    public RetryConfig getRetryConfig() {
        return retryConfig;
    }

    public DownloadItem getDownloadItem() {
        return downloadItem;
    }

    public TaskListener getTaskListener() {
        return taskListener;
    }

    public List<VerifyConfig> getVerifyConfigs() {
        return verifyConfigs;
    }

    @Override
    public final Boolean call() throws Exception {

        if (isValidState() && !onStart()) {
            return Boolean.FALSE;
        }

        if (isValidState() && !onDownload()) {
            return Boolean.FALSE;
        }

        if (isValidState() && !onVerify()) {
            return Boolean.FALSE;
        }

        if (isValidState() && taskListener != null) {
            taskListener.onCompleted(downloadItem);
        }

        return Boolean.TRUE;
    }

    protected abstract boolean onStart();

    protected abstract boolean onDownload();

    protected boolean onVerify() {

        if (verifyConfigs.isEmpty()) {
            Log.w(TAG, "onVerify: verify Config NOT FOUND, always valid!");
            return true;
        }

        String itemPath = downloadItem.getSavePath() + File.pathSeparator + downloadItem.getFilename();
        File file = new File(itemPath);
        if (!file.exists()) {
            Log.e(TAG, "onVerify: " + itemPath + " not exists.");
            return false;
        }

        IVerify verify = VerifyCheck.createVerify(file);

        for (VerifyConfig config : verifyConfigs) {
            if (!verify.verify(config)) {
                Log.e(TAG, "onVerify: check " + config + ", invalid");
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean onGuardEvent(GuardEvent guardEvent) {
        return false;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void start() {
        State state = getState();
        if (state == State.PAUSE) {
            this.state = State.START;
            return;
        } else {
            Log.w(TAG, "start: state = " + state.name());
        }
    }

    @Override
    public void pause() {
        State state = getState();
        if (state == State.START || state == State.LOADING) {
            this.state = State.PAUSE;
            return;
        } else {
            Log.w(TAG, "pause: state = " + state.name());
        }
    }

    @Override
    public void stop() {
        release();
    }

    @Override
    public void release() {
        this.state = State.RELEASE;
    }

    private boolean isValidState(){
        if (state == State.RELEASE)
            throw new IllegalStateException(""+this + " already release");

        return true;
    }
}
