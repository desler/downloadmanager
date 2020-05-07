package com.avit.downloadmanager.verify;

public interface IVerify{
    boolean verify(VerifyConfig config);

    enum VerifyType{MD5, CRC32, SHA_1}
}
