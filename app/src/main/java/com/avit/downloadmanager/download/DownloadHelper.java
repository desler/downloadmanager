package com.avit.downloadmanager.download;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public final class DownloadHelper {

    private final static String TAG = "DownloadHelper";

    private final static int CONNECT_TIMEOUT = 3 * 1000;
    private final static int READ_TIMEOUT = 3 * 1000;

    private final static int BUFFER_SIZE = 128 * 1024;

    private String dlPath;
    private HttpURLConnection httpURLConnection;
    private String range;

    private InputStream inputStream;
    private OutputStream outputStream;

    private OnProgressListener onProgressListener;

    public DownloadHelper withPath(String dlPath) {
        this.dlPath = dlPath;
        return this;
    }

    public DownloadHelper withRange(long start, long end) {
        range = String.format(Locale.ENGLISH, "bytes=%ld-%ld", start, end);
        Log.d(TAG, "withRange: " + range);
        return this;
    }

    public DownloadHelper created() throws IOException {

        if (URLUtil.isHttpUrl(dlPath)) {
            URL url = new URL(this.dlPath);
            this.httpURLConnection = (HttpURLConnection) url.openConnection();
            this.httpURLConnection.setConnectTimeout(CONNECT_TIMEOUT);
            this.httpURLConnection.setReadTimeout(READ_TIMEOUT);
            if (!TextUtils.isEmpty(range)) {
                this.httpURLConnection.setRequestProperty("Range", range);
            }
        } else if (URLUtil.isHttpsUrl(dlPath)) {

        } else if (URLUtil.isAssetUrl(dlPath)) {

        } else if (URLUtil.isFileUrl(dlPath)) {

        } else if (URLUtil.isContentUrl(dlPath)) {

        } else {
            throw new IllegalArgumentException("do not support path : " + dlPath);
        }


        return this;
    }

    public DownloadHelper withProgressListener(OnProgressListener listener) {
        this.onProgressListener = listener;
        return this;
    }

    public int getResponseCode() throws IOException {
        return this.httpURLConnection.getResponseCode();
    }

    public long getContentLength() {
        return this.httpURLConnection.getContentLength();
    }

    public File retrieveFile(String fileFullPath) throws IOException {
        this.inputStream = httpURLConnection.getInputStream();

        File tmp = new File(fileFullPath + ".tmp");
        this.outputStream = new FileOutputStream(tmp);

        byte[] buffer = new byte[BUFFER_SIZE];
        int offset = 0, totalBytes = 0;
        while ((offset = inputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, offset);
            totalBytes += offset;
            if (onProgressListener != null) {
                onProgressListener.onProgress(dlPath, fileFullPath, totalBytes);
            }
        }
        outputStream.flush();

        release();

        File file = new File(fileFullPath);
        if (file.exists()) {
            Log.w(TAG, "retrieveFile: " + file.delete());
        }

        boolean isRename = tmp.renameTo(file);
        if (!isRename) {
            throw new IOException(tmp.getPath() + " rename FAILED");
        }

        return tmp;
    }

    /**
     * base xxx.tmp file
     * @param filePath
     * @return
     */
    public long resumeBreakPoint(String filePath){
        File ftmp = new File(filePath + ".tmp");

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

    public void release() {

        try {
            if (inputStream != null) {
                this.inputStream.close();
                this.inputStream = null;
            }
        } catch (IOException e) {
        }

        try {
            if (outputStream != null) {
                this.outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
        }

        if (this.httpURLConnection != null) {
            this.httpURLConnection.disconnect();
            this.httpURLConnection = null;
        }
    }

    public interface OnProgressListener {
        void onProgress(String dlPath, String filePath, int length);
    }
}
