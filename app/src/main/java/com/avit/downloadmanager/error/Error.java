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

    enum Type {
        ERROR_SYSTEM(1000), ERROR_FILE(2000), ERROR_DATA(3000), ERROR_NETWORK(4000), ERROR_UNKNOWN(9000);
        private int value;

        Type(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
