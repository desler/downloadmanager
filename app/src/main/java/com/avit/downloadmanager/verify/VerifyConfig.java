package com.avit.downloadmanager.verify;

import androidx.annotation.NonNull;

public final class VerifyConfig {

    private IVerify.VerifyType type;

    private String verify;

    private VerifyConfig(){
    }

    public VerifyConfig withType(IVerify.VerifyType type){
        this.type = type;
        return this;
    }

    public VerifyConfig withVerify(String verify){
        this.verify = verify;
        return this;
    }

    public static VerifyConfig create(IVerify.VerifyType type, String verify){
        return new VerifyConfig().withType(type).withVerify(verify);
    }

    public IVerify.VerifyType getType() {
        return type;
    }

    public String getVerify() {
        return verify;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("[%s, %s]", type.getSubType(), verify);
    }
}
