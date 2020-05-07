package com.avit.downloadmanager.guard;

import android.util.Log;

public class GuardEvent {
    IGuard.Type type;

    int reason;
    String message;

    /**
     * SPACE : guard dir.
     */
    Object objExt;

    long longExt;

    public static String dump(GuardEvent guardEvent) {
        String dump = String.format("[%s, %d, %s, %s]", guardEvent.type.name(), guardEvent.reason, guardEvent.message, guardEvent.objExt);
        Log.i("GuardEvent", dump);
        return dump;
    }
}
