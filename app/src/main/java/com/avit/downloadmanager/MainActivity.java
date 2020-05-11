package com.avit.downloadmanager;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.avit.downloadmanager.data.DownloadItem;
import com.avit.downloadmanager.error.Error;
import com.avit.downloadmanager.guard.NetworkGuard;
import com.avit.downloadmanager.guard.SpaceGuard;
import com.avit.downloadmanager.task.AbstactTask;
import com.avit.downloadmanager.task.SingleThreadTask;
import com.avit.downloadmanager.task.TaskListener;
import com.avit.downloadmanager.task.retry.RetryConfig;
import com.avit.downloadmanager.task.retry.RetryTask;
import com.avit.downloadmanager.verify.IVerify;
import com.avit.downloadmanager.verify.VerifyConfig;

public class MainActivity extends AppCompatActivity implements TaskListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SpaceGuard.initFromSystem(this, null);

        /**
         * 创建 下载需要使用的 数据单元
         */
        DownloadItem downloadItem = DownloadItem.create();

        /**
         * 创建下载完成以后，需要到的校验，支持多个校验值
         */
        VerifyConfig md5 = VerifyConfig.create(IVerify.VerifyType.MD5, "");
        VerifyConfig crc32 = VerifyConfig.create(IVerify.VerifyType.CRC32, "");
        VerifyConfig sha_1 = VerifyConfig.create(IVerify.VerifyType.SHA, "");
        VerifyConfig sha_256 = VerifyConfig.create(IVerify.VerifyType.SHA.setSubType("SHA-256"), "");

        AbstactTask singleThreadTask = new SingleThreadTask(downloadItem)
                /**
                 * 添加 网络 及 磁盘空间 管控
                 */
                .withGuard(NetworkGuard.createNetworkGuard(this), SpaceGuard.createSpaceGuard(this, downloadItem.getSavePath()))
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
                .withVerifyConfig(md5, crc32, sha_1, sha_256);


        /**
         * 使用retry task ，可以增加重试机制，retry config使用，详见相关类的注释
         * 下面代码 task 如果不成功会 重试 5 次, -1 表示无限次数
         */
        RetryTask retryTask = new RetryTask(singleThreadTask, RetryConfig.create().retry(5));

        /**
         * 当前下载任务 立即执行
         */
        DownloadManager.getInstance().submit(retryTask);
        /**
         * 按任务提交顺序，先后执行
         */
//        DownloadManager.getInstance().submitNow(singleThreadTask);

    }

    /**
     * 下载任务准备开始
     * @param item
     */
    @Override
    public void onStart(DownloadItem item) {

    }

    /**
     * 下载完成
     * @param item
     */
    @Override
    public void onCompleted(DownloadItem item) {

    }

    /**
     * 进度更新， 0 <= percent <= 100
     * @param item
     * @param percent
     */
    @Override
    public void onUpdateProgress(DownloadItem item, int percent) {

    }

    /**
     * 暂停回调
     * @param item
     * @param percent
     */
    @Override
    public void onPause(DownloadItem item, int percent) {

    }

    /**
     * 错误回调
     * @param item
     * @param error
     */
    @Override
    public void onError(DownloadItem item, Error error) {

    }

    /**
     * 主动停止的回调
     * @param item
     * @param reason
     * @param message
     */
    @Override
    public void onStop(DownloadItem item, int reason, String message) {

    }
}
