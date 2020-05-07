package com.avit.downloadmanager.verify;

public final class TextVerify extends AbsVerify<CharSequence>{

    TextVerify(CharSequence str) {
        super(str);
    }

    @Override
    boolean isValidMd5(String md5) {
        return false;
    }

    @Override
    boolean isValidCRC32(String crc32) {
        return false;
    }

    @Override
    boolean isValidSHA_1(String sha_1) {
        return false;
    }

}
