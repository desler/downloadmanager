package com.avit.downloadmanager.utils;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

public final class DigestText {

    private final static String TAG = "DigestText";

    public static String md5(String content){
        return digest("MD5", content);
    }

    public static String sha(String algorithm, String content){
        return digest(algorithm, content);
    }

    public static String digest(String algorithm, String content) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(content.getBytes("utf-8"));
            String sum = toHex(bytes);
            Log.d(TAG, "digest: " + sum);
            return sum;
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            Log.e(TAG, "digest: ", e);
        } finally {
            if (digest != null) {
                digest.reset();
            }
        }
        return "";
    }

    public static String crc32(String crc) {
        CRC32 crc32 = new CRC32();
        try {
            crc32.update(crc.getBytes("utf-8"));

            String sum = Long.toHexString(crc32.getValue()).toUpperCase();

            return sum;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "crc32: ", e);
        }

        return "";
    }

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    public static String toHex(byte[] bytes) {
        StringBuilder ret = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            ret.append(HEX_DIGITS[(bytes[i] >> 4) & 0x0f]);
            ret.append(HEX_DIGITS[bytes[i] & 0x0f]);
        }
        return ret.toString();
    }
}
