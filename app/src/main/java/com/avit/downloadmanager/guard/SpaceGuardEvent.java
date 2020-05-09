package com.avit.downloadmanager.guard;

public class SpaceGuardEvent extends GuardEvent {
    public final static int EVENT_NONE = 0;
    public final static int EVENT_WARNING = -1;
    public final static int EVENT_ERROR = -2;
    public final static int EVENT_UNKNOWN = 1;
    public final static int EVENT_ENOUGH = 99;

    public SpaceGuardEvent(int reason, String message) {
        super(IGuard.Type.SPACE, reason, message);
    }

    public SpaceGuardEvent(int reason, String message, Object objExt) {
        super(IGuard.Type.SPACE, reason, message, objExt);
    }

    public SpaceGuardEvent(int reason, String message, Object objExt, long longExt) {
        super(IGuard.Type.SPACE, reason, message, objExt, longExt);
    }
}
