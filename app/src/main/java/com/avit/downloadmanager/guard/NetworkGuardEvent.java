package com.avit.downloadmanager.guard;

public class NetworkGuardEvent extends GuardEvent {

    public NetworkGuardEvent(int reason, String message) {
        super(IGuard.Type.NETWORK, reason, message);
    }

    public NetworkGuardEvent(int reason, String message, Object objExt) {
        super(IGuard.Type.NETWORK, reason, message, objExt);
    }

    public NetworkGuardEvent(int reason, String message, Object objExt, long longExt) {
        super(IGuard.Type.NETWORK, reason, message, objExt, longExt);
    }
}
