package com.billdesk.paymenthsm.client.internal.util;

public class HSMConstants {
    public static final int HSM_SOCKET_CONNECT_TIMEOUT = 5000;
    public static final int HSM_SOCKET_READ_TIMEOUT = 45000;
    public static final Long PING_HSM_TIMEOUT = 400L;
    public static final String VISA = "VISA";
    public static final String CAVV_GENERATION_KEYNAME_SUFFIX = "_CAVV_GEN";
    public static final String MASTERCARD = "MASTERCARD";
    public static final long DEFAULT_HSM_TIMEOUT= 400L;
    public static final int TIMED_EXECUTOR_CORE_POOLSIZE = 12;
    public static final int HEALTH_CHECK_INITIAL_DELAY = 30;
    public static final int HEALTH_CHECK_FREQPERIOD = 30;
}
