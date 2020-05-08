package com.avit.downloadmanager.guard;

import android.util.Log;

public class GuardEvent {
    public final IGuard.Type type;

    public final int reason;
    public final String message;

    /**
     * SPACE : guard dir.
     */
    public final Object objExt;

    public final long longExt;

    public GuardEvent(IGuard.Type type, int reason, String message) {
        this(type, reason, message, null);
    }

    public GuardEvent(IGuard.Type type, int reason, String message, Object objExt) {
        this(type, reason, message, objExt, 0l);
    }

    public GuardEvent(IGuard.Type type, int reason, String message, Object objExt, long longExt) {
        this.type = type;
        this.reason = reason;
        this.message = message;
        this.objExt = objExt;
        this.longExt = longExt;
    }

    public static String dump(GuardEvent guardEvent) {
        String dump = String.format("[%s, %d, %s, %s]", guardEvent.type.name(), guardEvent.reason, guardEvent.message, guardEvent.objExt);
        Log.i("GuardEvent", dump);
        return dump;
    }
}
