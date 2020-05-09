package com.avit.downloadmanager.guard;

public class NetworkGuardEvent extends GuardEvent {
    public NetworkGuardEvent(IGuard.Type type, int reason, String message) {
        super(type, reason, message);
    }

    public NetworkGuardEvent(IGuard.Type type, int reason, String message, Object objExt) {
        super(type, reason, message, objExt);
    }

    public NetworkGuardEvent(IGuard.Type type, int reason, String message, Object objExt, long longExt) {
        super(type, reason, message, objExt, longExt);
    }
}
