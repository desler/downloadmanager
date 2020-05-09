package com.avit.downloadmanager.verify;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

public final class TextVerify extends AbsVerify<String> {

    TextVerify(String str) {
        super(str);
    }

    private boolean isValidDigest(MessageDigest digest, String verify) {
        try {
            byte[] bytes = digest.digest(getContent().getBytes("utf-8"));
            String sum = toHex(bytes);
            Log.d(TAG, "isValidDigest: " + sum);
            return verify.equalsIgnoreCase(sum);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "isValidDigest: ", e);
        } finally {
            digest.reset();
        }
        return false;
    }

    @Override
    boolean isValidCRC32(String crc) {
        CRC32 crc32 = new CRC32();
        try {
            crc32.update(crc.getBytes("utf-8"));

            String sum = String.valueOf(crc32.getValue());
            Log.d(TAG, "isValidCRC32: " + sum);

            return crc.equalsIgnoreCase(sum);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "isValidCRC32: ", e);
        }

        return false;
    }

    @Override
    boolean isValidDigest(String type, String digest) {
        try {
            return isValidDigest(MessageDigest.getInstance(type), digest);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "isValidDigest: ", e);
        }
        Log.e(TAG, "isValidDigest: NoSuchAlgorithmException, pass it > [ " + type + ", " + digest + "]");

        return true;
    }

}
