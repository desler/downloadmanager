package com.avit.downloadmanager.guard;

public interface IGuard {
    void notifyEvent(Type type, GuardEvent event);

    void addGuardListener(IGuardListener guardListener);

    void removeGuardListener(IGuardListener guardListener);

    void guard();

    IGuard enable();

    IGuard disable();

    enum Type {NETWORK, SPACE, SYSTEM, UNKNOWN}
}
