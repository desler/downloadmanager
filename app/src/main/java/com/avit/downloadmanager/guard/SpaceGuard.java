package com.avit.downloadmanager.guard;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class SpaceGuard extends SystemGuard {

    private final static String TAG = "SpaceGuard";

    public final static SpaceGuard ERROR = new SpaceGuard(null, null);

    private final static CacheGuard cacheGuard = new CacheGuard();

    public static SpaceGuard createSpaceGuard(Context context, String path) {

        if (TextUtils.isEmpty(path)) {
            Log.e(TAG, "createSpaceGuard: path is null");
            return ERROR;
        }

        String dir = MountDir.path2MountDir(path);

        if (TextUtils.isEmpty(dir)) {
            Log.e(TAG, "createSpaceGuard: dir is null");
            return ERROR;
        }

        SpaceGuard spaceGuard = cacheGuard.getSpaceGuard(dir);
        if (spaceGuard == null) {
            spaceGuard = new SpaceGuard(context, dir);
            Log.d(TAG, "createSpaceGuard: dir = " + dir);
            cacheGuard.putSpaceGuard(dir, spaceGuard);
        }
        spaceGuard.guard();

        return spaceGuard;
    }

    @SuppressWarnings("ONLY ONCE CALL")
    public synchronized static int initFromSystem(Context context, ParseShellDFFactory shellDFFactory) {
        MountDir mountDir = new MountDir(context).withShellDFFactory(shellDFFactory);
        long begin = System.currentTimeMillis();
        int size = mountDir.initBySystem();
        Log.d(TAG, "initFromSystem: cost = " + (System.currentTimeMillis() - begin) + "ms");
        return size;
    }

    private final ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {

            Thread thread = new Thread(r);
            thread.setName("SpaceGuardScheduled#" + thread.getId());
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                    Log.e(TAG, "uncaughtException: Thread -> " + t.getName());
                    Log.e(TAG, "uncaughtException: ", e);
                }
            });
            return thread;
        }
    });


    /**
     * 预留 5M 的空间，用作 安全告警。
     */
    private final static long WARNING_SIZE = 5 * 1024 * 1024;
    /**
     * 预留空间 小于 1M时，不再给下载分配空间
     */
    private final static long RED_SIZE = 1 * 1024 * 1024;
    /**
     * 15 s 检测一次，使用空间是否正常
     */
    private final static int CHECK_INTERVAL = 15;/* s */

    String guardDir;
    AtomicLong totalSize;
    AtomicLong freeSize;

    int preEvent;

    private GuardTask guardTask;
    private ScheduledFuture futureTask;

    private SpaceGuard(Context context, String dir) {
        super(context);
        this.guardDir = dir;
        this.totalSize = new AtomicLong(0);
        this.freeSize = new AtomicLong(0);
    }

    @Override
    public synchronized void guard() {
        if (guardTask == null) {
            guardTask = new GuardTask(this);
            futureTask = scheduled.scheduleWithFixedDelay(guardTask, 0, CHECK_INTERVAL, TimeUnit.SECONDS);
        } else {
            Log.w(TAG, "guard: already > " + guardDir);
        }
    }

    public boolean occupySize(long size) {

        long tfsize = freeSize.addAndGet(-totalSize.addAndGet(-size));

        if (!checkMaybeFreeSpace(tfsize))
            return false;

        totalSize.addAndGet(size);

        return true;
    }

    public long revertSize(long size) {

        long tfsize = freeSize.addAndGet(-totalSize.addAndGet(-size));

        if (tfsize > RED_SIZE) {
            Log.d(TAG, "revertSize: enough size");
            notifyEvent(Type.SPACE, new SpaceGuardEvent(SpaceGuardEvent.EVENT_ENOUGH, "enough size", guardDir, tfsize));
        }

        return tfsize;
    }

    public boolean checkMaybeFreeSpace(long tfsize) {
        if (tfsize < RED_SIZE) {
            Log.e(TAG, "checkMaybeFreeSpace: FAILED, space not enough! > " + tfsize);
            notifyEvent(Type.SPACE, new SpaceGuardEvent(SpaceGuardEvent.EVENT_ERROR, "space not enough!", guardDir));
            return false;
        }

        if (tfsize < WARNING_SIZE) {
            Log.w(TAG, "checkMaybeFreeSpace: WARNING, space is will exhaust! > " + tfsize);
            notifyEvent(Type.SPACE, new SpaceGuardEvent(SpaceGuardEvent.EVENT_WARNING, "space is will exhaust!", guardDir));
        }
        return true;
    }

    @Override
    public void addGuardListener(IGuardListener guardListener) {
        guardHelper.addSpaceGuardListener(guardDir, guardListener);
    }

    @Override
    public void removeGuardListener(IGuardListener guardListener) {
        guardHelper.removeSpaceGuardListener(guardDir, guardListener);
    }

    @Override
    public void notifyEvent(Type type, GuardEvent event) {
        super.notifyEvent(type, event);
        preEvent = event.reason;
    }

    @Override
    public synchronized IGuard disable() {

        guardTask.disable();

        futureTask.cancel(true);
        futureTask = null;

        return super.disable();
    }

    @Override
    public synchronized IGuard enable() {

        guardTask.enable();

        if (futureTask == null) {
            futureTask = scheduled.scheduleWithFixedDelay(guardTask, 0, CHECK_INTERVAL, TimeUnit.SECONDS);
        }

        return super.enable();
    }

    private static class CacheGuard {
        static Map<String, SpaceGuard> cache = new ConcurrentHashMap<>(3);

        private SpaceGuard getSpaceGuard(String dir) {
            return cache.get(dir);
        }

        private int putSpaceGuard(String dir, SpaceGuard guard) {
            cache.put(dir, guard);
            return cache.size();
        }
    }

    /**
     * 不同系统 平台下 df 命令返回的格式有可能不同，及挂载目录 权限也有可能不同，
     * 因此，此处提供次接口 作为第三方定制解析使用
     */
    public interface ParseShellDFFactory {
        /**
         * @param lines df 命令返回的实际内容，包括 表头
         * @return 返回可用的 路径
         */
        String[] onParse(List<String> lines);
    }
}

