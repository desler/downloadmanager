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
            sInstance.guard();
        }

        return sInstance;
    }

    private NetworkGuard(Context context) {
        super(context);
    }

    @Override
    public int addGuardListener(IGuardListener guardListener) {
        return guardHelper.addNetworkGuardListener(guardListener);
    }

    @Override
    public int removeGuardListener(IGuardListener guardListener) {
        return guardHelper.removeNetworkGuardListener(guardListener);
    }

    @Override
    public void guard() {

    }
}
