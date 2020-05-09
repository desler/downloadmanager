package com.avit.downloadmanager.guard;

public class SpaceGuardEvent extends GuardEvent {
    public final static int EVENT_NONE = 0;
    public final static int EVENT_WARNING = -1;
    public final static int EVENT_ERROR = -2;
    public final static int EVENT_UNKNOWN = 1;
    public final static int EVENT_ENOUGH = 99;

    public SpaceGuardEvent(IGuard.Type type, int reason, String message) {
        super(type, reason, message);
    }

    public SpaceGuardEvent(IGuard.Type type, int reason, String message, Object objExt) {
        super(type, reason, message, objExt);
    }

    public SpaceGuardEvent(IGuard.Type type, int reason, String message, Object objExt, long longExt) {
        super(type, reason, message, objExt, longExt);
    }
}
