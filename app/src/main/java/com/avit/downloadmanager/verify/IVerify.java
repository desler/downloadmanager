package com.avit.downloadmanager.verify;

public interface IVerify{
    boolean verify(VerifyConfig config);

    enum VerifyType{
        MD5, CRC32, SHA;
        static {
            /**
             * default sha-1, others sha-256, sha-384, sha-512
             */
            SHA.subType = "SHA-1";
        }
        private String subType;

        VerifyType() {
            subType = name();
        }

        public String getSubType() {
            return subType;
        }

        public VerifyType setSubType(String subType) {
            this.subType = subType;
            return this;
        }
    }
}
