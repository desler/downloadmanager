package com.avit.downloadmanager.verify;

public interface IVerify{
    boolean verify(VerifyConfig config);

    class VerifyType{
        public final static VerifyType MD5 = new VerifyType("MD5") ;
        public final static VerifyType CRC32 = new VerifyType("CRC32");
        public final static VerifyType SHA = new VerifyType("SHA-1");

        private String subType;

        private VerifyType(String subType) {
            this.subType = subType;
        }

        public String getSubType() {
            return subType;
        }

        /**
         * md5, crc32,
         * SHA-1
         * SHA-224
         * SHA-256
         * SHA-384
         * SHA-512
         * @param subType
         * @return
         */
        public VerifyType withSubType(String subType) {

            if(subType.equals("MD5"))
                return MD5;

            if (subType.equals("CRC32"))
                return CRC32;

            if (subType.equals("SHA-1"))
                return SHA;

            if (subType.startsWith("SHA")){
                return new VerifyType(subType);
            }

            throw new IllegalArgumentException("do not support this algorithm -> " + subType);
        }
    }
}
