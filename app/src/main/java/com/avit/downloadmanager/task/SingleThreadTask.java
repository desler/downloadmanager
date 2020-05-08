package com.avit.downloadmanager.task;

import com.avit.downloadmanager.data.DLTempConfig;
import com.avit.downloadmanager.data.DownloadItem;

import java.io.File;

public class SingleThreadTask extends AbstactTask {

    private DLTempConfig tempConfig;

    public SingleThreadTask(DownloadItem downloadItem) {
        super(downloadItem);
    }

    @Override
    protected boolean onDownload() {
        return false;
    }

    @Override
    protected boolean onStart() {

        if (tempConfig == null){
            tempConfig = new DLTempConfig();
            tempConfig.key = getDownloadItem().getKey();
            tempConfig.filePath = downloadItem.getSavePath() + File.pathSeparator + downloadItem.getFilename();
        }

        if (taskListener != null){
            taskListener.onStart(downloadItem);
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
    }
}
