package com.avit.downloadmanager.task;

public class PauseExecute extends IllegalStateException {
    public PauseExecute() {
    }

    public PauseExecute(String s) {
        super(s);
    }

    public PauseExecute(String message, Throwable cause) {
        super(message, cause);
    }

    public PauseExecute(Throwable cause) {
        super(cause);
    }
}
