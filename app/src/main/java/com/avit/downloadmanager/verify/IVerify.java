package com.avit.downloadmanager.verify;

public interface IVerify{
    boolean verify(VerifyConfig config);

    enum VerifyType{
        MD5, CRC32, SHA;
        static {
            /**
             * default sha-1, others sha-256, sha-384, sha-512
             */
            SHA.value = "SHA-1";
        }
        private String value;

        VerifyType() {
            value = name();
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
