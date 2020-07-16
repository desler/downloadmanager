package com.avit.downloadmanager.verify;

public final class VerifyException extends IllegalArgumentException {
    public VerifyException() {
    }

    public VerifyException(String s) {
        super(s);
    }

    public VerifyException(String message, Throwable cause) {
        super(message, cause);
    }

    public VerifyException(Throwable cause) {
        super(cause);
    }
}
