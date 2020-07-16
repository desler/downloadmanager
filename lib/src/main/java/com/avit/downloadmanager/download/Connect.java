package com.avit.downloadmanager.download;

import java.io.IOException;
import java.io.InputStream;

public class Connect implements DownloadConnection {

    public static class DefaultFactory implements DownloadConnection.Factory{

        @Override
        public DownloadConnection create(String dlPath) {
            return null;
        }
    }

    @Override
    public int getResponseCode() {
        return 0;
    }

    @Override
    public long getContentLength() {
        return 0;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return null;
    }

    @Override
    public void disconnect() {

    }
}
