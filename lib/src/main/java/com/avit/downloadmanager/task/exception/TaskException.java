package com.avit.downloadmanager.task.exception;

public final class TaskException extends IllegalStateException {
    public TaskException() {
    }

    public TaskException(String s) {
        super(s);
    }

    public TaskException(String message, Throwable cause) {
        super(message, cause);
    }

    public TaskException(Throwable cause) {
        super(cause);
    }
}
