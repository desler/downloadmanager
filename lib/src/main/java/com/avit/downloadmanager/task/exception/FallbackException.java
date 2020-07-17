package com.avit.downloadmanager.task.exception;

public class FallbackException extends IllegalArgumentException {
    public FallbackException() {
    }

    public FallbackException(String s) {
        super(s);
    }

    public FallbackException(String message, Throwable cause) {
        super(message, cause);
    }

    public FallbackException(Throwable cause) {
        super(cause);
    }
}
