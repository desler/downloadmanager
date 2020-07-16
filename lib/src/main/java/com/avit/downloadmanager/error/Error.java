package com.avit.downloadmanager.error;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Locale;

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
    public final Object error;

    public Error(int what, String message) {
        this(what, message, null);
    }

    public Error(int what, String message, Object error) {
        this(what, message, 0, error);
    }

    public Error(int what, String message, int extra, Object err) {
        this.what = what;
        this.extra = extra;
        this.message = message;
        this.error = err;
    }

    public Type type() {

        if (what >= 0) {
            return Type.ERROR_NONE;
        }

        if (what > Type.ERROR_SYSTEM.value) {
            return Type.ERROR_UNKNOWN;
        }

        if (what > Type.ERROR_FILE.value) {
            return Type.ERROR_SYSTEM;
        }

        if (what > Type.ERROR_DATA.value) {
            return Type.ERROR_FILE;
        }

        if (what > Type.ERROR_NETWORK.value) {
            return Type.ERROR_DATA;
        }

        if (what > Type.ERROR_UNDEFINED.value()) {
            return Type.ERROR_NETWORK;
        }

        return Type.ERROR_UNDEFINED;
    }

    public String dump() {
        return dump(this);
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + "[" + type() + "]";
    }

    static final String FORMAT = "[type = %s, extra = %d,\r\n message = %s , err = %s]";

    public static String dump(Error error) {
        String dump = String.format(Locale.ENGLISH, FORMAT, error.type(), error.extra, error.message, error);
        Log.e("ErrorDump", dump);
        if (error.error != null && error.error instanceof Throwable) {
            Log.e("ErrorDump", "dump: ", (Throwable) error.error);
        }
        return dump;
    }

    public enum Type {
        ERROR_NONE(0),
        ERROR_UNKNOWN(-1),
        ERROR_SYSTEM(-1000),
        ERROR_FILE(-2000),
        ERROR_DATA(-3000),
        ERROR_NETWORK(-4000),
        ERROR_UNDEFINED(-5000);
        private final int value;

        Type(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
