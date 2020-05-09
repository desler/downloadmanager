package com.avit.downloadmanager.task;

import android.util.Log;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

public class SingleThreadTask extends AbstactTask implements DownloadHelper.OnProgressListener {

    private final static String pathFormat = "%s/%s";

    private DLTempConfig dlConfig;
    private DownloadHelper downloadHelper;
    private long fileLength;

    public SingleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
    }

    private DLTempConfig createDLTempConfig(long length) {

        DLTempConfig dlTempConfig = new DLTempConfig();
        dlTempConfig.key = downloadItem.getKey();

        dlTempConfig.start = 0;
        dlTempConfig.end = length;

        dlTempConfig.filePath = String.format(pathFormat, downloadItem.getSavePath(), downloadItem.getFilename());
        dlTempConfig.seq = 0;

        return dlTempConfig;
    }

    @Override
    protected boolean onStart() {

        try {
            downloadHelper = new DownloadHelper().withPath(downloadItem.getDlPath()).created();

            int responseCode = downloadHelper.getResponseCode();
            if (HttpURLConnection.HTTP_OK == responseCode || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                fileLength = downloadHelper.getContentLength();
            } else {
                Log.e(TAG, "onStart: responseCode = " + responseCode);
                return false;
            }
            Log.d(TAG, "onStart: fileLength = " + fileLength);

            if (dlConfig == null) {
                dlConfig = createDLTempConfig(fileLength);
            }

            taskListener.onStart(downloadItem);

            return true;

        } catch (IOException e) {
            Log.e(TAG, "onStart: ", e);
            taskListener.onError(downloadItem, null);
        }

        return false;
    }


    /**
     * written size
     *
     * @return
     */
    private long resumeBreakPoint() {

        File ftmp = new File(dlConfig.filePath + ".tmp");
        if (!supportBreakpoint) {
            Log.d(TAG, "resumeBreakPoint: always delete " + ftmp.delete());
            return 0;
        }

        if (ftmp.exists()) {

            if (!ftmp.isFile()) {
                Log.e(TAG, "resumeBreakPoint: dir delete " + ftmp.delete());
                return 0;
            }

            /**
             * 防止 最终的 末端 读写出现异常，导致 数据不正确，此处 回退 512 个字节
             */
            long existLength = ftmp.length() - 512;

            /**
             * 如果 大于 0 ，证明已经下载了部分数据， 支持 断点续写
             */
            existLength = existLength < 0 ? 0 : existLength;
            if (existLength == 0) {
                Log.w(TAG, "resumeBreakPoint: zero delete " + ftmp.delete());
            }

            return existLength;
        }

        return 0;
    }

    @Override
    protected boolean onDownload() {

        /**
         * 检测是否需要 断点续写
         */
        long writtenLength = resumeBreakPoint();

        Log.d(TAG, "onDownload: written length = " + writtenLength);

        if (writtenLength > 0) {
            downloadHelper.withRange(writtenLength, fileLength);
        }

        if (!spaceGuard.occupySize(fileLength - writtenLength)) {
            return false;
        }

        try {
            downloadHelper.withProgressListener(this).retrieveFile(dlConfig.filePath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "onDownload: ", e);
            taskListener.onError(downloadItem, null);
        }

        return false;
    }


    @Override
    public void start() {
        super.start();
    }

    @Override
    public void release() {
        super.release();
        downloadHelper.release();
    }

    @Override
    public void onProgress(String dlPath, String filePath, int length) {
        dlConfig.written = length;
        taskListener.onUpdateProgress(downloadItem, (int) (length * 1.0f / fileLength * 100));
    }
}
