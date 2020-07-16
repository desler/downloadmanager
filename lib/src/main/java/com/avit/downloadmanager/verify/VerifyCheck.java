package com.avit.downloadmanager.verify;

import java.io.File;

public final class VerifyCheck {
    public static FileVerify createVerify(File file){
        return new FileVerify(file);
    }

    public static TextVerify createVerify(String str){
        return new TextVerify(str);
    }
}
