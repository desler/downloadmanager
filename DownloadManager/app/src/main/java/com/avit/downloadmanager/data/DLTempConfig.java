package com.avit.downloadmanager.data;

/**
 * use to support resume breakpoint
 */
public final class DLTempConfig {
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
     * full path
     */
    public String filePath;
}
