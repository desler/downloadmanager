package com.avit.downloadmanager.verify;

import android.text.TextUtils;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class AbsVerify<CONTENT> implements IVerify{

    static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    static String toHex(byte[] bytes) {
        StringBuilder ret = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            ret.append(HEX_DIGITS[(bytes[i] >> 4) & 0x0f]);
            ret.append(HEX_DIGITS[bytes[i] & 0x0f]);
        }
        return ret.toString();
    }


    protected String TAG = "AbsVerify";

    private CONTENT content;

    public AbsVerify(CONTENT content) {
        TAG = getClass().getSimpleName();
        this.content = content;
    }

    abstract boolean isValidCRC32(String crc32);
    abstract boolean isValidDigest(String type, String digest);

    public CONTENT getContent() {
        return content;
    }

    @Override
    public boolean verify(VerifyConfig config) {

        VerifyType type = config.getType();
        String verify = config.getVerify();

        if (TextUtils.isEmpty(verify)){
            Log.w(TAG, "verify: is empty, always valid");
            return true;
        }

        if (type == VerifyType.MD5 || type == VerifyType.SHA){
            return isValidDigest(type.getValue(), verify);
        }

        if (type == VerifyType.CRC32){
            return isValidCRC32(verify);
        }

        Log.e(TAG, "verify: DO NOT SUPPORT > " + config + ", default pass it");

        return true;
    }
}
