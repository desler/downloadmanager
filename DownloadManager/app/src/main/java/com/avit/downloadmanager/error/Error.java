package com.avit.downloadmanager.error;

/**
 * system error,
 * file error,
 * data error,
 * network error,
 * unknown error
 */
public final class Error {
    int what;
    int extra;
    String message;
    Object err;
}
