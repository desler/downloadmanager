package com.avit.downloadmanager;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.NetworkGuard;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.task.AbstactTask;
import com.avit.downloadmanager.task.SingleThreadTask;
import com.avit.downloadmanager.task.TaskListener;
import com.avit.downloadmanager.task.retry.RetryConfig;
import com.avit.downloadmanager.task.retry.RetryTask;

public class MainActivity extends AppCompatActivity implements TaskListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AbstactTask singleThreadTask = new SingleThreadTask(null)
                .withGuard(NetworkGuard.createNetworkGuard(this), SpaceGuard.createSpaceGuard(this, null))
                .callbackOnMainThread()
                .supportBreakpoint()
                .withListener(this);
        RetryTask retryTask = new RetryTask(singleThreadTask, RetryConfig.create());

        DownloadManager.getInstance().submit(retryTask);
        DownloadManager.getInstance().submitNow(singleThreadTask);

    }

    @Override
    public void onStart(DownloadItem item) {

    }

    @Override
    public void onCompleted(DownloadItem item) {

    }

    @Override
    public void onUpdateProgress(DownloadItem item, int percent) {

    }

    @Override
    public void onPause(DownloadItem item, int percent) {

    }

    @Override
    public void onError(DownloadItem item, Error error) {

    }

    @Override
    public void onStop(DownloadItem item, int reason, String message) {

    }
}
