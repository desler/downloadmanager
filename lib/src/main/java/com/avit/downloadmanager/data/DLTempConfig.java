package com.avit.downloadmanager.data;

import androidx.annotation.NonNull;

import org.litepal.crud.LitePalSupport;

/**
 * use to support resume breakpoint
 */
public final class DLTempConfig extends LitePalSupport {
    /**
     * unique
     */
    public String key;
    /**
     * thread seq
     */
    public int seq;
    /**
     * offset
     */
    public long start;
    public long end;

    /**
     * current file already written bytes
     */
    public long written;

    /**
     * full path
     */
    public String filePath;

    @NonNull
    @Override
    public String toString() {
        return String.format("[key = %s, seq = %d]", key, seq);
    }
}
