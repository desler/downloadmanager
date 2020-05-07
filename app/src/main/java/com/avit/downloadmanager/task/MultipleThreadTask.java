package com.avit.downloadmanager.task;

import android.util.Log;

import androidx.annotation.NonNull;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class MultipleThreadTask extends AbstactTask {

    private static int MAX_THREADS = 4;
    private static int ACTIVE_SIZE = 10 * 1024 * 1024; // 10M

    private ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("MultipleThreadTask#"+thread.getId());
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    Log.e(TAG, "uncaughtException: " + t.getName(), e);
                }
            });
            return thread;
        }
    });

    protected MultipleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
    }

    @Override
    protected boolean onStart() {
        return false;
    }

    @Override
    protected boolean onDownload() {


        if (!mergeFiles()){
            return false;
        }

        return false;
    }


    @Override
    public void release() {

    }

    private boolean mergeFiles(File... files){

        return false;
    }

    static  class SingleTask extends AbstactTask{

        private DLTempConfig dlTempConfig;

        protected SingleTask(DownloadItem downloadItem) {
            super(downloadItem);
        }

        SingleTask withDLTempConfig(DLTempConfig config){
            this.dlTempConfig = config;
            return this;
        }

        @Override
        protected boolean onDownload() {
            return false;
        }

        @Override
        protected boolean onStart() {
            return true;
        }

        @Override
        public void release() {

        }
    }
}
