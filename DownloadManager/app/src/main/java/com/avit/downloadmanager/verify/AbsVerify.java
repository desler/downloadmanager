package com.avit.downloadmanager.verify;

import android.text.TextUtils;
import android.util.Log;

public abstract class AbsVerify<CONTENT> implements IVerify{

    private String TAG = "AbsVerify";

    private CONTENT content;

    public AbsVerify(CONTENT content) {
        this.content = content;
        TAG = getClass().getSimpleName();
    }

    abstract boolean isValidMd5(String md5);
    abstract boolean isValidCRC32(String crc32);
    abstract boolean isValidSHA_1(String sha_1);

    @Override
    public boolean verify(VerifyConfig config) {

        VerifyType type = config.getType();
        String verify = config.getVerify();

        if (TextUtils.isEmpty(verify)){
            Log.w(TAG, "verify: is empty, always valid");
            return true;
        }

        if (type == VerifyType.MD5){
            return isValidMd5(verify);
        }

        if (type == VerifyType.CRC32){
            return isValidCRC32(verify);
        }

        if (type == VerifyType.SHA_1){
            return isValidSHA_1(verify);
        }

        Log.e(TAG, "verify: DO NOT SUPPORT > " + config);

        return false;
    }
}
