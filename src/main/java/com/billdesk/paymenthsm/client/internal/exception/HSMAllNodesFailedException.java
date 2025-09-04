package com.billdesk.paymenthsm.client.internal.exception;

public class HSMAllNodesFailedException extends HSMException {
    public HSMAllNodesFailedException(String message) {
        super(message);
    }

    public HSMAllNodesFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

