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
    public void addGuardListener(IGuardListener guardListener) {
        guardHelper.addNetworkGuardListener(guardListener);
    }

    @Override
    public void removeGuardListener(IGuardListener guardListener) {
        guardHelper.removeNetworkGuardListener(guardListener);
    }

    @Override
    public void guard() {

    }
}
