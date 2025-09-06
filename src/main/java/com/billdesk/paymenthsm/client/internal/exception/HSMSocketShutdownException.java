package com.billdesk.paymenthsm.client.internal.exception;

public class HSMSocketShutdownException extends HSMException {
    public HSMSocketShutdownException(String message) {
        super(message);
    }

    public HSMSocketShutdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
