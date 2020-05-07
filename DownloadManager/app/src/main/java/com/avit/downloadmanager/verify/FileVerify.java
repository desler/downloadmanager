package com.avit.downloadmanager.verify;

import java.io.File;

public final class FileVerify extends AbsVerify<File> {

    FileVerify(File file) {
        super(file);
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
