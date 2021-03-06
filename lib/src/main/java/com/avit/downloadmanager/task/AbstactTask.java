package com.avit.downloadmanager.task;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.avit.downloadmanager.data.BreakPointHelper;
import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.executor.AbsExecutor;
import com.avit.downloadmanager.guard.GuardEvent;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.guard.SystemGuard;
import com.avit.downloadmanager.task.exception.PauseExecute;
import com.avit.downloadmanager.task.exception.TaskException;
import com.avit.downloadmanager.verify.IVerify;
import com.avit.downloadmanager.verify.VerifyCheck;
import com.avit.downloadmanager.verify.VerifyConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstactTask<TASK extends AbstactTask> implements ITask {

    protected String TAG = "AbstactTask";

    final static String pathFormat = "%s" + File.separator + "%s";

    private AbsExecutor absExecutor;

    protected final DownloadItem downloadItem;
    protected TaskListener taskListener;

    protected final List<VerifyConfig> verifyConfigs;
    protected final List<SystemGuard> systemGuards;
    /**
     * 关于 磁盘 空间大小的 guard，需要单独拎出来，因为 需要实时监控 磁盘空间大小。
     */
    protected SpaceGuard spaceGuard;

    protected boolean supportBreakpoint;
    protected final BreakPointHelper breakPointHelper;

    protected boolean callbackOnMainThread;

    protected State state;

    private ITask parent;

    public AbstactTask(DownloadItem downloadItem) {
        this.verifyConfigs = new ArrayList<>(1);
        this.systemGuards = new ArrayList<>(2);

        this.downloadItem = downloadItem;
        this.taskListener = EventDispatcher.DUMMY;

        this.breakPointHelper = new BreakPointHelper();

        this.state = State.NONE;
    }

    protected AbsExecutor getAbsExecutor() {
        return absExecutor;
    }

    @Override
    public void setExecutor(AbsExecutor executor) {
        this.absExecutor = executor;
    }

    public final void submitSelf(){
        this.absExecutor.submit(this);
    }

    public final void submit(ITask task){
        this.absExecutor.submit(task);
    }

    public TASK withListener(TaskListener listener) {

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
        return (TASK) this;
    }

    public TASK withVerifyConfig(VerifyConfig... verifyConfigs) {
        if (verifyConfigs == null || verifyConfigs.length <= 0)
            return (TASK) this;

        for (VerifyConfig verifyConfig : verifyConfigs) {
            this.verifyConfigs.add(verifyConfig);
        }
        return (TASK) this;
    }

    public TASK withGuard(SystemGuard... systemGuards) {
        if (systemGuards == null || systemGuards.length == 0) {
            Log.w(TAG, "withGuard: is nothing");
            return (TASK) this;
        }

        for (SystemGuard guard : systemGuards) {
            guard.addGuardListener(this);
            this.systemGuards.add(guard);
            guard.guard();

            if (guard instanceof SpaceGuard) {
                this.spaceGuard = (SpaceGuard) guard;
                Log.d(TAG, "withGuard: add a spaceGuard = " + spaceGuard);
            }
        }

        return (TASK) this;
    }

    public void setParent(ITask parent) {
        this.parent = parent;
    }

    @Override
    public ITask getParent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    public TASK supportBreakpoint() {
        supportBreakpoint = true;
        return (TASK) this;
    }

    public List<SystemGuard> getSystemGuards() {
        return systemGuards;
    }

    /**
     * 如果需要在主线程中执行 callback，则需要在 withListener 之前调用此函数
     *
     * @return
     */
    public TASK callbackOnMainThread() {
        callbackOnMainThread = true;
        return (TASK) this;
    }

    public DownloadItem getDownloadItem() {
        return downloadItem;
    }

    public TaskListener getTaskListener() {
        return ((EventDispatcher) taskListener).taskListener;
    }

    public List<VerifyConfig> getVerifyConfigs() {
        return verifyConfigs;
    }

    private boolean checkAndVerify(File file) {
        if (file.exists() && file.isFile()) {
            if (isValidState() && onVerify()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final Boolean call() throws Exception {

        Log.d(TAG, "call: save file path = " + downloadItem.getSavePath());

        /**
         * 如果文件已经存在，则 直接 校验 不需要 进行 实际的下载
         */
        if (!TextUtils.isEmpty(downloadItem.getFilename())) {
            String filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
            if (checkAndVerify(new File(filePath))) {
                Log.w(TAG, "call: init check already exist > " + downloadItem.getFilename());
                if (taskListener != null) {
                    taskListener.onCompleted(downloadItem);
                }
                release();
                return Boolean.TRUE;
            }
        }

        if (isValidState() && !onStart()) {
            release();
            return Boolean.FALSE;
        }

        /**
         * 有时候 文件名字 需要从 连接的 head 属性中获取，因此在这里做 二次校验
         */
        String filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());

        if (checkAndVerify(new File(filePath))) {
            Log.w(TAG, "call: after start already exist > " + downloadItem.getFilename());
            if (taskListener != null) {
                taskListener.onCompleted(downloadItem);
            }
            release();
            return Boolean.TRUE;
        }


        state = State.START;

        boolean isPause = false;
        try {
            if (isValidState() && !onDownload()) {
                return Boolean.FALSE;
            }

            if (isValidState() && !onVerify()) {
                taskListener.onError(downloadItem, new Error(Error.Type.ERROR_DATA.value(), "check verify invalid"));
                return Boolean.FALSE;
            }

            state = State.COMPLETE;
            if (taskListener != null) {
                taskListener.onCompleted(downloadItem);
            }
        }
        /**
         * 如果 是 pause 任务，则 资源不释放 等待 下一次提交
         */
        catch (PauseExecute ex) {
            isPause = true;
            throw ex;
        } finally {
            if (!isPause) {
                release();
            }
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

        String itemPath = downloadItem.getSavePath() + File.separator + downloadItem.getFilename();
        File file = new File(itemPath);
        if (!file.exists() || !file.isFile()) {
            Log.e(TAG, "onVerify: " + itemPath + " not exists.");
            return false;
        }

        IVerify verify = VerifyCheck.createVerify(file);

        for (VerifyConfig config : verifyConfigs) {
            if (!verify.verify(config)) {
                Log.e(TAG, "onVerify: check " + config + ", invalid");
                Log.e(TAG, "onVerify: failed!, delete " + file.delete() + " > " + file.getPath());
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
//        State state = getState();
//        if (state == State.PAUSE) {
//            this.state = State.START;
//            return;
//        } else {
            Log.w(TAG, "start: state = " + state.name());
//        }
    }

    @Override
    public void resume() {
        if (!isValidState()) {
            return;
        }
        State state = getState();
        if (state == State.PAUSE) {
            this.state = State.START;
            submitSelf();
            return;
        } else {
            Log.w(TAG, "resume: state = " + state.name());
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
    public ITask fallback() {
        return this;
    }

    private void releaseGuard() {
        for (SystemGuard guard : systemGuards) {
            guard.removeGuardListener(this);
        }
        systemGuards.clear();
    }

    @Override
    public void release() {

        Log.w(TAG, "release: " + this);

        this.state = State.RELEASE;
        releaseGuard();
    }

    public final boolean isValidState() {
        if (state == State.RELEASE || state == State.ERROR)
            throw new TaskException("" + this + " already " + state.name());

        return true;
    }

    @Override
    public ITask clone() {
        return null;
    }

    private static class EventDispatcher extends Handler implements TaskListener {

        private final static String TAG = "EventDispatcher";

        private final static EventDispatcher DUMMY = new EventDispatcher(Looper.getMainLooper());

        public final static int MSG_TASK_START = 1001;
        public final static int MSG_TASK_UPDATE = 1002;
        public final static int MSG_TASK_COMPLETE = 1003;
        public final static int MSG_TASK_PAUSE = 1004;
        public final static int MSG_TASK_STOP = 1005;
        public final static int MSG_TASK_FALLBACK = 1006;
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
                    Pair<String, DownloadItem> stop = (Pair<String, DownloadItem>) msg.obj;
                    taskListener.onStop(stop.second, msg.arg1, stop.first);
                    break;
                case MSG_TASK_FALLBACK:
                    Pair<Pair<ITask, ITask>, DownloadItem> fallback = (Pair<Pair<ITask, ITask>, DownloadItem>) msg.obj;
                    taskListener.onFallback(fallback.second, fallback.first.first, fallback.first.second);
                    break;
                case MSG_TASK_ERROR:
                    Pair<Error, DownloadItem> error = (Pair<Error, DownloadItem>) msg.obj;
                    taskListener.onError(error.second, error.first);
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
            msg.obj = Pair.create(error, item);

            Bundle bundle = new Bundle();
            bundle.putSerializable("error", error);
            msg.setData(bundle);

            msg.sendToTarget();
        }

        @Override
        public void onStop(DownloadItem item, int reason, String message) {
            Message msg = obtainMessage(MSG_TASK_STOP);
            msg.obj = Pair.create(message, item);
            msg.arg1 = reason;
            msg.sendToTarget();
        }

        @Override
        public void onFallback(DownloadItem item, ITask old, ITask fallback) {
            Message msg = obtainMessage(MSG_TASK_FALLBACK);
            msg.obj = Pair.create(Pair.create(old, fallback), item);
            msg.sendToTarget();
        }
    }

    static final long B = 1;
    static final long K = 1024 * B;
    static final long M = 1024 * K;
    static final long G = 1024 * M;
    static final long P = 1024 * G;

    static String size2String(long size) {
        if (size > G) {
            return String.format("%.2fGB", size * 1.0f / G);
        }
        if (size > M) {
            return String.format("%.2fMB", size * 1.0f / M);
        }
        if (size > K) {
            return String.format("%.2fKB", size * 1.0f / K);
        }

        return String.format("%dB", size);
    }
}
