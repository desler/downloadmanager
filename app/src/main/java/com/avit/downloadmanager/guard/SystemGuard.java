package com.avit.downloadmanager.guard;

import android.content.Context;

public class SystemGuard implements IGuard {
    protected Context context;

    protected Guard guard;

    public SystemGuard(Context context) {
        this.context = context;
        this.guard = Guard.getInstance();
    }

    @Override
    public void notifyEvent(Type type, GuardEvent event) {
        guard.notifyEvent(type, event);
    }

    @Override
    public void watchDog() {

    }

    @Override
    public IGuard guard() {
        return null;
    }
}
