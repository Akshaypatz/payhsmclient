package com.billdesk.paymenthsm.client.internal.exception;

public class HSMCommandException extends HSMException {

    public HSMCommandException(String message) {
        super(message);
    }

    public HSMCommandException(String message, Throwable cause) {
        super(message, cause);
    }


}
