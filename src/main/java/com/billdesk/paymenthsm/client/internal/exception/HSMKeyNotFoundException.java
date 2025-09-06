package com.billdesk.paymenthsm.client.internal.exception;

public class HSMKeyNotFoundException extends HSMException {
    public HSMKeyNotFoundException(String message) {
        super(message);
    }

    public HSMKeyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
