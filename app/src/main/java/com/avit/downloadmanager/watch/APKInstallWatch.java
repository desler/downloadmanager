package com.avit.downloadmanager.watch;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("utils")
public final class APKInstallWatch extends HandlerThread {

    private final static String TAG = "APKInstallWatch";

    private final static int STOP_COUNT = 5;

    private final Context context;
    private final PackageManager packageManager;
    private final ArrayList<String> packages;

    private Handler watchHandler;
    private volatile boolean isRelease;

    private ScheduledFuture watchFuture;
    private ScheduledExecutorService watchScheduled = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("APKInstallWatch#" + thread.getId());
            return thread;
        }
    });

    private int stopWatchCount;

    public APKInstallWatch(Context context) {
        super("APKInstallWatch");
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.packages = new ArrayList<>();
        start();
    }

    public APKInstallWatch addApk(String apkPath) {
        synchronized (packages) {
            this.packages.add(apkPath);
        }

        watch();

        Log.d(TAG, "addApk: " + apkPath);
        return this;
    }

    public APKInstallWatch watch() {

        if (watchFuture != null) {
            Log.w(TAG, "watch: already");
            return this;
        }

        if (watchHandler == null) {
            watchHandler = new Handler(getLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    if (msg.what == 0x10001) {
                        String file = (String) msg.obj;
                        File apk = new File(file);
                        if (apk.exists() && apk.isFile()) {
                            Log.d(TAG, "handleMessage: delete " + apk.delete() + " > " + apk.getPath());
                        }
                    }
                }
            };
        }

        watchFuture = watchScheduled.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {

                Log.d(TAG, "run: watch");

                if (packages.isEmpty()) {
                    Log.w(TAG, "run: nothing to watch");
                    ++stopWatchCount;

                    if (stopWatchCount >= STOP_COUNT) {
                        stopWatch();
                        stopWatchCount = 0;
                    }

                    return;
                }

                synchronized (packages) {
                    Iterator<String> iterator = packages.iterator();
                    while (iterator.hasNext() && !isRelease) {
                        try {
                            String pkgPath = iterator.next();

                            Log.d(TAG, "run: take path = " + pkgPath);

                            if (isInstalledCurrent(getPackageName(pkgPath))) {
                                iterator.remove();
                                Log.d(TAG, "run: already installed > " + pkgPath);

                                Message msg = watchHandler.obtainMessage(0x10001);
                                msg.obj = pkgPath;

                                watchHandler.sendMessageDelayed(msg, 3 * 1000);
                            }
                        } catch (Throwable e) {
                            Log.w(TAG, "run: " + e);
                        }
                    }
                    packages.trimToSize();
                }
            }
        }, 2, 5, TimeUnit.SECONDS);

        return this;
    }


    private void stopWatch() {
        if (watchFuture != null) {
            watchFuture.cancel(true);
            watchFuture = null;
        }
    }

    public void release() {
        isRelease = true;

        stopWatch();

        quitSafely();
    }

    private APKInfo getPackageName(String archiveFilePath) {
        PackageInfo info = packageManager.getPackageArchiveInfo(archiveFilePath, PackageManager.GET_ACTIVITIES);
        ApplicationInfo appInfo = info.applicationInfo;

        APKInfo apkInfo = new APKInfo();
        apkInfo.packageName = appInfo.packageName;
        apkInfo.appName = packageManager.getApplicationLabel(appInfo).toString();

        apkInfo.versionName = info.versionName;
        apkInfo.versionCode = info.versionCode;

        Log.d(TAG, "getPackageName: path = " + archiveFilePath);
        Log.d(TAG, "getPackageName: apk information = " + apkInfo);

        return apkInfo;
    }

    private boolean isInstalledCurrent(APKInfo apkInfo) {
        if (TextUtils.isEmpty(apkInfo.packageName)) {
            Log.w(TAG, "isInstalledCurrent: is empty");
            return false;
        }

        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(apkInfo.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            PackageInfo packageInfo = packageManager.getPackageInfo(apkInfo.packageName, 0);
            if (apkInfo.packageName.equals(appInfo.packageName)
                    && apkInfo.versionCode == packageInfo.versionCode
                    && apkInfo.versionName.equals(packageInfo.versionName)) {
                return true;
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "isInstalledCurrent: " + e);
        }
        return false;
    }

    private final static class APKInfo {
        String packageName;
        String appName;
        String versionName;
        int versionCode;

        @NonNull
        @Override
        public String toString() {
            return String.format("[appName = %s, \r\npackageName = %s, \r\nversionName = %s, versionCode = %d]", appName, packageName, versionName, versionCode);
        }
    }
}
