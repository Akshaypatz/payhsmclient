package com.billdesk.paymenthsm.client.internal.exception;

public class TriedAllHSMNodesException extends HSMException {
    public TriedAllHSMNodesException(String message) {
        super(message);
    }

    public TriedAllHSMNodesException(String message, Throwable cause) {
        super(message, cause);
    }
}
