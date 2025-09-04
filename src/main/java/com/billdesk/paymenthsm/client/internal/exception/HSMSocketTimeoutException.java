package com.billdesk.paymenthsm.client.internal.exception;

public class HSMSocketTimeoutException extends HSMException {
    public HSMSocketTimeoutException(String message) {
        super(message);
    }

    public HSMSocketTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

