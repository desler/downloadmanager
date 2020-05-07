package com.avit.downloadmanager.guard;

import android.content.Context;

public abstract class SystemGuard implements IGuard {
    protected Context context;

    protected GuardHelper guardHelper;

    public SystemGuard(Context context) {
        this.context = context;
        this.guardHelper = GuardHelper.getInstance();
    }

    @Override
    public void notifyEvent(Type type, GuardEvent event) {
        guardHelper.notifyEvent(type, event);
    }

    @Override
    public void watchDog() {

    }

    @Override
    public IGuard guard() {
        return null;
    }
}
