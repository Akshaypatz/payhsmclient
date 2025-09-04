package com.billdesk.paymenthsm.client.internal.exception;

public class HSMUnhealthyNodeException extends HSMException {
    public HSMUnhealthyNodeException(String message) {
        super(message);
    }

    public HSMUnhealthyNodeException(String message, Throwable cause) {
        super(message, cause);
    }
}

