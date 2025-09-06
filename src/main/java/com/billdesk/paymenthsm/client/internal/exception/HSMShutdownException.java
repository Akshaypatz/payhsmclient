package com.billdesk.paymenthsm.client.internal.exception;

public class HSMShutdownException extends HSMException {
    public HSMShutdownException(String message) {
        super(message);
    }

    public HSMShutdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
