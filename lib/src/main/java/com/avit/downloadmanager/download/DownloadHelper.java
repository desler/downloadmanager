package com.avit.downloadmanager.download;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
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
    private RandomAccessFile accessFile;

    private OnProgressListener onProgressListener;
    private boolean isNeedRename;

    private long start, end;

    private String tmpSuffix;

    public DownloadHelper() {
        isNeedRename = true;
        tmpSuffix = ".tmp";
    }

    public DownloadHelper withPath(String dlPath) {
        this.dlPath = dlPath;
        return this;
    }

    public DownloadHelper withRange(long start, long end) {

        this.start = start;
        this.end = end;

        if (end > 0) {
            range = String.format(Locale.ENGLISH, "bytes=%d-%d", start, end);
        } else {
            range = String.format(Locale.ENGLISH, "bytes=%d-", start);
        }
        Log.d(TAG, "withRange: " + range);
        return this;
    }

    public DownloadHelper withTmpSuffix(String tmpSuffix) {
        this.tmpSuffix = tmpSuffix;
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

        File tmp = new File(fileFullPath + tmpSuffix);
        this.outputStream = new FileOutputStream(tmp, true);

        byte[] buffer = new byte[BUFFER_SIZE];
        int offset = 0;
        long totalBytes = 0;
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

        if (isNeedRename) {
            boolean isRename = tmp.renameTo(file);
            if (!isRename) {
                throw new IOException(tmp.getName() + " rename FAILED");
            } else {
                Log.d(TAG, "retrieveFile: rename to " + file.getName());
            }
        }

        return tmp;
    }

    public File retrieveFileByRandom(String fileFullPath) throws IOException{

        this.inputStream = httpURLConnection.getInputStream();

        File tmp = new File(fileFullPath + tmpSuffix);
        accessFile = new RandomAccessFile(tmp, "rw");
        accessFile.seek(start);

        byte[] buffer = new byte[BUFFER_SIZE];
        int offset = 0;
        long totalBytes = 0;
        while ((offset = inputStream.read(buffer, 0, buffer.length)) != -1) {
            accessFile.write(buffer, 0, offset);
            totalBytes += offset;
            if (onProgressListener != null) {
                onProgressListener.onProgress(dlPath, fileFullPath, totalBytes);
            }
        }

        release();

        File file = new File(fileFullPath);
        if (file.exists()) {
            Log.w(TAG, "retrieveFileByRandom: " + file.delete());
        }

        if (isNeedRename) {
            boolean isRename = tmp.renameTo(new File(fileFullPath));
            if (!isRename) {
                throw new IOException(tmp.getName() + " rename FAILED");
            } else {
                Log.d(TAG, "retrieveFileByRandom: rename to " + file.getName());
            }
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

        if (ftmp.exists() && ftmp.isFile()) {

            long existLength = ftmp.length();

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
        } catch (Throwable e) {
            Log.w(TAG, "release: ", e);
        }

        try {
            if (outputStream != null) {
                this.outputStream.close();
                outputStream = null;
            }
        } catch (Throwable e) {
            Log.w(TAG, "release: ", e);
        }

        try {
            if (accessFile != null){
                accessFile.close();
                accessFile = null;
            }
        } catch (Throwable e) {
            Log.w(TAG, "release: ", e);
        }

        if (this.httpURLConnection != null) {
            this.httpURLConnection.disconnect();
            this.httpURLConnection = null;
        }
    }

    public DownloadHelper noRename() {
        isNeedRename = false;
        return this;
    }

    public final DownloadHelper clone(){

        DownloadHelper downloadHelper = new DownloadHelper();
        downloadHelper.dlPath = dlPath;
        downloadHelper.start = start;
        downloadHelper.end = end;
        downloadHelper.range = range;

        downloadHelper.onProgressListener = onProgressListener;

        downloadHelper.tmpSuffix = tmpSuffix;

        downloadHelper.isNeedRename = isNeedRename;

        return downloadHelper;
    }

    public interface OnProgressListener {
        void onProgress(String dlPath, String filePath, long length);
    }
}
