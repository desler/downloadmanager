package com.avit.downloadmanager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.NetworkGuard;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.task.AbstactTask;
import com.avit.downloadmanager.task.MultipleRandomTask;
import com.avit.downloadmanager.task.SingleRandomTask;
import com.avit.downloadmanager.task.TaskListener;
import com.avit.downloadmanager.task.retry.RetryTask;
import com.avit.downloadmanager.verify.IVerify;
import com.avit.downloadmanager.verify.VerifyConfig;
import com.avit.downloadmanager.watch.APKInstallWatch;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity implements TaskListener {

    private final static String TAG = "MainActivity";

    private void fillDownloadItem(DownloadItem item) {

        Uri uri = Uri.parse(item.getDlPath());
//        Log.w(TAG, "fillDownloadItem: " + uri.getLastPathSegment());

        String filename = uri.getLastPathSegment();
        item.withSavePath(Environment.getExternalStorageDirectory().getPath())
                .withFilename(filename);
    }

    private void submitDownloadTask(DownloadMock mock) {
        /**
         * 创建 下载需要使用的 数据单元
         */
        DownloadItem downloadItem = mock.item;

        NetworkGuard networkGuard = NetworkGuard.createNetworkGuard(this);
        Log.d(TAG, "submitDownloadTask: networkGuard " + networkGuard);
        SpaceGuard spaceGuard = SpaceGuard.createSpaceGuard(this, downloadItem.getSavePath());
        Log.d(TAG, "submitDownloadTask: spaceGuard " + spaceGuard);

//        AbstactTask singleThreadTask = new MultipleRandomTask(downloadItem)
//        AbstactTask singleThreadTask = new MultipleThreadTask(downloadItem)
        AbstactTask singleThreadTask = new SingleRandomTask(downloadItem)
//        AbstactTask singleThreadTask = new SingleThreadTask(downloadItem)
                /**
                 * 添加 网络 及 磁盘空间 管控
                 */
                .withGuard(networkGuard, spaceGuard)
                /**
                 * 在主线程中，回调 监听
                 */
                .callbackOnMainThread()
                /**
                 * 支持 断点 续传
                 */
                .supportBreakpoint()
                /**
                 * 添加监听
                 */
                .withListener(this)
                /**
                 * 添加下载完成后，对文件的校验，支持 md5 crc32 sha 序列
                 */
                .withVerifyConfig(mock.configs);

//        DownloadManager.getInstance().submit(singleThreadTask);
//        DownloadManager.getInstance().submitNow(singleThreadTask);
        DownloadManager.getInstance().submit(new RetryTask(singleThreadTask));
//        DownloadManager.getInstance().submitNow(new RetryTask(singleThreadTask));
    }

    private DownloadMock[] initMockData() {
        Gson gson = new Gson();
        BufferedReader bufferedReader = null;
        StringBuilder sbd = new StringBuilder();
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(getAssets().open("download_mock.json")));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sbd.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                }
            }
        }

        if (TextUtils.isEmpty(sbd.toString())) {
            Log.w(TAG, "onCreate: mock data is empty");
            return new DownloadMock[0];
        }

        DownloadMock[] downloadMocks = gson.fromJson(sbd.toString(), DownloadMock[].class);
        Log.d(TAG, "onCreate: downloadMocks length = " + downloadMocks.length);

        return downloadMocks;
    }

    private APKInstallWatch apkInstallWatch;

    /**
     * 多线程 进度 刷新问题 还需要优化
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apkInstallWatch = new APKInstallWatch(this);

        SpaceGuard.initFromSystem(this, null);

        DownloadMock[] downloadMocks = initMockData();
        for (DownloadMock mock : downloadMocks) {
            fillDownloadItem(mock.item);
//            if (mock.item.getFilename().startsWith("com.dotemu"))
//                continue;

            if (mock.verifys != null) {
                VerifyConfig[] configs = new VerifyConfig[mock.verifys.length];
                for (int i = 0; i < configs.length; ++i) {
                    configs[i] = mock.verifys[i].toVerifyConfig();
                }
                mock.configs = configs;
            }

            submitDownloadTask(mock);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        apkInstallWatch.release();
        super.onDestroy();
    }

    /**
     * 下载任务准备开始
     *
     * @param item
     */
    @Override
    public void onStart(DownloadItem item) {

    }

    /**
     * 下载完成
     *
     * @param item
     */
    @Override
    public void onCompleted(DownloadItem item) {
        Log.d(TAG, "onCompleted: " + item.getFilename());

//        if (item.getFilename().toLowerCase().endsWith(".apk")) {
//            Log.d(TAG, "onCompleted: will install " + item.getFilename());
//            File apkFile = new File(item.getSavePath() + File.separator + item.getFilename());
//            Intent intent = new Intent(Intent.ACTION_VIEW);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
//            startActivity(intent);
//
//            apkInstallWatch.addApk(apkFile.getPath());
//        }
    }

    /**
     * 进度更新， 0 <= percent <= 100
     *
     * @param item
     * @param percent
     */
    @Override
    public void onUpdateProgress(DownloadItem item, int percent) {
        if (percent % 5 == 0) {
            Log.d(TAG, "onUpdateProgress: " + item.getFilename() + " -> " + percent);
        }
    }

    /**
     * 暂停回调
     *
     * @param item
     * @param percent
     */
    @Override
    public void onPause(DownloadItem item, int percent) {

    }

    /**
     * 错误回调
     *
     * @param item
     * @param error
     */
    @Override
    public void onError(DownloadItem item, Error error) {

    }

    /**
     * 主动停止的回调
     *
     * @param item
     * @param reason
     * @param message
     */
    @Override
    public void onStop(DownloadItem item, int reason, String message) {

    }
}

class DownloadMock {
    DownloadItem item;
    VerifyConfigS[] verifys;
    VerifyConfig[] configs;
}

class VerifyConfigS {
    String type;
    String value;

    VerifyConfig toVerifyConfig() {
        String t = type.trim().toUpperCase();
        if (t.equals("MD5")) {
            return VerifyConfig.create(IVerify.VerifyType.MD5, value);
        }

        if (t.equals("CRC32")) {
            return VerifyConfig.create(IVerify.VerifyType.CRC32, value);
        }

        if (t.startsWith("SHA")) {
            return VerifyConfig.create(IVerify.VerifyType.SHA.withSubType(t), value);
        }

        throw new IllegalArgumentException("DO NOT support this algorithm -> " + t);
    }
}