package com.billdesk.paymenthsm.client.internal.exception;

public class HSMException extends RuntimeException {
    public HSMException(String message) {
        super(message);
    }

    public HSMException(String message, Throwable cause) {
        super(message, cause);
    }
}
