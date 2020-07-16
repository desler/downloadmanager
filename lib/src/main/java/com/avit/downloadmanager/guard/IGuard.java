package com.avit.downloadmanager.guard;

public interface IGuard {
    void notifyEvent(Type type, GuardEvent event);

    int addGuardListener(IGuardListener guardListener);

    int removeGuardListener(IGuardListener guardListener);

    void guard();

    IGuard enable();

    IGuard disable();

    enum Type {NETWORK, SPACE, SYSTEM, UNKNOWN}
}
