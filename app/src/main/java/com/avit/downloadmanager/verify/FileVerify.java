package com.avit.downloadmanager.verify;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

public final class FileVerify extends AbsVerify<File> {

    FileVerify(File file) {
        super(file);
    }

    private boolean isValidDigest(MessageDigest digest, String verify) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(getContent());
            byte[] buffer = new byte[4 * 1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            fis.close();

            byte[] byteArray = digest.digest();
            String sum = toHex(byteArray);

            Log.d(TAG, "isValidDigest: " + sum);

            return verify.equalsIgnoreCase(sum);

        } catch (IOException e) {
            Log.e(TAG, "isValidDigest: ", e);
        } finally {
            digest.reset();
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
            }
        }

        return false;
    }

    @Override
    boolean isValidCRC32(String crc) {
        CRC32 crc32 = new CRC32();
        FileInputStream fileInputStream = null;
        try {

            fileInputStream = new FileInputStream(getContent());
            byte[] buffer = new byte[4 * 1024];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                crc32.update(buffer, 0, length);
            }

            String sum = Long.toHexString(crc32.getValue()).toUpperCase();
            Log.d(TAG, "isValidCRC32: " + sum);

            return crc.equalsIgnoreCase(sum);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "isValidCRC32: ", e);
        } catch (IOException e) {
            Log.e(TAG, "isValidCRC32: ", e);
        } finally {
            try {
                if (fileInputStream != null)
                    fileInputStream.close();
            } catch (IOException e) {
            }
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
