package com.billdesk.paymenthsm.client.internal.exception;

public class HSMProtocolException extends HSMException {
    public HSMProtocolException(String message) {
        super(message);
    }

    public HSMProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
