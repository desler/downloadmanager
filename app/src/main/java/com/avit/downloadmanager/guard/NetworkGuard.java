package com.avit.downloadmanager.guard;

import android.content.Context;

/**
 * wireless，bluetooth，ethernet and etc.
 */
public final class NetworkGuard extends SystemGuard{

    private static NetworkGuard sInstance;

    public synchronized static NetworkGuard createNetworkGuard(Context context) {

        if (sInstance == null) {
            sInstance = new NetworkGuard(context);
        }

        return sInstance;
    }

    private NetworkGuard(Context context) {
        super(context);
    }

    @Override
    public void registerGuardListener(String key, IGuardListener guardListener) {
        guardHelper.registerNetworkGuardListener(guardListener);
    }
}
