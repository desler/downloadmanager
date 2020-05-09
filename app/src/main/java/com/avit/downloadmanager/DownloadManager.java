package com.avit.downloadmanager;

import com.avit.downloadmanager.executor.ImmediatelyExecutor;
import com.avit.downloadmanager.executor.SequentialExecutor;

public final class DownloadManager {
    private ImmediatelyExecutor immediatelyExecutor;
    private SequentialExecutor sequentialExecutor;
}
