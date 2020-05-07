package com.avit.downloadmanager.guard;

import java.util.concurrent.Callable;

public interface IGuard{
    void notifyEvent(Type type, GuardEvent event);

    void watchDog();

    IGuard guard();

    void registerGuardListener(String key , IGuardListener guardListener);

    enum Type {NETWORK, SPACE, SYSTEM, UNKNOWN}
}
