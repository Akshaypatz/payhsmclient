package com.billdesk.paymenthsm.client.internal.exception;

public class HSMNoHealthyNodeException extends HSMException {
    public HSMNoHealthyNodeException(String message) {
        super(message);
    }

    public HSMNoHealthyNodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