final class GuardTask implements Runnable {

    private final static String TAG = "GuardTask";

    private final SpaceGuard spaceGuard;
    private volatile boolean isDisable;

    public GuardTask(SpaceGuard spaceGuard) {
        this.spaceGuard = spaceGuard;
    }

    public void run() {

        String guardDir = spaceGuard.guardDir;
        if (isDisable) {
            Log.w(TAG, guardDir + " is disable");
            return;
        }

        AtomicLong freeSize = spaceGuard.freeSize;
        AtomicLong totalSize = spaceGuard.totalSize;

        File file = new File(guardDir);
        freeSize.set(file.getFreeSpace());
        long fsz = freeSize.get();
        Log.d(TAG, guardDir + " free size = " + MountDir.size2String(fsz));

        long tfsize = freeSize.addAndGet(-totalSize.get());
        if (spaceGuard.checkMaybeFreeSpace(tfsize) &&
                (spaceGuard.preEvent == SpaceGuardEvent.EVENT_ERROR || spaceGuard.preEvent == SpaceGuardEvent.EVENT_ERROR)) {
            Log.d(TAG, guardDir + " enough size = " + MountDir.size2String(tfsize));
            spaceGuard.notifyEvent(IGuard.Type.SPACE, new SpaceGuardEvent(SpaceGuardEvent.EVENT_ENOUGH, "enough size", guardDir, tfsize));
        }
    }

    public void enable() {
        isDisable = false;
    }

    public void disable() {
        isDisable = true;
    }
}

final class MountDir {

    private final static String TAG = "MountDir";

    private final static List<String> dirCache = new ArrayList<>();

    private Context context;
    private List<String> cmdLines;

