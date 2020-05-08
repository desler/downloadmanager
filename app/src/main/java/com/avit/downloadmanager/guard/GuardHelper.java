package com.avit.downloadmanager.guard;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GuardHelper {

    public final static String TAG = "Guard";

    private final static GuardHelper sInstance = new GuardHelper();

    private List<IGuardListener> networkListeners;
    private Map<String, List<IGuardListener>> dirIGuardListenerMap;


    public static GuardHelper getInstance() {
        return sInstance;
    }

    private GuardHelper() {
        networkListeners = new ArrayList<>();
        dirIGuardListenerMap = new HashMap<>();
    }

    public void addNetworkGuardListener(IGuardListener guardListener) {
        networkListeners.add(guardListener);
    }

    public void removeNetworkGuardListener(IGuardListener guardListener){
        networkListeners.remove(guardListener);
    }

    public void addSpaceGuardListener(String dir, IGuardListener guardListener) {

        Log.d(TAG, "addSpaceGuardListener: dir = " + dir + ", guardListener = " + guardListener);

        List<IGuardListener> listeners = dirIGuardListenerMap.get(dir);
        if (listeners == null) {
            listeners = new ArrayList<>();
            dirIGuardListenerMap.put(dir, listeners);
        }

        if (!listeners.contains(guardListener)) {
            listeners.add(guardListener);
        }
    }

    public void removeSpaceGuardListener(String dir, IGuardListener guardListener) {

        Log.d(TAG, "removeSpaceGuardListener: dir = " + dir + ", guardListener = " + guardListener);

        List<IGuardListener> listeners = dirIGuardListenerMap.get(dir);
        if (listeners == null) {
            listeners = new ArrayList<>();
            dirIGuardListenerMap.put(dir, listeners);
        }

        if (listeners != null && !listeners.isEmpty()) {
            listeners.remove(guardListener);
        }
    }


    public void notifyEvent(IGuard.Type type, GuardEvent event) {

        GuardEvent.dump(event);

        if (type == IGuard.Type.NETWORK) {
            /**
             * 网络异常，给所有 任务都需要分发
             */
            for (IGuardListener l : networkListeners) {
                l.onGuardEvent(event);
            }

        } else if (type == IGuard.Type.SPACE) {

            /**
             * 空间不够等异常，只针对需要相应目录的空间 才需要
             */
            List<IGuardListener> spaceListeners = dirIGuardListenerMap.get(event.objExt);
            if (spaceListeners != null && !spaceListeners.isEmpty()){
                for (IGuardListener l : spaceListeners) {
                    l.onGuardEvent(event);
                }
            }
        } else {
            Log.w(TAG, "notifyEvent: DO NOT SUPPORT, type = " + type.name());
        }
    }
}
