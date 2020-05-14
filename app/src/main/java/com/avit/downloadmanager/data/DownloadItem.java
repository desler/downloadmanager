package com.avit.downloadmanager.data;

import android.text.TextUtils;

import com.avit.downloadmanager.utils.DigestText;

public class DownloadItem {
    private String version;

    private String dlPath;
    private String savePath;

    private String filename;
    private Object object;

    private String key;

    public DownloadItem withDlPath(String dlPath) {
        this.dlPath = dlPath;
        return this;
    }

    public DownloadItem withSavePath(String savePath) {
        this.savePath = savePath;
        return this;
    }

    public DownloadItem withTag(Object object) {
        this.object = object;
        return this;
    }

    public DownloadItem withFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public String getFilename() {
        return filename;
    }

    public String getSavePath() {
        return savePath;
    }

    public String getDlPath() {
        return dlPath;
    }

    public String getKey() {
        if (TextUtils.isEmpty(key)) {
            key = DigestText.md5(version + "#" + dlPath);
        }
        return key;
    }

    public static DownloadItem create() {
        return new DownloadItem();
    }
}
