package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;

public class AbstractListener implements TaskListener {

    public final static String TAG = "TaskListener";

    @Override
    public void onStart(DownloadItem item) {
        Log.d(TAG, "onStart: item  = " + item);
    }

    @Override
    public void onCompleted(DownloadItem item) {
        Log.d(TAG, "onCompleted: item = " + item);
    }

    @Override
    public void onUpdateProgress(DownloadItem item, int percent) {
        Log.d(TAG, "onUpdateProgress: item = " + item + ", percent = " + percent);
    }

    @Override
    public void onPause(DownloadItem item, int percent) {
        Log.w(TAG, "onPause: item = " + item + ", percent = " + percent);
    }

    @Override
    public void onError(DownloadItem item, Error error) {
        Log.e(TAG, "onError: item = " + item + ", error = " + error.dump());
    }

    @Override
    public void onStop(DownloadItem item, int reason, String messsage) {
        Log.w(TAG, "onStop: item = " + item + ", reason = " + reason + ", message = " + messsage);
    }

    @Override
    public void onFallback(DownloadItem item, ITask old, ITask fallback) {
        Log.w(TAG, "onFallback: item = " + item + ", old = " + old + ", fallback = " + fallback);
    }
}
