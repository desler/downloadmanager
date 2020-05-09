package com.avit.downloadmanager.task;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.guard.SystemGuard;
import com.avit.downloadmanager.task.retry.RetryConfig;
import com.avit.downloadmanager.verify.IVerify;
import com.avit.downloadmanager.verify.VerifyCheck;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstactTask implements ITask {

    protected final String TAG;

    protected DownloadItem downloadItem;
    protected TaskListener taskListener;

    protected List<VerifyConfig> verifyConfigs;
    protected List<SystemGuard> systemGuards;
    /**
     * 关于 磁盘 空间大小的 guard，需要单独拎出来，因为 需要实时监控 磁盘空间大小。
     */
    protected SpaceGuard spaceGuard;

    protected boolean supportBreakpoint;
    protected boolean callbackOnMainThread;

    protected State state;

    public AbstactTask(DownloadItem downloadItem) {
        TAG = getClass().getSimpleName();

        this.verifyConfigs = new ArrayList<>(1);
        this.systemGuards = new ArrayList<>(2);

        this.downloadItem = downloadItem;
        this.taskListener = EventDispatcher.DUMMY;
    }

    public AbstactTask withListener(TaskListener listener) {

        if (this.taskListener == EventDispatcher.DUMMY) {
            /**
             * 默认在当前线程的 looper中执行 task listener,
             * 如果当前线程没有 looper，则 主线程中执行
             */
            Looper looper = Looper.myLooper();
            if (callbackOnMainThread || looper == null) {
                looper = Looper.getMainLooper();
            }
            this.taskListener = new EventDispatcher(looper);
        }

        ((EventDispatcher) this.taskListener).taskListener = listener;
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

    public AbstactTask withGuard(SystemGuard... systemGuards) {
        if (systemGuards == null || systemGuards.length == 0) {
            Log.w(TAG, "withGuard: is nothing");
            return this;
        }

        for (SystemGuard guard : this.systemGuards) {
            guard.addGuardListener(this);
            guard.guard();
            this.systemGuards.add(guard);

            if (guard instanceof SpaceGuard){
                this.spaceGuard = (SpaceGuard) guard;
            }
        }

        return this;
    }

    public AbstactTask supportBreakpoint() {
        supportBreakpoint = true;
        return this;
    }

    /**
     * 如果需要在主线程中执行 callback，则需要在 withListener 之前调用此函数
     *
     * @return
     */
    public AbstactTask callbackOnMainThread() {
        callbackOnMainThread = true;
        return this;
    }

    public DownloadItem getDownloadItem() {
        return downloadItem;
    }

    public TaskListener getTaskListener() {
        return ((EventDispatcher)taskListener).taskListener;
    }

    public List<VerifyConfig> getVerifyConfigs() {
        return verifyConfigs;
    }

    @Override
    public final Boolean call() throws Exception {

        if (isValidState() && !onStart()) {
            return Boolean.FALSE;
        }
        state = State.START;

        if (isValidState() && !onDownload()) {
            return Boolean.FALSE;
        }

        if (isValidState() && !onVerify()) {
            return Boolean.FALSE;
        }

        state = State.COMPLETE;
        if (taskListener != null) {
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
        GuardEvent.dump(guardEvent);
        return false;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void start() {
        if (!isValidState()) {
            return;
        }
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
        if (!isValidState()) {
            return;
        }
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
        if (!isValidState()) {
            return;
        }
        release();
    }

    @Override
    public void release() {
        this.state = State.RELEASE;

        for (SystemGuard guard : systemGuards) {
            guard.removeGuardListener(this);
        }
        systemGuards.clear();
    }

    private boolean isValidState() {
        if (state == State.RELEASE || state == State.ERROR)
            throw new IllegalStateException("" + this + " already " + state.name());

        return true;
    }

    private static class EventDispatcher extends Handler implements TaskListener {

        private final static String TAG = "EventDispatcher";

        private final static EventDispatcher DUMMY = new EventDispatcher(Looper.getMainLooper());

        public final static int MSG_TASK_START = 1001;
        public final static int MSG_TASK_UPDATE = 1002;
        public final static int MSG_TASK_COMPLETE = 1003;
        public final static int MSG_TASK_PAUSE = 1004;
        public final static int MSG_TASK_STOP = 1005;
        public final static int MSG_TASK_ERROR = 1111;

        private TaskListener taskListener;

        private EventDispatcher(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {

            if (msg.what != MSG_TASK_UPDATE) {
                Log.d(TAG, "handleMessage: " + msg);
            }

            if (taskListener == null) {
                Log.w(TAG, "handleMessage: taskListener is null");
                return;
            }

            switch (msg.what) {
                case MSG_TASK_UPDATE:
                    taskListener.onUpdateProgress((DownloadItem) msg.obj, msg.arg1);
                    break;
                case MSG_TASK_START:
                    taskListener.onStart((DownloadItem) msg.obj);
                    break;
                case MSG_TASK_PAUSE:
                    taskListener.onPause((DownloadItem) msg.obj, msg.arg1);
                    break;
                case MSG_TASK_COMPLETE:
                    taskListener.onCompleted((DownloadItem) msg.obj);
                    break;
                case MSG_TASK_STOP:
                    taskListener.onStop((DownloadItem) msg.obj, msg.arg1, msg.getData().getString("message"));
                    break;
                case MSG_TASK_ERROR:
                    taskListener.onError((DownloadItem) msg.obj, (Error) msg.getData().getSerializable("error"));
                    break;
            }
        }

        @Override
        public void onStart(DownloadItem item) {
            Message msg = obtainMessage(MSG_TASK_START);
            msg.obj = item;
            msg.sendToTarget();
        }

        @Override
        public void onCompleted(DownloadItem item) {
            Message msg = obtainMessage(MSG_TASK_COMPLETE);
            msg.obj = item;
            msg.sendToTarget();
        }

        @Override
        public void onUpdateProgress(DownloadItem item, int percent) {
            Message msg = obtainMessage(MSG_TASK_UPDATE);
            msg.obj = item;
            msg.arg1 = percent;
            msg.sendToTarget();
        }

        @Override
        public void onPause(DownloadItem item, int percent) {
            Message msg = obtainMessage(MSG_TASK_PAUSE);
            msg.obj = item;
            msg.arg1 = percent;
            msg.sendToTarget();
        }

        @Override
        public void onError(DownloadItem item, Error error) {
            Message msg = obtainMessage(MSG_TASK_ERROR);
            msg.obj = item;

            Bundle bundle = new Bundle();
            bundle.putSerializable("error", error);
            msg.setData(bundle);

            msg.sendToTarget();
        }

        @Override
        public void onStop(DownloadItem item, int reason, String message) {
            Message msg = obtainMessage(MSG_TASK_STOP);
            msg.obj = item;
            msg.arg1 = reason;

            Bundle bundle = new Bundle();
            bundle.putString("message", message);
            msg.setData(bundle);

            msg.sendToTarget();
        }
    }

}
