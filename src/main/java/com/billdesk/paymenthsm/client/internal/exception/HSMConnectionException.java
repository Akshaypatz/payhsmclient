package com.billdesk.paymenthsm.client.internal.exception;

public class HSMConnectionException extends HSMException {
    public HSMConnectionException(String message) {
        super(message);
    }

    public HSMConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