    /**
     * Filesystem               Size     Used     Free   Blksize
     * /dev                   450.0M    36.0K   450.0M   4096
     * /sys/fs/cgroup         450.0M    12.0K   450.0M   4096
     * /mnt/asec              450.0M     0.0K   450.0M   4096
     * /mnt/obb               450.0M     0.0K   450.0M   4096
     * /mnt/usb               450.0M     0.0K   450.0M   4096
     * /mnt/iso               450.0M     0.0K   450.0M   4096
     * /mnt/samba             450.0M     0.0K   450.0M   4096
     * /var                   450.0M   532.0K   449.5M   4096
     * /system                774.9M   621.4M   153.5M   4096
     * /cache                 991.9M   520.0K   991.4M   4096
     * /data                  991.9M   323.3M   668.6M   4096
     * /tvservice              59.0M    17.4M    41.5M   4096
     * /tvconfig               29.0M    14.2M    14.8M   1024
     * /tvdatabase              3.9M   300.0K     3.6M   4096
     * /tvcustomer             11.7M    40.0K    11.7M   4096
     * /tvcertificate           3.9M    60.0K     3.8M   4096
     * /mnt/shell/emulated    991.9M   323.3M   668.6M   4096
     */
    private static final SpaceGuard.ParseShellDFFactory defaultFactory = new SpaceGuard.ParseShellDFFactory() {
        @Override
        public String[] onParse(List<String> lines) {
            if (lines.isEmpty()) {
                return new String[0];
            }

            List<String> dirs = new ArrayList<>();
            /**
             * remove head, useless.
             */
            String remove = lines.remove(0);

            for (String line : lines) {
                if (line.startsWith("/system")
                        || line.startsWith("/tv")
                        || line.startsWith("/var")
                        || line.startsWith("/cache")
                        || line.startsWith("/sys")
                        || line.startsWith("/dev")
                ) {
                    continue;
                }

                String[] splits = line.split(" ");
                Log.d(TAG, "onParse: " + splits[0]);
                dirs.add(splits[0]);
            }

            return dirs.toArray(new String[0]);
        }
    };

    private SpaceGuard.ParseShellDFFactory shellDFFactory = defaultFactory;

    public MountDir(Context context) {
        this.context = context;
        this.cmdLines = new ArrayList<>();
    }

    public MountDir withShellDFFactory(SpaceGuard.ParseShellDFFactory shellDFFactory) {
        if (shellDFFactory != null) {
            this.shellDFFactory = shellDFFactory;
        } else {
            Log.w(TAG, "withShellDFFactory: shellDFFactory is null, will use default");
        }
        return this;
    }

    public int initBySystem() {

        BufferedReader bufferedReader = null;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("df");
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Log.d(TAG, "initBySystem: " + line);
                cmdLines.add(line);
            }

            process.waitFor();
        } catch (Throwable e) {
            Log.e(TAG, "initBySystem: ", e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                }
            }

            if (process != null) {
                process.destroy();
            }
        }

        String[] dirs = parseUserDirs(cmdLines);

        for (String dir : dirs) {
            SpaceGuard.createSpaceGuard(context, dir + "/");
        }

        return dirs.length;
    }

    /**
     * @param lines
     * @return
     */
    private String[] parseUserDirs(List<String> lines) {
        dirCache.clear();
        String[] dirs = shellDFFactory.onParse(lines);
        dirCache.addAll(Arrays.asList(dirs));
        return dirs;
    }

    /**
     * 区分挂载目录时，应该使用  最长匹配原则，已适应下面的情况：
     * /storage                 1.4G     0.0K     1.4G   4096
     * /storage/emulated       10.4G     4.1G     6.2G   4096
     * /storage/self            1.4G     0.0K     1.4G   4096
     *
     * @param path
     * @return
     */
    static String path2MountDir(String path) {

        String maxPath = null;
        for (String dir : dirCache) {
            if (!path.startsWith(dir + "/")) {
                continue;
            }

            if (TextUtils.isEmpty(maxPath)) {
                maxPath = path;
            }

            if (path.length() > maxPath.length()) {
                maxPath = path;
            }
        }

        if (!TextUtils.isEmpty(maxPath)) {
            return maxPath;
        }

        Log.w(TAG, "path2MountDir: failed, use default /data");

        return "/data";
    }


    static final long B = 1;
    static final long K = 1024 * B;
    static final long M = 1024 * K;
    static final long G = 1024 * M;
    static final long P = 1024 * G;

    static String size2String(long size) {
        if (size > G) {
            return String.format("%.2fGB", size * 1.0f / G);
        }
        if (size > M) {
            return String.format("%.2fMB", size * 1.0f / M);
        }
        if (size > K) {
            return String.format("%.2fKB", size * 1.0f / K);
        }

        return String.format("%dB", size);
    }
}
