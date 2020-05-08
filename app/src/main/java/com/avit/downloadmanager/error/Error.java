package com.avit.downloadmanager.error;

import java.io.Serializable;

/**
 * system error,
 * file error,
 * data error,
 * network error,
 * unknown error
 */
public final class Error implements Serializable {
    public final int what;
    public final int extra;
    public final String message;
    public final Object err;

    public Error(int what, String message) {
        this(what, message, 0, null);
    }

    public Error(int what, String message, int extra, Object err) {
        this.what = what;
        this.extra = extra;
        this.message = message;
        this.err = err;
    }
}
