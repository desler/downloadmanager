package com.avit.downloadmanager.guard;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public final class SpaceGuard extends SystemGuard {

    private final static String TAG = "SpaceGuard";

    private static CacheGuard cacheGuard = new CacheGuard();

    public final static SpaceGuard ERROR = new SpaceGuard(null, null);

    public static SpaceGuard createSpaceGuard(Context context, String path) {
        String dir = path2MountDir(path);

        if (TextUtils.isEmpty(dir)) {
            Log.e(TAG, "createSpaceGuard: dir is null");
            return ERROR;
        }

        SpaceGuard spaceGuard = cacheGuard.getSpaceGuard(dir);
        if (spaceGuard == null) {
            spaceGuard = new SpaceGuard(context, dir);
            cacheGuard.putSpaceGuard(dir, spaceGuard);
        }
        return spaceGuard;
    }

    /**
     * 预留 5M 的空间，用作 安全告警。
     */
    private final static long WARNING_SIZE = 5 * 1024 * 1024;
    /**
     * 预留空间 小于 1M时，不再给下载分配空间
     */
    private final static long RED_SIZE = 1 * 1024 * 1024;

    private String guardDir;
    private long totalSize;

    private long freeSize;

    private SpaceGuard(Context context, String dir) {
        super(context);
        this.guardDir = dir;
    }

    @Override
    public void guard() {

    }

    public boolean occupySize(long size) {

        long tfsize = freeSize - totalSize;

        if (tfsize < RED_SIZE) {
            Log.e(TAG, "occupySize: FAILED, space not enough! > " + tfsize);
            notifyEvent(Type.SPACE, new GuardEvent(Type.SPACE, SpaceGuardEvent.EVENT_ERROR, "space not enough!", guardDir));
            return false;
        }

        totalSize += size;

        if (tfsize < WARNING_SIZE) {
            Log.w(TAG, "occupySize: WARNING, space is will exhaust! > " + tfsize);
            notifyEvent(Type.SPACE, new GuardEvent(Type.SPACE, SpaceGuardEvent.EVENT_WARNING, "space is will exhaust!", guardDir));
        }

        return true;
    }

    public long revertSize(long size) {
        totalSize -= size;

        long tfsize = freeSize - totalSize;

        if (tfsize > RED_SIZE) {
            Log.d(TAG, "revertSize: enough size");
            notifyEvent(Type.SPACE, new GuardEvent(Type.SPACE, SpaceGuardEvent.EVENT_ENOUGH, "enough size", guardDir, tfsize));
        }

        return tfsize;
    }

    @Override
    public void addGuardListener(IGuardListener guardListener) {
        guardHelper.addSpaceGuardListener(guardDir, guardListener);
    }

    @Override
    public void removeGuardListener(IGuardListener guardListener) {
        guardHelper.removeSpaceGuardListener(guardDir, guardListener);
    }

    static String path2MountDir(String path) {
        return path;
    }

    private static class CacheGuard {
        static Map<String, SpaceGuard> cache = new HashMap<>(3);

        private SpaceGuard getSpaceGuard(String dir) {
            return cache.get(dir);
        }

        private int putSpaceGuard(String dir, SpaceGuard guard) {
            cache.put(dir, guard);
            return cache.size();
        }
    }
}
