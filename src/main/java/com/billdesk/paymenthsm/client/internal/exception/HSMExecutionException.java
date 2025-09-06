package com.billdesk.paymenthsm.client.internal.exception;

public class HSMExecutionException extends HSMException {
    public HSMExecutionException(String message) {
        super(message);
    }

    public HSMExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
