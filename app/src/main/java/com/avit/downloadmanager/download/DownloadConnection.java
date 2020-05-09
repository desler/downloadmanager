package com.avit.downloadmanager.download;

import java.io.IOException;
import java.io.InputStream;

public interface DownloadConnection {

    interface Factory {
        DownloadConnection create(String dlPath);
    }

    int getResponseCode();

    long getContentLength();

    InputStream getInputStream() throws IOException;

    void disconnect();
}
