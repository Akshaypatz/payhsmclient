package com.billdesk.paymenthsm.client.internal.exception;

public class HSMRequestTimeoutException extends HSMException {
    public HSMRequestTimeoutException(String message) {
        super(message);
    }

    public HSMRequestTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
