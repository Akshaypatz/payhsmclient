package com.billdesk.paymenthsm.client.internal.exception;

public class HSMIOException extends HSMException {
    public HSMIOException(String message) {
        super(message);
    }

    public HSMIOException(String message, Throwable cause) {
        super(message, cause);
    }
}

